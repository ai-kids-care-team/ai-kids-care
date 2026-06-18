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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ADR-0026 Phase 1：摄像头流密码 AES-256-GCM 加密集成测试。
 *
 * 验收：
 * - KINDERGARTEN_ADMIN 创建带密码的流 → 201，响应含 hasPassword=true，
 *   不含 streamPassword/sourceUrl/streamUser/ciphertext/iv/keyVersion；
 *   DB 中密文非明文、IV 非空、keyVersion=v1；往返解密还原明文。
 * - 创建无密码的流 → 201，hasPassword=false，DB ciphertext IS NULL。
 * - KINDERGARTEN_ADMIN 更新密码 → DB ciphertext 已更新；往返解密正确。
 * - TEACHER 角色 POST → 403（粗粒度门拒绝）。
 * - 未认证 POST → 401。
 *
 * Phone 前缀：{@code 010-0915-*}（未被其他测试类占用）。
 */
@AutoConfigureMockMvc
class CameraStreamEncryptionIntegrationTest extends BaseIntegrationTest {

    private static final String PASSWORD = "CsEnc@Test2026";
    private static final long OWN_KG = 1L;
    // 使用 kindergarten_id=1 的现有 seed camera（camera_id=1），避免插入 FK 链
    private static final long SEED_CAMERA_ID = 1L;

    private static final String ADMIN_LOGIN = "cs-admin";
    private static final String TEACHER_LOGIN = "cs-teacher";

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private ObjectMapper objectMapper;

    // dev 测试密钥（从 application-test.yml 注入，非生产）
    @Value("${camera-stream.crypto.keys.v1}")
    private String devKey;

    // 测试过程中创建的 camera_streams id，@AfterEach 清理
    private final List<Long> createdStreamIds = new ArrayList<>();

    @BeforeEach
    void setUpActors() {
        // phone 前缀 010-0915-*，保证全局唯一（见 test-conventions.md）
        upsertUser(ADMIN_LOGIN, "010-0915-0001");
        setKindergartenAdminIdentity(ADMIN_LOGIN, OWN_KG);

        upsertUser(TEACHER_LOGIN, "010-0915-0002");
        setTeacherIdentity(TEACHER_LOGIN, OWN_KG);
    }

    @AfterEach
    void cleanUp() {
        for (Long streamId : createdStreamIds) {
            jdbc.update("DELETE FROM camera_streams WHERE stream_id = ?", streamId);
        }
        createdStreamIds.clear();
    }

    // ── 创建：带密码 → 201，hasPassword=true，DB 密文非明文 ─────────────────────────

    @Test
    void createCameraStream_kindergartenAdmin_returns201WithEncryptedPassword() throws Exception {
        Cookie session = login(ADMIN_LOGIN);
        String body = objectMapper.writeValueAsString(Map.of(
                "cameraId", SEED_CAMERA_ID,
                "streamPassword", "TestPassword123",
                "streamType", "MAIN",
                "enabled", true
        ));

        MvcResult result = mockMvc.perform(withRealCsrf(
                        post("/api/v1/camera_streams")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body)
                                .cookie(session)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.hasPassword").value(true))
                // 敏感字段不得出现在响应中
                .andExpect(jsonPath("$.streamPassword").doesNotExist())
                .andExpect(jsonPath("$.sourceUrl").doesNotExist())
                .andExpect(jsonPath("$.streamUser").doesNotExist())
                .andExpect(jsonPath("$.streamPasswordCiphertext").doesNotExist())
                .andExpect(jsonPath("$.streamPasswordIv").doesNotExist())
                .andExpect(jsonPath("$.streamPasswordKeyVersion").doesNotExist())
                .andReturn();

        long streamId = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("streamId").asLong();
        createdStreamIds.add(streamId);

        // 验证 DB：密文不是明文，IV 不为空，keyVersion=v1
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT stream_password_ciphertext, stream_password_iv, stream_password_key_version "
                        + "FROM camera_streams WHERE stream_id = ?", streamId);
        String ciphertext = (String) row.get("stream_password_ciphertext");
        String iv = (String) row.get("stream_password_iv");
        String keyVersion = (String) row.get("stream_password_key_version");

        assertThat(ciphertext).isNotNull().isNotEqualTo("TestPassword123");
        assertThat(iv).isNotNull().isNotBlank();
        assertThat(keyVersion).isEqualTo("v1");

        // 往返解密验证
        String decrypted = AesGcmCryptoUtil.decrypt(ciphertext, iv, devKey);
        assertThat(decrypted).isEqualTo("TestPassword123");
    }

