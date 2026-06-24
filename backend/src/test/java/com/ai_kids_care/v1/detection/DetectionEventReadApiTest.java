package com.ai_kids_care.v1.detection;

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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP contract tests for the staff detection-event read API (⑥ dashboard data source). A
 * KINDERGARTEN_ADMIN, a TEACHER and a GUARDIAN are seeded into the kindergarten owning a seeded
 * detection event, plus a foreign-tenant admin, to exercise read + role gate + tenant scope.
 */
@AutoConfigureMockMvc
class DetectionEventReadApiTest extends BaseIntegrationTest {

    private static final String PW = "Test@DetRead2026";
    private static final String ADMIN_LOGIN = "detread-admin";
    private static final String TEACHER_LOGIN = "detread-teacher";
    private static final String GUARDIAN_LOGIN = "detread-guardian";
    private static final String FOREIGN_ADMIN_LOGIN = "detread-foreign-admin";

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private ObjectMapper objectMapper;

    private long eventId;
    private long kindergartenId;
    private long foreignKindergartenId;

    @BeforeEach
    void setUp() {
        Map<String, Object> ev = jdbc.queryForMap(
                "SELECT event_id AS eid, kindergarten_id AS kg FROM detection_events ORDER BY event_id LIMIT 1");
        eventId = ((Number) ev.get("eid")).longValue();
        kindergartenId = ((Number) ev.get("kg")).longValue();
        foreignKindergartenId = jdbc.queryForObject(
                "SELECT kindergarten_id FROM kindergartens WHERE kindergarten_id <> ? ORDER BY kindergarten_id LIMIT 1",
                Long.class, kindergartenId);

        seedTenantUser(ADMIN_LOGIN, "detread-admin@test.local", "010-0000-6701", "KINDERGARTEN_ADMIN", kindergartenId);
        seedTenantUser(TEACHER_LOGIN, "detread-teacher@test.local", "010-0000-6702", "TEACHER", kindergartenId);
        seedTenantUser(GUARDIAN_LOGIN, "detread-guardian@test.local", "010-0000-6703", "GUARDIAN", kindergartenId);
        seedTenantUser(FOREIGN_ADMIN_LOGIN, "detread-foreign@test.local", "010-0000-6704", "KINDERGARTEN_ADMIN", foreignKindergartenId);
    }

    @Test
    void list_admin_returnsOwnKindergartenEvents() throws Exception {
        Cookie admin = login(ADMIN_LOGIN);
        mockMvc.perform(get("/api/v1/detection-events").cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.eventId == " + eventId + ")]").exists());
    }

    @Test
    void list_teacher_returns200() throws Exception {
        Cookie teacher = login(TEACHER_LOGIN);
        mockMvc.perform(get("/api/v1/detection-events").cookie(teacher))
                .andExpect(status().isOk());
    }

    @Test
    void list_guardian_returns403() throws Exception {
        Cookie guardian = login(GUARDIAN_LOGIN);
        mockMvc.perform(get("/api/v1/detection-events").cookie(guardian))
                .andExpect(status().isForbidden());
    }

    @Test
    void list_anonymous_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/detection-events"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getById_admin_returnsEvent() throws Exception {
        Cookie admin = login(ADMIN_LOGIN);
        mockMvc.perform(get("/api/v1/detection-events/{id}", eventId).cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventId").value(eventId));
    }

    @Test
    void getById_crossTenant_returnsHidden404() throws Exception {
        // A staff admin of a DIFFERENT kindergarten must not read this event (scoped to its own KG).
        Cookie foreignAdmin = login(FOREIGN_ADMIN_LOGIN);
        mockMvc.perform(get("/api/v1/detection-events/{id}", eventId).cookie(foreignAdmin))
                .andExpect(status().isNotFound());
    }

    // ── helpers (mirror EventReviewApiTest) ──────────────────────────────────────

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

    private void seedTenantUser(String loginId, String email, String phone, String role, long kgId) {
        jdbc.update("""
                INSERT INTO users (login_id, email, phone, password_hash, status, created_at, updated_at)
                VALUES (?, ?, ?, ?, 'ACTIVE', NOW(), NOW())
                ON CONFLICT (login_id) DO UPDATE SET password_hash = EXCLUDED.password_hash, status = 'ACTIVE'
                """, loginId, email, phone, passwordEncoder.encode(PW));
        jdbc.update("DELETE FROM user_role_assignments WHERE user_id = (SELECT user_id FROM users WHERE login_id = ?)", loginId);
        jdbc.update("DELETE FROM user_kindergarten_memberships WHERE user_id = (SELECT user_id FROM users WHERE login_id = ?)", loginId);
        jdbc.update("""
                INSERT INTO user_role_assignments (user_id, role, scope_type, scope_id, status, granted_at)
                SELECT user_id, ?::user_role_enum, 'KINDERGARTEN', ?, 'ACTIVE', NOW() FROM users WHERE login_id = ?
                """, role, kgId, loginId);
        jdbc.update("""
                INSERT INTO user_kindergarten_memberships (user_id, kindergarten_id, status, joined_at, created_at, updated_at)
                SELECT user_id, ?, 'ACTIVE', NOW(), NOW(), NOW() FROM users WHERE login_id = ?
                """, kgId, loginId);
    }
}
