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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Auth contract for the detection-event SSE stream ({@code GET /api/v1/detection-events/stream}).
 * Staff (KINDERGARTEN_ADMIN / TEACHER) of an active kindergarten may open the stream (async
 * started); a GUARDIAN is forbidden and an anonymous caller unauthorized.
 */
@AutoConfigureMockMvc
class DetectionEventStreamAuthTest extends BaseIntegrationTest {

    private static final String PW = "Test@Stream2026";
    private static final String ADMIN_LOGIN = "stream-admin";
    private static final String TEACHER_LOGIN = "stream-teacher";
    private static final String GUARDIAN_LOGIN = "stream-guardian";

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private ObjectMapper objectMapper;

    private long kindergartenId;

    @BeforeEach
    void setUp() {
        kindergartenId = jdbc.queryForObject(
                "SELECT kindergarten_id FROM kindergartens ORDER BY kindergarten_id LIMIT 1", Long.class);
        seedTenantUser(ADMIN_LOGIN, "stream-admin@test.local", "010-0000-6801", "KINDERGARTEN_ADMIN", kindergartenId);
        seedTenantUser(TEACHER_LOGIN, "stream-teacher@test.local", "010-0000-6802", "TEACHER", kindergartenId);
        seedTenantUser(GUARDIAN_LOGIN, "stream-guardian@test.local", "010-0000-6803", "GUARDIAN", kindergartenId);
    }

    @Test
    void stream_admin_asyncStarted() throws Exception {
        mockMvc.perform(get("/api/v1/detection-events/stream").cookie(login(ADMIN_LOGIN)))
                .andExpect(request().asyncStarted());
    }

    @Test
    void stream_teacher_asyncStarted() throws Exception {
        mockMvc.perform(get("/api/v1/detection-events/stream").cookie(login(TEACHER_LOGIN)))
                .andExpect(request().asyncStarted());
    }

    @Test
    void stream_guardian_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/detection-events/stream").cookie(login(GUARDIAN_LOGIN)))
                .andExpect(status().isForbidden());
    }

    @Test
    void stream_anonymous_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/detection-events/stream"))
                .andExpect(status().isUnauthorized());
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
