package com.ai_kids_care.v1.security;

import com.ai_kids_care.BaseIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ADR-0026 Phase 2：内部凭据接口 {@code GET /api/v1/internal/streams/{id}/credentials} 集成测试。
 *
 * 验收：
 * - 有效 Bearer token → 200，返回解密后的 streamPassword / sourceUrl / streamUser。
 * - 缺失 token → 401；错误 token → 401。
 * - 普通会话用户（KINDERGARTEN_ADMIN）无 Bearer → 403（SESSION_AUTHENTICATED ≠ ROLE_AI_SERVICE）。
 * - 无密码的流 → 200，streamPassword 为 null。
 * - 不存在的流 → 404。
 * - 端点 {@code @Hidden}：不出现在 /v3/api-docs。
 *
 * Phone 前缀：{@code 010-0916-*}（未被其他测试类占用）。
 */
@AutoConfigureMockMvc
class StreamCredentialEndpointIntegrationTest extends BaseIntegrationTest {

    private static final String PASSWORD = "CredEp@Test2026";
    private static final long OWN_KG = 1L;
    private static final long SEED_CAMERA_ID = 1L;
    private static final String ADMIN_LOGIN = "cred-admin";
    private static final String CRED_PATH = "/api/v1/internal/streams/{id}/credentials";

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private ObjectMapper objectMapper;

    @Value("${internal.ai.service-token}")
    private String serviceToken;

    private final List<Long> createdStreamIds = new ArrayList<>();

    @BeforeEach
    void setUpActor() {
        upsertUser(ADMIN_LOGIN, "010-0916-0001");
        setKindergartenAdminIdentity(ADMIN_LOGIN, OWN_KG);
    }

    @AfterEach
    void cleanUp() {
        for (Long streamId : createdStreamIds) {
            jdbc.update("DELETE FROM camera_streams WHERE stream_id = ?", streamId);
        }
        createdStreamIds.clear();
    }

    // ── 有效 Bearer → 200，解密凭据 ───────────────────────────────────────────────────

