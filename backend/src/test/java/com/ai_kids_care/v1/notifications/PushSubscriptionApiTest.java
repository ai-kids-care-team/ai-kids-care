package com.ai_kids_care.v1.notifications;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP contract tests for the published push_subscriptions self-service API.
 * Two PLATFORM SUPERADMIN users are upserted (push_subscriptions is user-scoped, no tenant) to
 * exercise self-scoping and cross-user hiding.
 */
@AutoConfigureMockMvc
class PushSubscriptionApiTest extends BaseIntegrationTest {

    private static final String PW = "Test@PushSub2026";
    private static final String LOGIN_A = "pushsub-api-user-a";
    private static final String LOGIN_B = "pushsub-api-user-b";

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private ObjectMapper objectMapper;

    @BeforeEach
    void seedUsers() {
        seedSuperadmin(LOGIN_A, "pushsub-a@test.local", "010-0000-8801");
        seedSuperadmin(LOGIN_B, "pushsub-b@test.local", "010-0000-8802");
        jdbc.update("DELETE FROM push_subscriptions WHERE user_id IN "
                + "(SELECT user_id FROM users WHERE login_id IN (?, ?))", LOGIN_A, LOGIN_B);
    }

    @Test
    void register_returns201_andDoesNotEchoAddress() throws Exception {
        Cookie session = login(LOGIN_A);
        String body = mockMvc.perform(withCsrf(post("/api/v1/push_subscriptions")).cookie(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("provider", "PUSHOVER", "address", "pushover-key-aaa", "deviceLabel", "iphone"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.pushSubscriptionId").exists())
                .andExpect(jsonPath("$.provider").value("PUSHOVER"))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).as("delivery address (user key) must not be echoed").doesNotContain("pushover-key-aaa");
    }

    @Test
    void list_returnsOnlyCallersOwnSubscriptions() throws Exception {
        Cookie a = login(LOGIN_A);
        long idA = register(a, "key-a-only");
        Cookie b = login(LOGIN_B);

        mockMvc.perform(get("/api/v1/push_subscriptions").cookie(b))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.pushSubscriptionId == " + idA + ")]").doesNotExist());
        mockMvc.perform(get("/api/v1/push_subscriptions").cookie(a))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.pushSubscriptionId == " + idA + ")]").exists());
    }

    @Test
    void crossUserDelete_returnsHidden404() throws Exception {
        Cookie a = login(LOGIN_A);
        long idA = register(a, "key-a-protected");
        Cookie b = login(LOGIN_B);

        mockMvc.perform(withCsrf(delete("/api/v1/push_subscriptions/{id}", idA)).cookie(b))
                .andExpect(status().isNotFound());
        // still there for A
        mockMvc.perform(get("/api/v1/push_subscriptions").cookie(a))
                .andExpect(jsonPath("$[?(@.pushSubscriptionId == " + idA + ")]").exists());
    }

    @Test
    void duplicateRegistration_returns409() throws Exception {
        Cookie a = login(LOGIN_A);
        register(a, "dup-key");
        mockMvc.perform(withCsrf(post("/api/v1/push_subscriptions")).cookie(a)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("provider", "PUSHOVER", "address", "dup-key"))))
                .andExpect(status().isConflict());
    }

    @Test
    void unsupportedProvider_returns400() throws Exception {
        Cookie a = login(LOGIN_A);
        mockMvc.perform(withCsrf(post("/api/v1/push_subscriptions")).cookie(a)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("provider", "FCM", "address", "some-key"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void crossUserUpdate_returnsHidden404() throws Exception {
        Cookie a = login(LOGIN_A);
        long idA = register(a, "key-a-update-protected");
        Cookie b = login(LOGIN_B);

        mockMvc.perform(withCsrf(put("/api/v1/push_subscriptions/{id}", idA)).cookie(b)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("deviceLabel", "hijacked"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void update_ownSubscription_returns200WithMutation() throws Exception {
        Cookie a = login(LOGIN_A);
        long idA = register(a, "key-a-updatable");

        mockMvc.perform(withCsrf(put("/api/v1/push_subscriptions/{id}", idA)).cookie(a)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("deviceLabel", "tablet", "status", "DISABLED"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deviceLabel").value("tablet"))
                .andExpect(jsonPath("$.status").value("DISABLED"));
    }

    @Test
    void anonymousList_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/push_subscriptions"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void anonymousWrite_isRejected() throws Exception {
        // No session, no CSRF: blocked at CSRF (403) or auth (401) — either is a correct rejection.
        mockMvc.perform(post("/api/v1/push_subscriptions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("address", "x"))))
                .andExpect(status().is4xxClientError());
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private long register(Cookie session, String address) throws Exception {
        String body = mockMvc.perform(withCsrf(post("/api/v1/push_subscriptions")).cookie(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("provider", "PUSHOVER", "address", address))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("pushSubscriptionId").asLong();
    }

    private Cookie login(String loginId) throws Exception {
        MvcResult login = mockMvc.perform(withCsrf(post("/api/v1/auth/login"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("identifier", loginId, "password", PW))))
                .andExpect(status().isOk())
                .andReturn();
        return login.getResponse().getCookie("AI_KIDS_CARE_SESSION");
    }

    private MockHttpServletRequestBuilder withCsrf(MockHttpServletRequestBuilder request) throws Exception {
        MvcResult csrf = mockMvc.perform(get("/api/v1/auth/csrf")).andExpect(status().isOk()).andReturn();
        String token = objectMapper.readTree(csrf.getResponse().getContentAsString()).get("token").asText();
        return request.cookie(csrf.getResponse().getCookie("XSRF-TOKEN")).header("X-XSRF-TOKEN", token);
    }

    private void seedSuperadmin(String loginId, String email, String phone) {
        jdbc.update("""
                INSERT INTO users (login_id, email, phone, password_hash, status, created_at, updated_at)
                VALUES (?, ?, ?, ?, 'ACTIVE', NOW(), NOW())
                ON CONFLICT (login_id) DO UPDATE SET password_hash = EXCLUDED.password_hash, status = 'ACTIVE'
                """, loginId, email, phone, passwordEncoder.encode(PW));
        jdbc.update("DELETE FROM user_kindergarten_memberships WHERE user_id = (SELECT user_id FROM users WHERE login_id = ?)", loginId);
        jdbc.update("DELETE FROM user_role_assignments WHERE user_id = (SELECT user_id FROM users WHERE login_id = ?)", loginId);
        jdbc.update("""
                INSERT INTO user_role_assignments (user_id, role, scope_type, scope_id, status, granted_at)
                SELECT user_id, 'SUPERADMIN', 'PLATFORM', NULL, 'ACTIVE', NOW() FROM users WHERE login_id = ?
                """, loginId);
        jdbc.update("""
                INSERT INTO superadmins (user_id, name, department, status, created_at, updated_at)
                SELECT user_id, '테스트', 'Test', 'ACTIVE', NOW(), NOW() FROM users WHERE login_id = ?
                ON CONFLICT (user_id) DO UPDATE SET status = 'ACTIVE'
                """, loginId);
    }
}
