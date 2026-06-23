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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP contract tests for the event review workflow. A KINDERGARTEN_ADMIN and a GUARDIAN are seeded
 * into the kindergarten that owns a seeded detection event, to exercise confirm + read + auth.
 */
@AutoConfigureMockMvc
class EventReviewApiTest extends BaseIntegrationTest {

    private static final String PW = "Test@EvReview2026";
    private static final String ADMIN_LOGIN = "evrev-admin";
    private static final String TEACHER_LOGIN = "evrev-teacher";
    private static final String GUARDIAN_LOGIN = "evrev-guardian";
    private static final String FOREIGN_ADMIN_LOGIN = "evrev-foreign-admin";

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
        // A different existing kindergarten — for the cross-tenant tests (a foreign-tenant staff user).
        foreignKindergartenId = jdbc.queryForObject(
                "SELECT kindergarten_id FROM kindergartens WHERE kindergarten_id <> ? ORDER BY kindergarten_id LIMIT 1",
                Long.class, kindergartenId);

        seedTenantUser(ADMIN_LOGIN, "evrev-admin@test.local", "010-0000-6601", "KINDERGARTEN_ADMIN", kindergartenId);
        seedTenantUser(TEACHER_LOGIN, "evrev-teacher@test.local", "010-0000-6603", "TEACHER", kindergartenId);
        seedTenantUser(GUARDIAN_LOGIN, "evrev-guardian@test.local", "010-0000-6602", "GUARDIAN", kindergartenId);
        seedTenantUser(FOREIGN_ADMIN_LOGIN, "evrev-foreign@test.local", "010-0000-6604", "KINDERGARTEN_ADMIN", foreignKindergartenId);
        // reset event status so RESOLVED assertions are meaningful across reruns
        jdbc.update("UPDATE detection_events SET status = 'OPEN'::event_status_enum WHERE event_id = ?", eventId);
    }

    @Test
    void confirm_writesReviewAndUpdatesStatus() throws Exception {
        Cookie admin = login(ADMIN_LOGIN);
        mockMvc.perform(withCsrf(post("/api/v1/event_reviews")).cookie(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("eventId", eventId, "resultStatus", "RESOLVED", "comment", "ok"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.resultStatus").value("RESOLVED"));

        Integer reviews = jdbc.queryForObject(
                "SELECT count(*) FROM event_reviews WHERE event_id = ? AND result_status = 'RESOLVED'::event_status_enum",
                Integer.class, eventId);
        assertThat(reviews).isGreaterThanOrEqualTo(1);
        String stat = jdbc.queryForObject("SELECT status FROM detection_events WHERE event_id = ?", String.class, eventId);
        assertThat(stat).isEqualTo("RESOLVED");
    }

    @Test
    void confirm_openResultStatus_returns400() throws Exception {
        Cookie admin = login(ADMIN_LOGIN);
        mockMvc.perform(withCsrf(post("/api/v1/event_reviews")).cookie(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("eventId", eventId, "resultStatus", "OPEN"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void confirm_teacherRole_returns201() throws Exception {
        Cookie teacher = login(TEACHER_LOGIN);
        mockMvc.perform(withCsrf(post("/api/v1/event_reviews")).cookie(teacher)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("eventId", eventId, "resultStatus", "IN_REVIEW"))))
                .andExpect(status().isCreated());
    }

    @Test
    void confirm_crossTenantEvent_returnsHidden404() throws Exception {
        // A staff admin of a DIFFERENT kindergarten cannot confirm this event (in kindergartenId).
        Cookie foreignAdmin = login(FOREIGN_ADMIN_LOGIN);
        mockMvc.perform(withCsrf(post("/api/v1/event_reviews")).cookie(foreignAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("eventId", eventId, "resultStatus", "RESOLVED"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void get_crossTenantReview_returnsHidden404() throws Exception {
        Cookie admin = login(ADMIN_LOGIN);
        String body = mockMvc.perform(withCsrf(post("/api/v1/event_reviews")).cookie(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("eventId", eventId, "resultStatus", "ACKNOWLEDGED"))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long reviewId = objectMapper.readTree(body).get("reviewId").asLong();

        Cookie foreignAdmin = login(FOREIGN_ADMIN_LOGIN);
        mockMvc.perform(get("/api/v1/event_reviews/{id}", reviewId).cookie(foreignAdmin))
                .andExpect(status().isNotFound());
    }

    @Test
    void confirm_guardianRole_returns403() throws Exception {
        Cookie guardian = login(GUARDIAN_LOGIN);
        mockMvc.perform(withCsrf(post("/api/v1/event_reviews")).cookie(guardian)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("eventId", eventId, "resultStatus", "RESOLVED"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void anonymous_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/event_reviews")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/event_reviews")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("eventId", eventId, "resultStatus", "RESOLVED"))))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void list_returnsReviewHistoryForEvent() throws Exception {
        Cookie admin = login(ADMIN_LOGIN);
        mockMvc.perform(withCsrf(post("/api/v1/event_reviews")).cookie(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("eventId", eventId, "resultStatus", "ACKNOWLEDGED"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/event_reviews").param("eventId", String.valueOf(eventId)).cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.eventId == " + eventId + ")]").exists());
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

    private void seedTenantUser(String loginId, String email, String phone, String role, long kgId) {
        // Upsert the user (do NOT delete — audit_logs FK-references it after login). Only the
        // role assignment / membership are rebuilt (not referenced by audit_logs).
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
