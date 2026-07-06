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

import java.util.Map;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * camera-endpoint-hygiene (C6-gap-a): {@code kindergartenId} on the two camera GET-list
 * endpoints is {@code required = false} — the front end must stop sending it (see the
 * change proposal), and this test locks in that the service layer already resolves tenant
 * scope from {@link EffectiveAuthorizationContextHolder}'s ThreadLocal regardless of whether
 * the (now-optional) query param is present. Both endpoints must keep returning only the
 * caller's own-tenant rows — omitting the param must not 400, and must not leak or blank out
 * tenant scoping.
 *
 * Seed data: kindergarten 2 owns cctv_cameras.camera_id=2 and camera_streams.stream_id=2
 * (see db/initdb/28_cctv_cameras_seed.sql, 39_camera_streams_seed.sql), so a
 * KINDERGARTEN_ADMIN scoped to kindergarten 2 has a genuine, non-empty own-tenant list for
 * both resources.
 */
@AutoConfigureMockMvc
class CameraEndpointOptionalKindergartenIdTest extends BaseIntegrationTest {

    private static final String LOGIN_ID = "camera-hygiene-test-user";
    private static final String PASSWORD = "Test@CameraHygiene2024";
    private static final long OWN_KG = 2L;

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private ObjectMapper objectMapper;

    @BeforeEach
    void setUpKindergartenAdminInOwnKindergarten() {
        jdbc.update("""
                INSERT INTO users (login_id, email, phone, password_hash, status, created_at, updated_at)
                VALUES (?, ?, ?, ?, 'ACTIVE', NOW(), NOW())
                ON CONFLICT (login_id) DO UPDATE
                    SET password_hash = EXCLUDED.password_hash, status = 'ACTIVE'
                """,
                LOGIN_ID, LOGIN_ID + "@test-tenant.internal", "010-0000-9992",
                passwordEncoder.encode(PASSWORD));
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
                SELECT user_id, 'KINDERGARTEN_ADMIN'::user_role_enum, 'KINDERGARTEN', ?, 'ACTIVE', NOW()
                FROM users WHERE login_id = ?
                """, OWN_KG, LOGIN_ID);
        jdbc.update("""
                INSERT INTO user_kindergarten_memberships
                    (user_id, kindergarten_id, status, joined_at, created_at, updated_at)
                SELECT user_id, ?, 'ACTIVE', NOW(), NOW(), NOW()
                FROM users WHERE login_id = ?
                """, OWN_KG, LOGIN_ID);
    }

    @Test
    void listCctvCamerasWithoutKindergartenIdParamReturnsOwnTenantDataOnly() throws Exception {
        Cookie session = login();

        MvcResult list = mockMvc.perform(get("/api/v1/cctv_cameras").cookie(session))
                .andExpect(status().isOk())
                .andReturn();

        var content = objectMapper.readTree(list.getResponse().getContentAsString()).path("content");
        assertThat(content.size()).isGreaterThan(0);
        boolean anyForeign = StreamSupport.stream(content.spliterator(), false)
                .anyMatch(item -> item.path("kindergartenId").asLong() != OWN_KG);
        assertThat(anyForeign).isFalse();
    }

    @Test
    void listCameraStreamsWithoutKindergartenIdParamReturnsOwnTenantDataOnly() throws Exception {
        Cookie session = login();

        MvcResult list = mockMvc.perform(get("/api/v1/camera_streams").cookie(session))
                .andExpect(status().isOk())
                .andReturn();

        var content = objectMapper.readTree(list.getResponse().getContentAsString()).path("content");
        assertThat(content.size()).isGreaterThan(0);
        boolean anyForeign = StreamSupport.stream(content.spliterator(), false)
                .anyMatch(item -> item.path("kindergartenId").asLong() != OWN_KG);
        assertThat(anyForeign).isFalse();
    }

    @Test
    void listCctvCamerasWithOwnKindergartenIdParamStillWorks() throws Exception {
        Cookie session = login();

        mockMvc.perform(get("/api/v1/cctv_cameras")
                        .param("kindergartenId", String.valueOf(OWN_KG))
                        .cookie(session))
                .andExpect(status().isOk());
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

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder withRealCsrf(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request
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
