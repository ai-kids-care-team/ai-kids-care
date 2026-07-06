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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * UX-08: HTTP contract tests for the self-service {@code GET/PUT /api/v1/notification_rules/me}
 * endpoints (api-contract.md). Two GUARDIAN users are seeded into the SAME kindergarten (so both
 * pass the coarse {@code NOTIFICATION_PREFERENCE_MANAGE} gate) to exercise self-scoping / cross-user
 * hiding on the shared {@code (kindergarten_id, user_id)} canonical row.
 */
@AutoConfigureMockMvc
class NotificationPreferenceApiTest extends BaseIntegrationTest {

    private static final String PW = "Test@NotifPref2026";
    private static final String LOGIN_A = "notifpref-api-user-a";
    private static final String LOGIN_B = "notifpref-api-user-b";

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private ObjectMapper objectMapper;

    private long kindergartenId;

    @BeforeEach
    void setUp() {
        kindergartenId = jdbc.queryForObject(
                "SELECT kindergarten_id FROM kindergartens ORDER BY kindergarten_id LIMIT 1", Long.class);
        seedTenantUser(LOGIN_A, "notifpref-a@test.local", "010-0000-8901", kindergartenId);
        seedTenantUser(LOGIN_B, "notifpref-b@test.local", "010-0000-8902", kindergartenId);
        jdbc.update("DELETE FROM notification_rules WHERE user_id IN "
                + "(SELECT user_id FROM users WHERE login_id IN (?, ?)) AND target_type = 'KINDERGARTEN'::notification_target_type",
                LOGIN_A, LOGIN_B);
    }

    @Test
    void getMine_withNoCanonicalRow_returnsDefault() throws Exception {
        Cookie a = login(LOGIN_A);
        mockMvc.perform(get("/api/v1/notification_rules/me").cookie(a))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.quietHoursStart").doesNotExist())
                .andExpect(jsonPath("$.quietHoursEnd").doesNotExist());
    }

    @Test
    void putThenGet_roundTripsPersistedValues() throws Exception {
        Cookie a = login(LOGIN_A);
        mockMvc.perform(withCsrf(put("/api/v1/notification_rules/me")).cookie(a)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("enabled", false, "quietHoursStart", "22:00", "quietHoursEnd", "06:30"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.quietHoursStart").value("22:00"))
                .andExpect(jsonPath("$.quietHoursEnd").value("06:30"));

        mockMvc.perform(get("/api/v1/notification_rules/me").cookie(a))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.quietHoursStart").value("22:00"))
                .andExpect(jsonPath("$.quietHoursEnd").value("06:30"));
    }

    @Test
    void put_updatesExistingRowInPlace_doesNotDuplicate() throws Exception {
        Cookie a = login(LOGIN_A);
        mockMvc.perform(withCsrf(put("/api/v1/notification_rules/me")).cookie(a)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("enabled", true))))
                .andExpect(status().isOk());
        mockMvc.perform(withCsrf(put("/api/v1/notification_rules/me")).cookie(a)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("enabled", false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));

        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM notification_rules WHERE user_id = (SELECT user_id FROM users WHERE login_id = ?) "
                        + "AND kindergarten_id = ? AND target_type = 'KINDERGARTEN'::notification_target_type",
                Integer.class, LOGIN_A, kindergartenId);
        org.assertj.core.api.Assertions.assertThat(count).isEqualTo(1);
    }

    @Test
    void put_onlyStartSet_returns400() throws Exception {
        Cookie a = login(LOGIN_A);
        mockMvc.perform(withCsrf(put("/api/v1/notification_rules/me")).cookie(a)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("enabled", true, "quietHoursStart", "22:00"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").isNotEmpty());
    }

    @Test
    void put_onlyEndSet_returns400() throws Exception {
        Cookie a = login(LOGIN_A);
        mockMvc.perform(withCsrf(put("/api/v1/notification_rules/me")).cookie(a)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("enabled", true, "quietHoursEnd", "06:30"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void put_malformedTimeFormat_returns400() throws Exception {
        Cookie a = login(LOGIN_A);
        mockMvc.perform(withCsrf(put("/api/v1/notification_rules/me")).cookie(a)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("enabled", true, "quietHoursStart", "25:99", "quietHoursEnd", "06:30"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void put_missingEnabled_returns400() throws Exception {
        Cookie a = login(LOGIN_A);
        mockMvc.perform(withCsrf(put("/api/v1/notification_rules/me")).cookie(a)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void crossUser_isolated_bDoesNotSeeAsPreference() throws Exception {
        Cookie a = login(LOGIN_A);
        mockMvc.perform(withCsrf(put("/api/v1/notification_rules/me")).cookie(a)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("enabled", false, "quietHoursStart", "20:00", "quietHoursEnd", "08:00"))))
                .andExpect(status().isOk());

        // B has never set anything → B's own GET must still show the implicit default, unaffected by A's row.
        Cookie b = login(LOGIN_B);
        mockMvc.perform(get("/api/v1/notification_rules/me").cookie(b))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.quietHoursStart").doesNotExist());
    }

    @Test
    void crossUser_bPutDoesNotMutateAsRow() throws Exception {
        Cookie a = login(LOGIN_A);
        mockMvc.perform(withCsrf(put("/api/v1/notification_rules/me")).cookie(a)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("enabled", true))))
                .andExpect(status().isOk());

        Cookie b = login(LOGIN_B);
        mockMvc.perform(withCsrf(put("/api/v1/notification_rules/me")).cookie(b)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("enabled", false))))
                .andExpect(status().isOk());

        // A's row must be untouched by B's write.
        mockMvc.perform(get("/api/v1/notification_rules/me").cookie(a))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true));
    }

    @Test
    void anonymousGet_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/notification_rules/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void anonymousPut_isRejected() throws Exception {
        mockMvc.perform(put("/api/v1/notification_rules/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("enabled", true))))
                .andExpect(status().is4xxClientError());
    }

    // ── helpers ──────────────────────────────────────────────────────────────

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

    private void seedTenantUser(String loginId, String email, String phone, long kgId) {
        jdbc.update("""
                INSERT INTO users (login_id, email, phone, password_hash, status, created_at, updated_at)
                VALUES (?, ?, ?, ?, 'ACTIVE', NOW(), NOW())
                ON CONFLICT (login_id) DO UPDATE SET password_hash = EXCLUDED.password_hash, status = 'ACTIVE'
                """, loginId, email, phone, passwordEncoder.encode(PW));
        jdbc.update("DELETE FROM user_role_assignments WHERE user_id = (SELECT user_id FROM users WHERE login_id = ?)", loginId);
        jdbc.update("DELETE FROM user_kindergarten_memberships WHERE user_id = (SELECT user_id FROM users WHERE login_id = ?)", loginId);
        jdbc.update("""
                INSERT INTO user_role_assignments (user_id, role, scope_type, scope_id, status, granted_at)
                SELECT user_id, 'GUARDIAN'::user_role_enum, 'KINDERGARTEN', ?, 'ACTIVE', NOW() FROM users WHERE login_id = ?
                """, kgId, loginId);
        jdbc.update("""
                INSERT INTO user_kindergarten_memberships (user_id, kindergarten_id, status, joined_at, created_at, updated_at)
                SELECT user_id, ?, 'ACTIVE', NOW(), NOW(), NOW() FROM users WHERE login_id = ?
                """, kgId, loginId);
    }
}
