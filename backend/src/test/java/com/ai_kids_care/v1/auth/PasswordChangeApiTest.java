package com.ai_kids_care.v1.auth;

import com.ai_kids_care.BaseIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
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
 * UX-07 wire-password-management, backend lane task 1: authenticated self-service
 * {@code POST /api/v1/auth/change-password}.
 *
 * Covers: success path (204, bcrypt updated, all sessions revoked so the same cookie 401s on the
 * next request), wrong current password (400, password unchanged), non-compliant new password
 * (400 via existing {@code @ValidPassword}), unauthenticated caller (401 default-deny — endpoint
 * is intentionally NOT on the public allowlist), and missing CSRF (403).
 */
@AutoConfigureMockMvc
class PasswordChangeApiTest extends BaseIntegrationTest {

    private static final String TEST_PASSWORD = "Test@Baseline2024";
    private static final String NEW_PASSWORD = "NewSecret2024!";

    @Autowired private JdbcTemplate jdbc;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    private String loginId;

    @BeforeEach
    void setUp() {
        loginId = "pwchange-user-" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
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
    }

    @Test
    void changePassword_correctCurrentAndCompliantNew_returns204AndRevokesAllSessions() throws Exception {
        Cookie sessionCookie = loginSessionCookie();

        mockMvc.perform(withRealCsrf(post("/api/v1/auth/change-password"))
                        .cookie(sessionCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("currentPassword", TEST_PASSWORD, "newPassword", NEW_PASSWORD))))
                .andExpect(status().isNoContent());

        // New bcrypt persisted.
        String storedHash = jdbc.queryForObject(
                "SELECT password_hash FROM users WHERE login_id = ?", String.class, loginId);
        assertThat(passwordEncoder.matches(NEW_PASSWORD, storedHash)).isTrue();
        assertThat(passwordEncoder.matches(TEST_PASSWORD, storedHash)).isFalse();

        // Session used to change the password is itself revoked (all-devices revoke).
        mockMvc.perform(get("/api/v1/auth/session").cookie(sessionCookie))
                .andExpect(status().isUnauthorized());

        // New password logs in fine.
        mockMvc.perform(withRealCsrf(post("/api/v1/auth/login"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("identifier", loginId, "password", NEW_PASSWORD))))
                .andExpect(status().isOk());
    }

    @Test
    void changePassword_wrongCurrentPassword_returns400AndLeavesPasswordUnchanged() throws Exception {
        Cookie sessionCookie = loginSessionCookie();

        mockMvc.perform(withRealCsrf(post("/api/v1/auth/change-password"))
                        .cookie(sessionCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("currentPassword", "definitely-wrong", "newPassword", NEW_PASSWORD))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("현재 비밀번호가 올바르지 않습니다."));

        String storedHash = jdbc.queryForObject(
                "SELECT password_hash FROM users WHERE login_id = ?", String.class, loginId);
        assertThat(passwordEncoder.matches(TEST_PASSWORD, storedHash)).isTrue();

        // Session must still be valid — a bad-current-password attempt does not revoke anything.
        mockMvc.perform(get("/api/v1/auth/session").cookie(sessionCookie))
                .andExpect(status().isOk());
    }

    @Test
    void changePassword_nonCompliantNewPassword_returns400AndLeavesPasswordUnchanged() throws Exception {
        Cookie sessionCookie = loginSessionCookie();

        mockMvc.perform(withRealCsrf(post("/api/v1/auth/change-password"))
                        .cookie(sessionCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("currentPassword", TEST_PASSWORD, "newPassword", "short1"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").isNotEmpty());

        String storedHash = jdbc.queryForObject(
                "SELECT password_hash FROM users WHERE login_id = ?", String.class, loginId);
        assertThat(passwordEncoder.matches(TEST_PASSWORD, storedHash)).isTrue();
    }

    @Test
    void changePassword_unauthenticatedCaller_returns401DefaultDeny() throws Exception {
        mockMvc.perform(withRealCsrf(post("/api/v1/auth/change-password"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("currentPassword", TEST_PASSWORD, "newPassword", NEW_PASSWORD))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void changePassword_missingCsrfToken_returns403() throws Exception {
        Cookie sessionCookie = loginSessionCookie();

        mockMvc.perform(post("/api/v1/auth/change-password")
                        .cookie(sessionCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("currentPassword", TEST_PASSWORD, "newPassword", NEW_PASSWORD))))
                .andExpect(status().isForbidden());
    }

    private Cookie loginSessionCookie() throws Exception {
        MvcResult login = mockMvc.perform(withRealCsrf(post("/api/v1/auth/login"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("identifier", loginId, "password", TEST_PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("AI_KIDS_CARE_SESSION"))
                .andReturn();
        return login.getResponse().getCookie("AI_KIDS_CARE_SESSION");
    }

    private MockHttpServletRequestBuilder withRealCsrf(
            MockHttpServletRequestBuilder request) throws Exception {
        MvcResult csrf = mockMvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("XSRF-TOKEN"))
                .andReturn();
        String token = objectMapper.readTree(csrf.getResponse().getContentAsString())
                .get("token")
                .asText();
        return request
                .cookie(csrf.getResponse().getCookie("XSRF-TOKEN"))
                .header("X-XSRF-TOKEN", token);
    }
}