    // ── 创建：无密码 → 201，hasPassword=false，DB ciphertext IS NULL ─────────────────

    @Test
    void createCameraStream_withoutPassword_returns201WithHasPasswordFalse() throws Exception {
        Cookie session = login(ADMIN_LOGIN);
        String body = objectMapper.writeValueAsString(Map.of(
                "cameraId", SEED_CAMERA_ID,
                "streamType", "SUB",
                "enabled", false
        ));

        MvcResult result = mockMvc.perform(withRealCsrf(
                        post("/api/v1/camera_streams")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body)
                                .cookie(session)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.hasPassword").value(false))
                .andReturn();

        long streamId = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("streamId").asLong();
        createdStreamIds.add(streamId);

        // 验证 DB：ciphertext IS NULL
        String ciphertext = jdbc.queryForObject(
                "SELECT stream_password_ciphertext FROM camera_streams WHERE stream_id = ?",
                String.class, streamId);
        assertThat(ciphertext).isNull();
    }

    // ── 更新：更新密码 → DB 密文已替换，往返解密正确 ────────────────────────────────

    @Test
    void updateCameraStream_kindergartenAdmin_updatesEncryptedPassword() throws Exception {
        Cookie session = login(ADMIN_LOGIN);

        // 先创建无密码 stream
        String createBody = objectMapper.writeValueAsString(Map.of(
                "cameraId", SEED_CAMERA_ID,
                "enabled", true
        ));
        MvcResult createResult = mockMvc.perform(withRealCsrf(
                        post("/api/v1/camera_streams")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(createBody)
                                .cookie(session)))
                .andExpect(status().isCreated())
                .andReturn();

        long streamId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .get("streamId").asLong();
        createdStreamIds.add(streamId);

        // 再 PUT 带 streamPassword
        String updateBody = objectMapper.writeValueAsString(Map.of(
                "streamPassword", "UpdatedPassword456"
        ));
        // need fresh CSRF for PUT
        mockMvc.perform(withRealCsrf(
                        put("/api/v1/camera_streams/{id}", streamId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(updateBody)
                                .cookie(session)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasPassword").value(true));

        // 验证 DB：密文已更新
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT stream_password_ciphertext, stream_password_iv, stream_password_key_version "
                        + "FROM camera_streams WHERE stream_id = ?", streamId);
        String ciphertext = (String) row.get("stream_password_ciphertext");
        String iv = (String) row.get("stream_password_iv");

        assertThat(ciphertext).isNotNull().isNotEqualTo("UpdatedPassword456");
        assertThat(iv).isNotNull().isNotBlank();

        // 往返解密验证
        String decrypted = AesGcmCryptoUtil.decrypt(ciphertext, iv, devKey);
        assertThat(decrypted).isEqualTo("UpdatedPassword456");
    }

    // ── TEACHER → 403（粗粒度门拒绝）────────────────────────────────────────────────

    @Test
    void createCameraStream_teacher_returns403() throws Exception {
        Cookie session = login(TEACHER_LOGIN);
        String body = objectMapper.writeValueAsString(Map.of(
                "cameraId", SEED_CAMERA_ID,
                "enabled", true
        ));

        mockMvc.perform(withRealCsrf(
                        post("/api/v1/camera_streams")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body)
                                .cookie(session)))
                .andExpect(status().isForbidden());
    }

    // ── 未认证 → 401 ─────────────────────────────────────────────────────────────────

    @Test
    void getCameraStreams_unauthenticated_returns401() throws Exception {
        // GET 无需 CSRF，直接测试未认证 → 401
        mockMvc.perform(get("/api/v1/camera_streams")
                        .param("kindergartenId", "1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createCameraStream_unauthenticated_returns401() throws Exception {
        // 未认证 POST 写端点（ADR-0026 Phase 1 安全边界）→ 401。
        // 携带有效 CSRF token 但无 session cookie：CSRF 先过，再到匿名身份 → @PreAuthorize 拒 → 401。
        // （不带 CSRF 会被 CSRF filter 先拒为 403，测不到鉴权边界。）
        mockMvc.perform(withRealCsrf(post("/api/v1/camera_streams"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cameraId\":1}"))
                .andExpect(status().isUnauthorized());
    }

    // ── Helpers ────────────────────────────────────────────────────────────────────

    private void upsertUser(String loginId, String phone) {
        String hash = passwordEncoder.encode(PASSWORD);
        jdbc.update("""
                INSERT INTO users (login_id, email, phone, password_hash, status, created_at, updated_at)
                VALUES (?, ?, ?, ?, 'ACTIVE', NOW(), NOW())
                ON CONFLICT (login_id) DO UPDATE
                    SET password_hash = EXCLUDED.password_hash, status = 'ACTIVE'
                """, loginId, loginId + "@cs-test.internal", phone, hash);
    }

    private void setKindergartenAdminIdentity(String loginId, long kindergartenId) {
        clearRoleAndMembership(loginId);
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

    private void setTeacherIdentity(String loginId, long kindergartenId) {
        clearRoleAndMembership(loginId);
        jdbc.update("""
                INSERT INTO user_role_assignments (user_id, role, scope_type, scope_id, status, granted_at)
                SELECT user_id, 'TEACHER'::user_role_enum, 'KINDERGARTEN', ?, 'ACTIVE', NOW()
                FROM users WHERE login_id = ?
                """, kindergartenId, loginId);
        jdbc.update("""
                INSERT INTO user_kindergarten_memberships
                    (user_id, kindergarten_id, status, joined_at, created_at, updated_at)
                SELECT user_id, ?, 'ACTIVE', NOW(), NOW(), NOW()
                FROM users WHERE login_id = ?
                ON CONFLICT (user_id, kindergarten_id) DO UPDATE SET status = 'ACTIVE'
                """, kindergartenId, loginId);
        // DELETE + INSERT (not upsert) to avoid rrn_hash / staff_no unique constraint conflicts
        jdbc.update("""
                DELETE FROM teachers
                WHERE user_id = (SELECT user_id FROM users WHERE login_id = ?)
                """, loginId);
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 6);
        jdbc.update("""
                INSERT INTO teachers
                    (kindergarten_id, user_id, staff_no, name, gender, rrn_hash, rrn_first6,
                     level, start_date, status, created_at, updated_at)
                SELECT ?, user_id, ?, ?, 'MALE', ?, '000101', 'TEACHER'::level_enum,
                       '2025-03-01', 'ACTIVE', NOW(), NOW()
                FROM users WHERE login_id = ?
                """, kindergartenId, "CS-STAFF-" + suffix, "Teacher " + loginId,
                "CS-FIXTURE-HASH-" + suffix, loginId);
    }

    private void clearRoleAndMembership(String loginId) {
        jdbc.update("DELETE FROM user_kindergarten_memberships WHERE user_id = "
                + "(SELECT user_id FROM users WHERE login_id = ?)", loginId);
        jdbc.update("DELETE FROM user_role_assignments WHERE user_id = "
                + "(SELECT user_id FROM users WHERE login_id = ?)", loginId);
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
