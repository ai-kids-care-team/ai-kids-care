package com.ai_kids_care.v1.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;

/**
 * 登录 暴力破解 防护(harden-auth-bootstrap-and-demo D2): Redis 失败计数 + TTL 临时锁定。
 *
 * <p>설계:
 * <ul>
 *   <li>identifier 별 실패 카운터를 유한 윈도우({@code window-seconds}) TTL 로 센다. 임계치
 *       ({@code max-attempts})에 도달하면 {@code lock-seconds} TTL 의 lock 키를 둔다.</li>
 *   <li>{@link #isLocked} 가 true 면 호출부(AuthService)가 비밀번호가 맞아도 429 를 던진다.</li>
 *   <li>로그인 성공 시 {@link #reset} 으로 카운터·lock 을 모두 지운다(이전 실패가 누적되지 않게).</li>
 *   <li>기존 session 용 Redis 인프라를 재사용한다(신규 의존성 0). 단일 인스턴스 가정(다중 인스턴스
 *       분산 일관성은 follow-up).</li>
 * </ul>
 *
 * <p>키에는 raw identifier(이메일/전화 등 S1 가능)를 넣지 않고 SHA-256 해시를 쓴다. 감사/로그에도
 * identifier·password 를 절대 남기지 않는다(SPEC §282).
 */
@Service
public class LoginThrottleService {

    private static final Logger log = LoggerFactory.getLogger(LoginThrottleService.class);

    private static final String FAIL_KEY_PREFIX = "login:throttle:fail:";
    private static final String LOCK_KEY_PREFIX = "login:throttle:lock:";

    private final StringRedisTemplate redisTemplate;
    private final int maxAttempts;
    private final Duration window;
    private final Duration lock;

    public LoginThrottleService(
            StringRedisTemplate redisTemplate,
            @Value("${login.throttle.max-attempts:5}") int maxAttempts,
            @Value("${login.throttle.window-seconds:300}") long windowSeconds,
            @Value("${login.throttle.lock-seconds:900}") long lockSeconds
    ) {
        this.redisTemplate = redisTemplate;
        this.maxAttempts = Math.max(1, maxAttempts);
        this.window = Duration.ofSeconds(Math.max(1, windowSeconds));
        this.lock = Duration.ofSeconds(Math.max(1, lockSeconds));
    }

    /** identifier 가 현재 임시 잠금 상태인지(lock 키 존재 여부). Redis 장애 시 false(가용성 우선). */
    public boolean isLocked(String identifier) {
        if (isBlank(identifier)) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(lockKey(identifier)));
        } catch (RuntimeException ex) {
            // Redis 장애가 로그인 자체를 막지 않도록 best-effort(비밀번호 검증은 그대로 수행됨).
            log.warn("Login throttle isLocked check failed; allowing attempt.", ex);
            return false;
        }
    }

    /**
     * 실패 1회 기록. 윈도우 내 누적 실패가 임계치에 도달하면 lock 키를 설정한다.
     *
     * @return 이번 실패로 새로 잠금이 발동되었으면 true(호출부가 LOGIN_THROTTLE 감사를 1회만 쓰게).
     */
    public boolean recordFailure(String identifier) {
        if (isBlank(identifier)) {
            return false;
        }
        try {
            String failKey = failKey(identifier);
            Long count = redisTemplate.opsForValue().increment(failKey);
            if (count != null && count == 1L) {
                // 윈도우 시작 — 첫 실패에만 TTL 을 건다(슬라이딩이 아닌 고정 윈도우).
                redisTemplate.expire(failKey, window);
            }
            if (count != null && count >= maxAttempts) {
                String lockKey = lockKey(identifier);
                boolean alreadyLocked = Boolean.TRUE.equals(redisTemplate.hasKey(lockKey));
                redisTemplate.opsForValue().set(lockKey, "1", lock);
                return !alreadyLocked;
            }
        } catch (RuntimeException ex) {
            log.warn("Login throttle recordFailure failed; continuing without lock.", ex);
        }
        return false;
    }

    /** 로그인 성공 시 실패 카운터·lock 을 모두 제거(누적 초기화). */
    public void reset(String identifier) {
        if (isBlank(identifier)) {
            return;
        }
        try {
            redisTemplate.delete(failKey(identifier));
            redisTemplate.delete(lockKey(identifier));
        } catch (RuntimeException ex) {
            log.warn("Login throttle reset failed.", ex);
        }
    }

    private String failKey(String identifier) {
        return FAIL_KEY_PREFIX + hash(identifier);
    }

    private String lockKey(String identifier) {
        return LOCK_KEY_PREFIX + hash(identifier);
    }

    /** raw identifier(S1 가능)를 키에 직접 넣지 않도록 SHA-256 해시(소문자 정규화 후). */
    private static String hash(String identifier) {
        String normalized = identifier.trim().toLowerCase();
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(normalized.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException ex) {
            // SHA-256 은 모든 JVM 에 존재 — 도달 불가.
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
