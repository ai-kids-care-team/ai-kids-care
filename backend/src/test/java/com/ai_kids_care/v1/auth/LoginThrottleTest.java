package com.ai_kids_care.v1.auth;

import com.ai_kids_care.BaseIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 登录 暴力破解 防护(harden-auth-bootstrap-and-demo D2) 集成 테스트.
 *
 * <p>임계치 5(application.yml 기본값)를 가정한다. identifier 별 카운터/lock 은 Redis 에 있으므로
 * 각 테스트는 고유 identifier 를 쓰고 {@link #clearThrottle}로 키를 정리해 컨테이너 재사용에도 멱등하다.
 *
 * 검증:
 * <ul>
 *   <li>연속 실패가 임계치에 도달하면 이후 시도는 429(올바른 비밀번호여도)。</li>
 *   <li>잠금 전 성공 로그인은 카운터를 초기화한다(이후 실패가 누적되지 않음)。</li>
 *   <li>429 응답 바디는 generic 메시지만(비밀번호/해시/내부 필드 미포함)。</li>
 *   <li>잠금 발동 시 LOGIN_THROTTLE 감사가 기록된다(identifier 미기록)。</li>
 * </ul>
 */
@AutoConfigureMockMvc
class LoginThrottleTest extends BaseIntegrationTest {

    private static final String TEST_PASSWORD = "Test@Baseline2024";
    private static final int MAX_ATTEMPTS = 5; // application.yml login.throttle.max-attempts 기본값

    @Autowired private JdbcTemplate jdbc;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private StringRedisTemplate redisTemplate;

    private String loginId;

    @BeforeEach
    void setUp() {
        loginId = "throttle-user-" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        String hash = passwordEncoder.encode(TEST_PASSWORD);
        jdbc.update("""
                INSERT INTO users (login_id, email, phone, password_hash, status, created_at, updated_at)
                VALUES (?, ?, ?, ?, 'ACTIVE', NOW(), NOW())
                """,
                loginId,
                loginId + "@test-baseline.internal",
                "010" + loginId.substring(loginId.length() - 8),
                hash);
        jdbc.update("""
                INSERT INTO user_role_assignments (user_id, role, scope_type, scope_id, status, granted_at)
                SELECT user_id, 'SUPERADMIN', 'PLATFORM', NULL, 'ACTIVE', NOW()
                FROM users WHERE login_id = ?
                """, loginId);
        jdbc.update("""
                INSERT INTO superadmins (user_id, name, department, status, created_at, updated_at)
                SELECT user_id, '테스트관리자', 'Test Department', 'ACTIVE', NOW(), NOW()
                FROM users WHERE login_id = ?
                """, loginId);
        clearThrottle();
    }

    @Test
    void repeatedFailures_thenLockedReturns429EvenWithCorrectPassword() throws Exception {
        long auditBefore = throttleAuditCount();

        // 임계치까지 연속 실패(틀린 비밀번호) — 각 401.
        for (int i = 0; i < MAX_ATTEMPTS; i++) {
            attemptLogin(loginId, "wrong-password").andExpect(status().isUnauthorized());
        }

        // 이후 시도는 올바른 비밀번호여도 429.
        attemptLogin(loginId, TEST_PASSWORD)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error").value("Too many login attempts"))
                .andExpect(cookie().doesNotExist("AI_KIDS_CARE_SESSION"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        // 잠금 발동 시 LOGIN_THROTTLE 감사 1건 기록(identifier 미기록).
        assertThat(throttleAuditCount()).isGreaterThan(auditBefore);
    }

    @Test
    void successfulLoginBeforeThreshold_resetsFailureCounter() throws Exception {
        // 임계치 미만의 실패.
        for (int i = 0; i < MAX_ATTEMPTS - 1; i++) {
            attemptLogin(loginId, "wrong-password").andExpect(status().isUnauthorized());
        }

        // 성공 로그인 → 카운터 초기화.
        attemptLogin(loginId, TEST_PASSWORD).andExpect(status().isOk());

        // 다시 임계치 미만 실패해도 잠금되지 않아야 한다(누적 초기화됨).
        for (int i = 0; i < MAX_ATTEMPTS - 1; i++) {
            attemptLogin(loginId, "wrong-password").andExpect(status().isUnauthorized());
        }
        // 여전히 잠금 아님 → 올바른 비밀번호로 200.
        attemptLogin(loginId, TEST_PASSWORD).andExpect(status().isOk());
    }

    private org.springframework.test.web.servlet.ResultActions attemptLogin(
            String identifier, String password) throws Exception {
        return mockMvc.perform(withRealCsrf(post("/api/v1/auth/login"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        Map.of("identifier", identifier, "password", password))));
    }

    private void clearThrottle() {
        // identifier 해시 키를 직접 못 만드므로 prefix 전체를 스캔 삭제(테스트 한정).
        var failKeys = redisTemplate.keys("login:throttle:fail:*");
        if (failKeys != null && !failKeys.isEmpty()) {
            redisTemplate.delete(failKeys);
        }
        var lockKeys = redisTemplate.keys("login:throttle:lock:*");
        if (lockKeys != null && !lockKeys.isEmpty()) {
            redisTemplate.delete(lockKeys);
        }
    }

    private long throttleAuditCount() {
        Long n = jdbc.queryForObject(
                "SELECT count(*) FROM audit_logs WHERE action = 'LOGIN_THROTTLE'", Long.class);
        return n == null ? 0L : n;
    }

    private MockHttpServletRequestBuilder withRealCsrf(
            MockHttpServletRequestBuilder request) throws Exception {
        MvcResult csrf = mockMvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("XSRF-TOKEN"))
                .andReturn();
        String token = objectMapper.readTree(csrf.getResponse().getContentAsString())
                .get("token").asText();
        return request
                .cookie(csrf.getResponse().getCookie("XSRF-TOKEN"))
                .header("X-XSRF-TOKEN", token);
    }
}
