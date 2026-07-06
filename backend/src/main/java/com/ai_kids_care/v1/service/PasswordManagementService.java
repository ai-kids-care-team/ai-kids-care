package com.ai_kids_care.v1.service;

import com.ai_kids_care.v1.entity.User;
import com.ai_kids_care.v1.repository.UserRepository;
import com.ai_kids_care.v1.security.PasswordResetTokenService;
import com.ai_kids_care.v1.security.SessionRevocationService;
import com.ai_kids_care.v1.type.StatusEnum;
import com.ai_kids_care.v1.vo.PasswordResetVerifyVO;
import com.ai_kids_care.v1.vo.VerificationCodeCreateVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;

/**
 * Business logic for UX-07 (wire-password-management): self-service authenticated password
 * change, and the three-step enumeration-safe SMS password reset flow. Kept separate from
 * {@link AuthService} (registration/login/tenant-context) since this slice owns a distinct set of
 * collaborators (Redis challenge/token store, SMS send, session revocation).
 *
 * <p>Zero schema: all reset-flow state lives in Redis via {@link PasswordResetTokenService}; this
 * class also owns the {@code password-reset/request} rate limiter, which follows the same
 * "SHA-256 hashed key, fail-open on Redis outage" pattern as {@code LoginThrottleService} but
 * counts *requests* (not failures) since existing and non-existing accounts must be throttled
 * identically.
 */
@Service
public class PasswordManagementService {

    private static final Logger log = LoggerFactory.getLogger(PasswordManagementService.class);
    private static final String REQUEST_THROTTLE_FAIL_PREFIX = "pwreset:throttle:request:fail:";
    private static final String REQUEST_THROTTLE_LOCK_PREFIX = "pwreset:throttle:request:lock:";

    private final PasswordEncoder passwordEncoder;
    private final UserRepository userRepository;
    private final SessionRevocationService sessionRevocationService;
    private final PasswordResetTokenService passwordResetTokenService;
    private final SmsPort smsPort;
    private final StringRedisTemplate redisTemplate;
    private final int requestThrottleMaxAttempts;
    private final Duration requestThrottleWindow;
    private final Duration requestThrottleLock;

    public PasswordManagementService(
            PasswordEncoder passwordEncoder,
            UserRepository userRepository,
            SessionRevocationService sessionRevocationService,
            PasswordResetTokenService passwordResetTokenService,
            SmsPort smsPort,
            StringRedisTemplate redisTemplate,
            @Value("${password-reset.throttle.max-attempts:5}") int requestThrottleMaxAttempts,
            @Value("${password-reset.throttle.window-seconds:300}") long requestThrottleWindowSeconds,
            @Value("${password-reset.throttle.lock-seconds:900}") long requestThrottleLockSeconds
    ) {
        this.passwordEncoder = passwordEncoder;
        this.userRepository = userRepository;
        this.sessionRevocationService = sessionRevocationService;
        this.passwordResetTokenService = passwordResetTokenService;
        this.smsPort = smsPort;
        this.redisTemplate = redisTemplate;
        this.requestThrottleMaxAttempts = Math.max(1, requestThrottleMaxAttempts);
        this.requestThrottleWindow = Duration.ofSeconds(Math.max(1, requestThrottleWindowSeconds));
        this.requestThrottleLock = Duration.ofSeconds(Math.max(1, requestThrottleLockSeconds));
    }

