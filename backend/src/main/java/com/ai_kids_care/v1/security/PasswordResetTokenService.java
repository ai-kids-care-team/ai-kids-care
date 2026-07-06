package com.ai_kids_care.v1.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Redis-backed store for the SMS password-reset flow (UX-07 wire-password-management): challenge
 * codes, verify-attempt counters, and single-use reset tokens. Zero schema — everything is TTL'd
 * Redis state, mirroring {@link LoginThrottleService}'s "no new dependency, fail-open on Redis
 * outage" posture (a Redis outage here only means the reset flow itself is unavailable — it never
 * blocks the caller's HTTP response with an exception, and it never turns into an
 * existence oracle).
 *
 * <p><b>Enumeration safety</b>: {@link #createChallenge} always returns an opaque random
 * {@code challengeId} and stores a real random code + hash regardless of whether {@code userId} is
 * non-null; callers only decide whether to actually send an SMS for it. A challenge stored with a
 * {@code null} {@code userId} (dummy — non-existent account or no phone on file) can never verify
 * successfully: {@link #verify} rejects a null-bound challenge even on a hash match, so the
 * observable failure shape is identical to "wrong code for a real account".
 *
 * <p>Verification code, reset token, and any bound identifier are never logged — only generic
 * warnings on Redis failures.
 */
@Service
public class PasswordResetTokenService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetTokenService.class);

    private static final String CHALLENGE_KEY_PREFIX = "pwreset:challenge:";
    private static final String TOKEN_KEY_PREFIX = "pwreset:token:";
    private static final String FIELD_CODE_HASH = "codeHash";
    private static final String FIELD_USER_ID = "userId";
    private static final String FIELD_ATTEMPTS = "attempts";
    private static final String NO_USER_SENTINEL = "";

    private final StringRedisTemplate redisTemplate;
    private final Duration challengeTtl;
    private final Duration tokenTtl;
    private final int maxVerifyAttempts;

    public PasswordResetTokenService(
            StringRedisTemplate redisTemplate,
            @Value("${password-reset.challenge-ttl-seconds:300}") long challengeTtlSeconds,
            @Value("${password-reset.token-ttl-seconds:600}") long tokenTtlSeconds,
            @Value("${password-reset.max-verify-attempts:5}") int maxVerifyAttempts
    ) {
        this.redisTemplate = redisTemplate;
        this.challengeTtl = Duration.ofSeconds(Math.max(1, challengeTtlSeconds));
        this.tokenTtl = Duration.ofSeconds(Math.max(1, tokenTtlSeconds));
        this.maxVerifyAttempts = Math.max(1, maxVerifyAttempts);
    }

    /** {@code code} is the plaintext 6-digit code — only ever handed to the SMS send call, never persisted or logged as-is. */
    public record Challenge(String challengeId, String code, OffsetDateTime expiresAt) {
    }

    public record VerifyResult(boolean success, String resetToken, OffsetDateTime expiresAt) {
        public static VerifyResult failure() {
            return new VerifyResult(false, null, null);
        }
    }

    /**
     * Creates and stores a challenge. {@code userId} is {@code null} for a dummy challenge
     * (account doesn't exist / has no phone) — the code is still a real random value (never sent
     * anywhere for a dummy challenge) so a dummy and a real challenge are indistinguishable at rest.
     */
    public Challenge createChallenge(Long userId) {
        String challengeId = UUID.randomUUID().toString();
        String code = String.format("%06d", ThreadLocalRandom.current().nextInt(1_000_000));
        String codeHash = sha256Hex(code);
        OffsetDateTime expiresAt = OffsetDateTime.now().plus(challengeTtl);
        try {
            String key = challengeKey(challengeId);
            redisTemplate.opsForHash().putAll(key, Map.of(
                    FIELD_CODE_HASH, codeHash,
                    FIELD_USER_ID, userId == null ? NO_USER_SENTINEL : userId.toString(),
                    FIELD_ATTEMPTS, "0"
            ));
            redisTemplate.expire(key, challengeTtl);
        } catch (RuntimeException ex) {
            log.warn("Password reset challenge store failed; challenge will not be verifiable.", ex);
        }
        return new Challenge(challengeId, code, expiresAt);
    }

    /**
     * Verifies {@code code} against the stored challenge. Every failure path (unknown/expired
     * challenge, wrong code, dummy challenge, attempts exceeded) returns the same
     * {@link VerifyResult#failure()} shape — callers must not branch on *why* it failed.
     */
    public VerifyResult verify(String challengeId, String code) {
        String key = challengeKey(challengeId);
        Map<Object, Object> entries;
        try {
            entries = redisTemplate.opsForHash().entries(key);
        } catch (RuntimeException ex) {
            log.warn("Password reset challenge lookup failed.", ex);
            return VerifyResult.failure();
        }
        if (entries.isEmpty()) {
            return VerifyResult.failure();
        }
        String storedHash = (String) entries.get(FIELD_CODE_HASH);
        String storedUserId = (String) entries.get(FIELD_USER_ID);
        int attempts = parseIntOrZero(entries.get(FIELD_ATTEMPTS));
        if (attempts >= maxVerifyAttempts) {
            deleteChallenge(key);
            return VerifyResult.failure();
        }
        boolean hashMatches = storedHash != null && constantTimeEquals(storedHash, sha256Hex(code));
        if (!hashMatches) {
            registerFailedAttempt(key, attempts);
            return VerifyResult.failure();
        }
        // Dummy challenge (no bound user): a hash match here would only ever be an astronomically
        // unlikely coincidence, never a genuine success — still a uniform failure, no branch
        // visible to the caller.
        if (storedUserId == null || storedUserId.isBlank()) {
            deleteChallenge(key);
            return VerifyResult.failure();
        }
        deleteChallenge(key);
        return issueResetToken(Long.parseLong(storedUserId));
    }

    /**
     * Consumes (deletes) a reset token exactly once; returns the bound userId, or {@code null} if
     * the token is unknown, expired, or already consumed.
     */
    public Long consumeResetToken(String resetToken) {
        String key = tokenKey(resetToken);
        try {
            String value = redisTemplate.opsForValue().get(key);
            if (value == null) {
                return null;
            }
            redisTemplate.delete(key);
            return Long.parseLong(value);
        } catch (RuntimeException ex) {
            log.warn("Password reset token consume failed.", ex);
            return null;
        }
    }

    private VerifyResult issueResetToken(Long userId) {
        String resetToken = generateResetToken();
        OffsetDateTime expiresAt = OffsetDateTime.now().plus(tokenTtl);
        try {
            redisTemplate.opsForValue().set(tokenKey(resetToken), userId.toString(), tokenTtl);
        } catch (RuntimeException ex) {
            log.warn("Password reset token store failed.", ex);
            return VerifyResult.failure();
        }
        return new VerifyResult(true, resetToken, expiresAt);
    }

    private void registerFailedAttempt(String key, int previousAttempts) {
        try {
            redisTemplate.opsForHash().increment(key, FIELD_ATTEMPTS, 1);
            if (previousAttempts + 1 >= maxVerifyAttempts) {
                deleteChallenge(key);
            }
        } catch (RuntimeException ex) {
            log.warn("Password reset attempt increment failed.", ex);
        }
    }

    private void deleteChallenge(String key) {
        try {
            redisTemplate.delete(key);
        } catch (RuntimeException ex) {
            log.warn("Password reset challenge delete failed.", ex);
        }
    }

    private static String generateResetToken() {
        byte[] bytes = new byte[32]; // 256-bit
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static int parseIntOrZero(Object value) {
        if (value == null) {
            return 0;
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(
                a.getBytes(StandardCharsets.UTF_8),
                b.getBytes(StandardCharsets.UTF_8));
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException ex) {
            // SHA-256 is present on every JVM — unreachable.
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }

    private String challengeKey(String challengeId) {
        return CHALLENGE_KEY_PREFIX + challengeId;
    }

    private String tokenKey(String resetToken) {
        return TOKEN_KEY_PREFIX + resetToken;
    }
}