    @Test
    void getCredentials_validBearer_returnsDecryptedCredentials() throws Exception {
        long streamId = createStreamWithCredentials("rtsp://cam.example/stream1", "camuser", "SecretPw123");

        mockMvc.perform(get(CRED_PATH, streamId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + serviceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.streamId").value(streamId))
                .andExpect(jsonPath("$.sourceUrl").value("rtsp://cam.example/stream1"))
                .andExpect(jsonPath("$.streamUser").value("camuser"))
                .andExpect(jsonPath("$.streamPassword").value("SecretPw123"));
    }

    // ── 缺失 token → 401 ─────────────────────────────────────────────────────────────

    @Test
    void getCredentials_missingToken_returns401() throws Exception {
        long streamId = createStreamWithCredentials("rtsp://cam.example/s", "u", "Pw");

        mockMvc.perform(get(CRED_PATH, streamId))
                .andExpect(status().isUnauthorized());
    }

    // ── 错误 token → 401 ─────────────────────────────────────────────────────────────

    @Test
    void getCredentials_wrongToken_returns401() throws Exception {
        long streamId = createStreamWithCredentials("rtsp://cam.example/s", "u", "Pw");

        mockMvc.perform(get(CRED_PATH, streamId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer wrong-token-value"))
                .andExpect(status().isUnauthorized());
    }

    // ── 普通会话用户无 Bearer → 403 ──────────────────────────────────────────────────

    @Test
    void getCredentials_sessionUserWithoutBearer_returns403() throws Exception {
        long streamId = createStreamWithCredentials("rtsp://cam.example/s", "u", "Pw");
        Cookie session = login(ADMIN_LOGIN);

        mockMvc.perform(get(CRED_PATH, streamId).cookie(session))
                .andExpect(status().isForbidden());
    }

    // ── 无密码的流 → 200，streamPassword=null ────────────────────────────────────────

    @Test
    void getCredentials_streamWithoutPassword_returnsNullPassword() throws Exception {
        Cookie session = login(ADMIN_LOGIN);
        String body = objectMapper.writeValueAsString(Map.of(
                "cameraId", SEED_CAMERA_ID,
                "sourceUrl", "rtsp://cam.example/nopw",
                "enabled", true
        ));
        MvcResult create = mockMvc.perform(withRealCsrf(
                        post("/api/v1/camera_streams")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body)
                                .cookie(session)))
                .andExpect(status().isCreated())
                .andReturn();
        long streamId = extractStreamId(create);
        createdStreamIds.add(streamId);

        mockMvc.perform(get(CRED_PATH, streamId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + serviceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sourceUrl").value("rtsp://cam.example/nopw"))
                .andExpect(jsonPath("$.streamPassword").doesNotExist());
    }

    // ── 不存在的流 → 404 ─────────────────────────────────────────────────────────────

    @Test
    void getCredentials_nonexistentStream_returns404() throws Exception {
        mockMvc.perform(get(CRED_PATH, 999_999_999L)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + serviceToken))
                .andExpect(status().isNotFound());
    }

    // ── @Hidden：内部端点不出现在 /v3/api-docs ───────────────────────────────────────

    @Test
    void internalCredentialEndpointIsHiddenFromOpenApi() throws Exception {
        MvcResult result = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode apiDocs = objectMapper.readTree(result.getResponse().getContentAsString());

        JsonNode paths = apiDocs.path("paths");
        paths.fieldNames().forEachRemaining(path ->
                assertThat(path)
                        .as("内部凭据接口不得发布到 OpenAPI")
                        .doesNotContain("/internal/"));

        // StreamCredentialDTO（含 streamPassword/sourceUrl/streamUser）不得作为已发布 schema 出现。
        assertThat(apiDocs.path("components").path("schemas").has("StreamCredentialDTO"))
                .as("StreamCredentialDTO 不得出现在已发布 schema")
                .isFalse();
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────────

    private long createStreamWithCredentials(String sourceUrl, String streamUser, String password) throws Exception {
        Cookie session = login(ADMIN_LOGIN);
        String body = objectMapper.writeValueAsString(Map.of(
                "cameraId", SEED_CAMERA_ID,
                "sourceUrl", sourceUrl,
                "streamUser", streamUser,
                "streamPassword", password,
                "streamType", "MAIN",
                "enabled", true
        ));
        MvcResult create = mockMvc.perform(withRealCsrf(
                        post("/api/v1/camera_streams")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body)
                                .cookie(session)))
                .andExpect(status().isCreated())
                .andReturn();
        long streamId = extractStreamId(create);
        createdStreamIds.add(streamId);
        return streamId;
    }

    private long extractStreamId(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("streamId").asLong();
    }

    private void upsertUser(String loginId, String phone) {
        String hash = passwordEncoder.encode(PASSWORD);
        jdbc.update("""
                INSERT INTO users (login_id, email, phone, password_hash, status, created_at, updated_at)
                VALUES (?, ?, ?, ?, 'ACTIVE', NOW(), NOW())
                ON CONFLICT (login_id) DO UPDATE
                    SET password_hash = EXCLUDED.password_hash, status = 'ACTIVE'
                """, loginId, loginId + "@cred-test.internal", phone, hash);
    }

    private void setKindergartenAdminIdentity(String loginId, long kindergartenId) {
        jdbc.update("DELETE FROM user_kindergarten_memberships WHERE user_id = "
                + "(SELECT user_id FROM users WHERE login_id = ?)", loginId);
        jdbc.update("DELETE FROM user_role_assignments WHERE user_id = "
                + "(SELECT user_id FROM users WHERE login_id = ?)", loginId);
        jdbc.update("""
                INSERT INTO user_role_assignments (user_id, role, scope_type, scope_id, status, granted_at)
                SELECT user_id, 'KINDERGARTEN_ADMIN'::user_role_enum, 'KINDERGARTEN', ?, 'ACTIVE', NOW()
                FROM users WHERE login_id = ?
                """, kindergartenId, loginId);
        jdbc.update("""
                INSERT INTO user_kindergarten_memberships
                    (user_id, kindergarten_id, status, joined_at, created_at, updated_at)
                SELECT user_id, ?, 'ACTIVE', NOW(), NOW(), NOW()
                FROM users WHERE login_id = ?
                ON CONFLICT (user_id, kindergarten_id) DO UPDATE SET status = 'ACTIVE'
                """, kindergartenId, loginId);
    }

    private Cookie login(String loginId) throws Exception {
        MvcResult result = mockMvc.perform(withRealCsrf(post("/api/v1/auth/login"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("identifier", loginId, "password", PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("AI_KIDS_CARE_SESSION"))
                .andReturn();
        return result.getResponse().getCookie("AI_KIDS_CARE_SESSION");
    }

    private MockHttpServletRequestBuilder withRealCsrf(MockHttpServletRequestBuilder request) throws Exception {
        MvcResult csrf = mockMvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode csrfJson = objectMapper.readTree(csrf.getResponse().getContentAsString());
        String token = csrfJson.get("token").asText();
        return request
                .cookie(csrf.getResponse().getCookie("XSRF-TOKEN"))
                .header("X-XSRF-TOKEN", token);
    }
}