    /**
     * Authenticated self-service change: current user identity comes from the caller (resolved
     * from {@code EffectiveAuthorizationContextHolder} at the controller boundary, never from the
     * request body). Revokes all server sessions for the user on success (all devices, including
     * the current one) — the caller must re-authenticate with the new password.
     */
    @Transactional
    public void changePassword(Long userId, String currentPassword, String newPassword) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication failed"));
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "현재 비밀번호가 올바르지 않습니다.");
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        sessionRevocationService.revokeAllForUser(userId);
    }

    /**
     * Step 1 — always returns a structurally identical {@code 200} body regardless of whether
     * {@code loginId} resolves to an account, and regardless of whether that account has a phone
     * on file. An SMS is sent only when both are true; otherwise a dummy challenge is stored so a
     * subsequent verify attempt fails the same way a wrong code would.
     */
    public VerificationCodeCreateVO requestReset(String loginId) {
        if (isRequestThrottled(loginId)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many requests");
        }
        recordRequestAttempt(loginId);

        User user = userRepository.findByLoginId(loginId);
        Long userId = (user != null && user.getStatus() == StatusEnum.ACTIVE) ? user.getId() : null;

        PasswordResetTokenService.Challenge challenge = passwordResetTokenService.createChallenge(userId);

        if (userId != null && StringUtils.hasText(user.getPhone())) {
            sendResetCodeBestEffort(user.getPhone(), challenge.code());
        }

        return VerificationCodeCreateVO.builder()
                .challengeId(challenge.challengeId())
                .expiresAt(challenge.expiresAt())
                .build();
    }

    /**
     * Step 2 — uniform {@code 400} for every failure (wrong code, unknown/expired challenge,
     * dummy challenge, attempts exceeded); success issues a single-use {@code resetToken}.
     */
    public PasswordResetVerifyVO verifyReset(String challengeId, String code) {
        PasswordResetTokenService.VerifyResult result = passwordResetTokenService.verify(challengeId, code);
        if (!result.success()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "인증에 실패했습니다.");
        }
        return PasswordResetVerifyVO.builder()
                .resetToken(result.resetToken())
                .expiresAt(result.expiresAt())
                .build();
    }

    /**
     * Step 3 — consumes (deletes) the {@code resetToken} exactly once, persists the new bcrypt
     * password, and revokes all server sessions for the user.
     */
    @Transactional
    public void confirmReset(String resetToken, String newPassword) {
        Long userId = passwordResetTokenService.consumeResetToken(resetToken);
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "유효하지 않거나 만료된 요청입니다.");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "유효하지 않거나 만료된 요청입니다."));
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        sessionRevocationService.revokeAllForUser(userId);
    }

    /**
     * SMS send happens outside any DB transaction (external IO) and is best-effort: a delivery
     * failure never changes the client-visible response (enumeration safety requires request/ to
     * always look the same) — it is only logged at warn, and never with the code or phone.
     */
    private void sendResetCodeBestEffort(String phone, String code) {
        try {
            smsPort.send(phone, "[AI Kids Care] 인증번호: " + code + " (5분)");
        } catch (RuntimeException ex) {
            log.warn("Password reset SMS send failed.", ex);
        }
    }

    private boolean isRequestThrottled(String loginId) {
        if (!StringUtils.hasText(loginId)) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(requestLockKey(loginId)));
        } catch (RuntimeException ex) {
            log.warn("Password reset throttle isLocked check failed; allowing attempt.", ex);
            return false;
        }
    }

    private void recordRequestAttempt(String loginId) {
        if (!StringUtils.hasText(loginId)) {
            return;
        }
        try {
            String failKey = requestFailKey(loginId);
            Long count = redisTemplate.opsForValue().increment(failKey);
            if (count != null && count == 1L) {
                redisTemplate.expire(failKey, requestThrottleWindow);
            }
            if (count != null && count >= requestThrottleMaxAttempts) {
                redisTemplate.opsForValue().set(requestLockKey(loginId), "1", requestThrottleLock);
            }
        } catch (RuntimeException ex) {
            log.warn("Password reset throttle recordAttempt failed; continuing without lock.", ex);
        }
    }

    private String requestFailKey(String loginId) {
        return REQUEST_THROTTLE_FAIL_PREFIX + hash(loginId);
    }

    private String requestLockKey(String loginId) {
        return REQUEST_THROTTLE_LOCK_PREFIX + hash(loginId);
    }

    /** raw loginId (S1-adjacent) never goes into a Redis key directly — SHA-256 hash only. */
    private static String hash(String identifier) {
        String normalized = identifier.trim().toLowerCase();
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(normalized.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }
}
