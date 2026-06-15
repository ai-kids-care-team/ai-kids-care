package com.ai_kids_care.v1.security;

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

import java.util.HashMap;
import java.util.Map;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Cross-tenant isolation for the published tenant-scoped resources.
 *
 * The principal is scoped to kindergarten 2. Seed data places rooms/cameras/streams/
 * sessions in kindergarten 1 and rooms/cameras in kindergarten 2, so kindergarten 1 ids
 * are genuine-but-foreign for this principal. Foreign resources must be hidden: list
 * results exclude foreign rows, get-by-id returns a hidden {@code 404}, and a client
 * {@code kindergartenId} override on write is rejected with a hidden {@code 404} rather
 * than switching tenant. Reachable own-tenant reads prove the 404s are tenant scoping,
 * not a blanket deny.
 */
@AutoConfigureMockMvc
class TenantIsolationIntegrationTest extends BaseIntegrationTest {

    private static final String LOGIN_ID = "tenant-iso-test-user";
    private static final String PASSWORD = "Test@Tenant2024";
    private static final long OWN_KG = 2L;
    private static final long FOREIGN_KG = 1L;

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private ObjectMapper objectMapper;

    @BeforeEach
    void setUpTeacherInOwnKindergarten() {
        jdbc.update("""
                INSERT INTO users (login_id, email, phone, password_hash, status, created_at, updated_at)
                VALUES (?, ?, ?, ?, 'ACTIVE', NOW(), NOW())
                ON CONFLICT (login_id) DO UPDATE
                    SET password_hash = EXCLUDED.password_hash, status = 'ACTIVE'
                """,
                LOGIN_ID, LOGIN_ID + "@test-tenant.internal", "010-0000-9996",
                passwordEncoder.encode(PASSWORD));
        configureKindergartenRole("TEACHER", OWN_KG);
    }

    @Test
    void teacherCannotReadForeignTenantResourcesByValidId() throws Exception {
        Cookie session = login();
        assertForeignHidden("/api/v1/rooms/{id}", foreignId("rooms", "room_id"), session);
        assertForeignHidden("/api/v1/cctv_cameras/{id}", foreignId("cctv_cameras", "camera_id"), session);
        assertForeignHidden("/api/v1/camera_streams/{id}", foreignId("camera_streams", "stream_id"), session);
        assertForeignHidden("/api/v1/detection_sessions/{id}", foreignId("detection_sessions", "session_id"), session);
    }

    @Test
    void teacherListSeesOnlyOwnTenantRoomsAndOwnRoomIsReachable() throws Exception {
        Cookie session = login();

        MvcResult list = mockMvc.perform(get("/api/v1/rooms").cookie(session))
                .andExpect(status().isOk())
                .andReturn();
        boolean anyForeign = StreamSupport.stream(
                        objectMapper.readTree(list.getResponse().getContentAsString())
                                .path("content")
                                .spliterator(),
                        false)
                .anyMatch(item -> item.path("kindergartenId").asLong() != OWN_KG);
        assertThat(anyForeign).isFalse();

        long ownRoom = ownId("rooms", "room_id");
        mockMvc.perform(get("/api/v1/rooms/{id}", ownRoom).cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kindergartenId").value((int) OWN_KG));
    }

    @Test
    void adminWriteRejectsForeignTenantKindergartenOverride() throws Exception {
        configureKindergartenRole("KINDERGARTEN_ADMIN", OWN_KG);
        Cookie session = login();

        Map<String, Object> body = new HashMap<>();
        body.put("kindergartenId", FOREIGN_KG);
        body.put("name", "Cross-tenant room");
        body.put("roomType", "교실");
        body.put("status", "ACTIVE");

        mockMvc.perform(withRealCsrf(post("/api/v1/rooms"))
                        .cookie(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Resource not found"));
    }

    private void assertForeignHidden(String template, long foreignId, Cookie session) throws Exception {
        mockMvc.perform(get(template, foreignId).cookie(session))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Resource not found"));
    }

    private long foreignId(String table, String idColumn) {
        return tenantId(table, idColumn, FOREIGN_KG);
    }

    private long ownId(String table, String idColumn) {
        return tenantId(table, idColumn, OWN_KG);
    }

    private long tenantId(String table, String idColumn, long kindergartenId) {
        return jdbc.queryForObject(
                "SELECT " + idColumn + " FROM " + table
                        + " WHERE kindergarten_id = ? ORDER BY " + idColumn + " LIMIT 1",
                Long.class,
                kindergartenId);
    }

    private void configureKindergartenRole(String role, long kindergartenId) {
        jdbc.update("""
                DELETE FROM user_kindergarten_memberships
                WHERE user_id = (SELECT user_id FROM users WHERE login_id = ?)
                """, LOGIN_ID);
        jdbc.update("""
                DELETE FROM user_role_assignments
                WHERE user_id = (SELECT user_id FROM users WHERE login_id = ?)
                """, LOGIN_ID);
        jdbc.update("""
                INSERT INTO user_role_assignments
                    (user_id, role, scope_type, scope_id, status, granted_at)
                SELECT user_id, ?::user_role_enum, 'KINDERGARTEN', ?, 'ACTIVE', NOW()
                FROM users WHERE login_id = ?
                """, role, kindergartenId, LOGIN_ID);
        jdbc.update("""
                INSERT INTO user_kindergarten_memberships
                    (user_id, kindergarten_id, status, joined_at, created_at, updated_at)
                SELECT user_id, ?, 'ACTIVE', NOW(), NOW(), NOW()
                FROM users WHERE login_id = ?
                """, kindergartenId, LOGIN_ID);
    }

    private Cookie login() throws Exception {
        MvcResult result = mockMvc.perform(withRealCsrf(post("/api/v1/auth/login"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("identifier", LOGIN_ID, "password", PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("AI_KIDS_CARE_SESSION"))
                .andReturn();
        return result.getResponse().getCookie("AI_KIDS_CARE_SESSION");
    }

    private MockHttpServletRequestBuilder withRealCsrf(
            MockHttpServletRequestBuilder request
    ) throws Exception {
        MvcResult csrf = mockMvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isOk())
                .andReturn();
        String token = objectMapper.readTree(csrf.getResponse().getContentAsString())
                .get("token").asText();
        return request
                .cookie(csrf.getResponse().getCookie("XSRF-TOKEN"))
                .header("X-XSRF-TOKEN", token);
    }
}
