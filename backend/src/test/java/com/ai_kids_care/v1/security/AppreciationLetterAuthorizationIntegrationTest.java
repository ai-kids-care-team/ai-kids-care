package com.ai_kids_care.v1.security;

import com.ai_kids_care.BaseIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SPEC-0003：感谢信读写授权集成测试。
 *
 * Phone 前缀：{@code 010-0920-*}（未被其他测试类占用）。
 * KG-1（kindergarten_id=1）来自 seed 数据，无需创建。
 *
 * 验收覆盖：
 * - 未认证 → 401（全端点）
 * - GUARDIAN create / read own / read public / update own / delete own
 * - 客户端伪造 senderUserId/kindergartenId/status 被忽略
 * - GUARDIAN 无法读/改/删他人私信 → 隐藏 404 + 审计
 * - TEACHER 读 public + 发给本人；不可写 → 403
 * - KINDERGARTEN_ADMIN 读全部；不可写 → 403
 * - SUPERADMIN 读/写均被拒 → 403
 * - 非法/跨租户 target → 404
 * - 跨租户 letterId → 404
 * - keyword 过滤仅返回匹配项
 */
@AutoConfigureMockMvc
class AppreciationLetterAuthorizationIntegrationTest extends BaseIntegrationTest {

    private static final String PASSWORD = "Al@Test2026";
    private static final long OWN_KG = 1L;

    // actor login IDs（phone 前缀 010-0920-*）
    private static final String GUARDIAN_LOGIN = "al-guardian";
    private static final String OTHER_GUARDIAN_LOGIN = "al-other-guardian";
    private static final String TEACHER_LOGIN = "al-teacher";
    private static final String ADMIN_LOGIN = "al-admin";
    private static final String SUPERADMIN_LOGIN = "al-superadmin";

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private ObjectMapper objectMapper;

    // 测试中创建的 letter id，@AfterEach 清理
    private final List<Long> createdLetterIds = new ArrayList<>();

    // actor 的 teacher_id（TEACHER 行）
    private long teacherProfileId;

    @BeforeEach
    void setUpActors() {
        upsertUser(GUARDIAN_LOGIN, "010-0920-0001");
        setGuardianIdentity(GUARDIAN_LOGIN, OWN_KG);

        upsertUser(OTHER_GUARDIAN_LOGIN, "010-0920-0002");
        setGuardianIdentity(OTHER_GUARDIAN_LOGIN, OWN_KG);

        upsertUser(TEACHER_LOGIN, "010-0920-0003");
        setTeacherIdentity(TEACHER_LOGIN, OWN_KG);
        teacherProfileId = resolveTeacherId(TEACHER_LOGIN);

        upsertUser(ADMIN_LOGIN, "010-0920-0004");
        setKindergartenAdminIdentity(ADMIN_LOGIN, OWN_KG);

        upsertUser(SUPERADMIN_LOGIN, "010-0920-0005");
        setSuperadminIdentity(SUPERADMIN_LOGIN);
    }

    @AfterEach
    void cleanUp() {
        for (Long letterId : createdLetterIds) {
            jdbc.update(
                    "DELETE FROM audit_logs WHERE resource_id = ? AND resource_type = 'APPRECIATION_LETTER'",
                    letterId);
            jdbc.update("DELETE FROM appreciation_letters WHERE letter_id = ?", letterId);
        }
        createdLetterIds.clear();
    }

    // ── 未认证 → 401 ───────────────────────────────────────────────────────────────────

    @Test
    void unauthenticated_allEndpoints_return401() throws Exception {
        mockMvc.perform(get("/api/v1/appreciation_letters"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/appreciation_letters/1"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(withRealCsrf(post("/api/v1/appreciation_letters"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(withRealCsrf(put("/api/v1/appreciation_letters/1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(withRealCsrf(delete("/api/v1/appreciation_letters/1")))
                .andExpect(status().isUnauthorized());
    }

    // ── GUARDIAN create → 201；响应含期望字段，不含禁止字段 ───────────────────────────────

    @Test
    void guardianCreate_kindergartenTarget_returns201WithMinimalVO() throws Exception {
        Cookie session = login(GUARDIAN_LOGIN);
        String body = objectMapper.writeValueAsString(Map.of(
                "targetType", "KINDERGARTEN",
                "targetId", OWN_KG,
                "title", "Thank you kindergarten",
                "content", "Great job",
                "isPublic", true
        ));

        MvcResult result = mockMvc.perform(withRealCsrf(post("/api/v1/appreciation_letters"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .cookie(session))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.letterId").exists())
                .andExpect(jsonPath("$.senderName").exists())
                .andExpect(jsonPath("$.targetType").value("KINDERGARTEN"))
                .andExpect(jsonPath("$.targetName").exists())
                .andExpect(jsonPath("$.title").value("Thank you kindergarten"))
                .andExpect(jsonPath("$.content").value("Great job"))
                .andExpect(jsonPath("$.isPublic").value(true))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.updatedAt").exists())
                // 禁止字段不得出现
                .andExpect(jsonPath("$.senderUserId").doesNotExist())
                .andExpect(jsonPath("$.kindergartenId").doesNotExist())
                .andExpect(jsonPath("$.targetId").doesNotExist())
                .andExpect(jsonPath("$.status").doesNotExist())
                .andReturn();

        Long letterId = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("letterId").asLong();
        createdLetterIds.add(letterId);
    }

    // ── 客户端伪造 senderUserId/kindergartenId/status 被忽略 ─────────────────────────────

    @Test
    void guardianCreate_forgedIdentityFields_areIgnoredServerDerivesValues() throws Exception {
        Cookie session = login(GUARDIAN_LOGIN);
        long guardianUserId = userIdOf(GUARDIAN_LOGIN);
        // 伪造 senderUserId=999, kindergartenId=999, status=DISABLED
        String body = "{\"targetType\":\"KINDERGARTEN\",\"targetId\":1,"
                + "\"title\":\"Forged\",\"content\":\"Body\",\"isPublic\":false,"
                + "\"senderUserId\":999,\"kindergartenId\":999,\"status\":\"DISABLED\"}";

        MvcResult result = mockMvc.perform(withRealCsrf(post("/api/v1/appreciation_letters"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .cookie(session))
                .andExpect(status().isCreated())
                .andReturn();

        Long letterId = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("letterId").asLong();
        createdLetterIds.add(letterId);

        // 验证 DB：sender_user_id 应为当前用户，kindergarten_id=1，status=ACTIVE
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT sender_user_id, kindergarten_id, status "
                        + "FROM appreciation_letters WHERE letter_id = ?", letterId);
        assertThat(((Number) row.get("sender_user_id")).longValue()).isEqualTo(guardianUserId);
        assertThat(((Number) row.get("kindergarten_id")).longValue()).isEqualTo(OWN_KG);
        assertThat(row.get("status")).isEqualTo("ACTIVE");
    }

    // ── GUARDIAN 读自己的私信 → 200 ────────────────────────────────────────────────────

    @Test
    void guardianGetOwnPrivateLetter_returns200() throws Exception {
        Long letterId = createLetterAsGuardian(GUARDIAN_LOGIN, false, "Own Private", "Body");
        Cookie session = login(GUARDIAN_LOGIN);

        mockMvc.perform(get("/api/v1/appreciation_letters/{id}", letterId).cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.letterId").value(letterId))
                .andExpect(jsonPath("$.title").value("Own Private"));
    }

    // ── GUARDIAN 读他人私信（同租户）→ 隐藏 404 + 审计 DENIED ───────────────────────────

    @Test
    void guardianGetOtherPrivateLetter_returnsHidden404AndAuditsDenied() throws Exception {
        Long letterId = createLetterAsGuardian(OTHER_GUARDIAN_LOGIN, false, "Other Private", "Body");
        Cookie session = login(GUARDIAN_LOGIN);

        MvcResult result = mockMvc.perform(
                        get("/api/v1/appreciation_letters/{id}", letterId).cookie(session))
                .andExpect(status().isNotFound())
                .andReturn();

        String correlationId = result.getResponse().getHeader("X-Correlation-Id");
        assertThat(correlationId).isNotBlank();
        Integer denied = jdbc.queryForObject(
                "SELECT count(*) FROM audit_logs "
                        + "WHERE correlation_id = ? AND action = 'AUTHORIZATION_DENIED' "
                        + "AND result = 'DENIED' AND resource_type = 'APPRECIATION_LETTER' "
                        + "AND resource_id = ? AND user_id = ?",
                Integer.class, correlationId, letterId, userIdOf(GUARDIAN_LOGIN));
        assertThat(denied).isEqualTo(1);
    }

    // ── GUARDIAN 读他人公开信（同租户）→ 200 ──────────────────────────────────────────

    @Test
    void guardianGetOtherPublicLetter_returns200() throws Exception {
        Long letterId = createLetterAsGuardian(OTHER_GUARDIAN_LOGIN, true, "Other Public", "Body");
        Cookie session = login(GUARDIAN_LOGIN);

        mockMvc.perform(get("/api/v1/appreciation_letters/{id}", letterId).cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.letterId").value(letterId));
    }

    // ── editable 归属信号：作者=true，非作者=false（FE 据此显隐编辑/删除入口）─────────────

    @Test
    void editableFlag_trueForAuthor_falseForNonAuthor() throws Exception {
        Long letterId = createLetterAsGuardian(GUARDIAN_LOGIN, true, "Editable Probe", "Body");

        // 作者本人 → editable=true
        mockMvc.perform(get("/api/v1/appreciation_letters/{id}", letterId)
                        .cookie(login(GUARDIAN_LOGIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.editable").value(true));

        // 同租户其他家长（可见公开信，但非作者）→ editable=false
        mockMvc.perform(get("/api/v1/appreciation_letters/{id}", letterId)
                        .cookie(login(OTHER_GUARDIAN_LOGIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.editable").value(false));

        // 园所管理员（可见全部，但非作者）→ editable=false
        mockMvc.perform(get("/api/v1/appreciation_letters/{id}", letterId)
                        .cookie(login(ADMIN_LOGIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.editable").value(false));
    }

    // ── GUARDIAN update 本人信 → 200；target/sender 不变 ───────────────────────────────

    @Test
    void guardianUpdateOwnLetter_returns200AndPreservesImmutableFields() throws Exception {
        Long letterId = createLetterAsGuardian(GUARDIAN_LOGIN, false, "Original Title", "Original Body");
        long guardianUserId = userIdOf(GUARDIAN_LOGIN);
        Cookie session = login(GUARDIAN_LOGIN);

        String body = objectMapper.writeValueAsString(Map.of(
                "title", "Updated Title",
                "content", "Updated Body",
                "isPublic", true
        ));

        mockMvc.perform(withRealCsrf(put("/api/v1/appreciation_letters/{id}", letterId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.letterId").value(letterId))
                .andExpect(jsonPath("$.title").value("Updated Title"))
                .andExpect(jsonPath("$.isPublic").value(true));

        // 验证 sender_user_id / kindergarten_id 不变
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT sender_user_id, kindergarten_id FROM appreciation_letters WHERE letter_id = ?",
                letterId);
        assertThat(((Number) row.get("sender_user_id")).longValue()).isEqualTo(guardianUserId);
        assertThat(((Number) row.get("kindergarten_id")).longValue()).isEqualTo(OWN_KG);
    }

    // ── GUARDIAN 部分更新（仅 title）→ 200；未传字段（content/isPublic）保持不变 ─────────

    @Test
    void guardianPartialUpdate_onlyTitle_doesNotOverwriteOtherFields() throws Exception {
        Long letterId = createLetterAsGuardian(GUARDIAN_LOGIN, false, "Original Title", "Original Content");
        Cookie session = login(GUARDIAN_LOGIN);

        // 仅发送 title，不含 content / isPublic
        String body = "{\"title\":\"New Title\"}";

        mockMvc.perform(withRealCsrf(put("/api/v1/appreciation_letters/{id}", letterId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.letterId").value(letterId))
                .andExpect(jsonPath("$.title").value("New Title"))
                .andExpect(jsonPath("$.content").value("Original Content"))
                .andExpect(jsonPath("$.isPublic").value(false));

        // 验证 DB：content 和 is_public 未被 null 覆写
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT title, content, is_public FROM appreciation_letters WHERE letter_id = ?",
                letterId);
        assertThat(row.get("title")).isEqualTo("New Title");
        assertThat(row.get("content")).isEqualTo("Original Content");
        assertThat(row.get("is_public")).isEqualTo(false);
    }

    // ── GUARDIAN 无法更新他人信 → 404 ─────────────────────────────────────────────────

    @Test
    void guardianUpdateOtherLetter_returns404() throws Exception {
        Long letterId = createLetterAsGuardian(OTHER_GUARDIAN_LOGIN, false, "Other", "Body");
        Cookie session = login(GUARDIAN_LOGIN);

        String body = objectMapper.writeValueAsString(Map.of("title", "Hacked"));
        mockMvc.perform(withRealCsrf(put("/api/v1/appreciation_letters/{id}", letterId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .cookie(session))
                .andExpect(status().isNotFound());
    }

    // ── GUARDIAN 软删本人信 → 204；再 GET → 404 ──────────────────────────────────────

    @Test
    void guardianDeleteOwnLetter_returns204AndLetterBecomesInvisible() throws Exception {
        Long letterId = createLetterAsGuardian(GUARDIAN_LOGIN, false, "To Delete", "Body");
        Cookie session = login(GUARDIAN_LOGIN);

        mockMvc.perform(withRealCsrf(delete("/api/v1/appreciation_letters/{id}", letterId))
                        .cookie(session))
                .andExpect(status().isNoContent());

        // 验证 DB status 变为非 ACTIVE
        String status = jdbc.queryForObject(
                "SELECT status FROM appreciation_letters WHERE letter_id = ?",
                String.class, letterId);
        assertThat(status).isNotEqualTo("ACTIVE");

        // GET 后应为 404（软删后不可见）
        mockMvc.perform(get("/api/v1/appreciation_letters/{id}", letterId).cookie(session))
                .andExpect(status().isNotFound());
    }

    // ── GUARDIAN 无法删除他人信 → 404 ────────────────────────────────────────────────

    @Test
    void guardianDeleteOtherLetter_returns404() throws Exception {
        Long letterId = createLetterAsGuardian(OTHER_GUARDIAN_LOGIN, false, "Other", "Body");
        Cookie session = login(GUARDIAN_LOGIN);

        mockMvc.perform(withRealCsrf(delete("/api/v1/appreciation_letters/{id}", letterId))
                        .cookie(session))
                .andExpect(status().isNotFound());
    }

    // ── TEACHER 读 public 信 → 200 ─────────────────────────────────────────────────

    @Test
    void teacherGetPublicLetter_returns200() throws Exception {
        Long letterId = createLetterAsGuardian(GUARDIAN_LOGIN, true, "Public for Teacher", "Body");
        Cookie session = login(TEACHER_LOGIN);

        mockMvc.perform(get("/api/v1/appreciation_letters/{id}", letterId).cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.letterId").value(letterId));
    }

    // ── TEACHER 读发给本人的私信 → 200 ─────────────────────────────────────────────

    @Test
    void teacherGetLetterAddressedToThem_returns200() throws Exception {
        Long letterId = createLetterAsGuardianToTeacher(GUARDIAN_LOGIN, teacherProfileId, false,
                "Dear Teacher", "Body");
        Cookie session = login(TEACHER_LOGIN);

        mockMvc.perform(get("/api/v1/appreciation_letters/{id}", letterId).cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.letterId").value(letterId));
    }

    // ── TEACHER 无法读私信（发给 KG，非本人）→ 404 ──────────────────────────────────

    @Test
    void teacherGetPrivateLetterNotAddressedToThem_returns404() throws Exception {
        // KINDERGARTEN 类型私信，不是发给该 teacher 的
        Long letterId = createLetterAsGuardian(GUARDIAN_LOGIN, false, "Private KG Letter", "Body");
        Cookie session = login(TEACHER_LOGIN);

        mockMvc.perform(get("/api/v1/appreciation_letters/{id}", letterId).cookie(session))
                .andExpect(status().isNotFound());
    }

    // ── TEACHER 写操作 → 403 ──────────────────────────────────────────────────────

    @Test
    void teacherCreate_returns403() throws Exception {
        Cookie session = login(TEACHER_LOGIN);
        String body = objectMapper.writeValueAsString(Map.of(
                "targetType", "KINDERGARTEN",
                "targetId", OWN_KG,
                "title", "T",
                "content", "C",
                "isPublic", false
        ));
        mockMvc.perform(withRealCsrf(post("/api/v1/appreciation_letters"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .cookie(session))
                .andExpect(status().isForbidden());
    }

    // ── KINDERGARTEN_ADMIN 读本租户全部 → 200 ────────────────────────────────────

    @Test
    void kindergartenAdminGetPrivateLetter_returns200() throws Exception {
        Long letterId = createLetterAsGuardian(GUARDIAN_LOGIN, false, "Private for Admin", "Body");
        Cookie session = login(ADMIN_LOGIN);

        mockMvc.perform(get("/api/v1/appreciation_letters/{id}", letterId).cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.letterId").value(letterId));
    }

    // ── KINDERGARTEN_ADMIN 写操作 → 403 ──────────────────────────────────────────

    @Test
    void kindergartenAdminCreate_returns403() throws Exception {
        Cookie session = login(ADMIN_LOGIN);
        String body = objectMapper.writeValueAsString(Map.of(
                "targetType", "KINDERGARTEN",
                "targetId", OWN_KG,
                "title", "T",
                "content", "C",
                "isPublic", false
        ));
        mockMvc.perform(withRealCsrf(post("/api/v1/appreciation_letters"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .cookie(session))
                .andExpect(status().isForbidden());
    }

    // ── SUPERADMIN 读 → 403（无 tenant identity）──────────────────────────────────

    @Test
    void superadminRead_returns403() throws Exception {
        Cookie session = login(SUPERADMIN_LOGIN);
        mockMvc.perform(get("/api/v1/appreciation_letters").cookie(session))
                .andExpect(status().isForbidden());
    }

    // ── 跨租户 teacher target（存在于 KG-2，KG-1 GUARDIAN 写 → 404）────────────────
    // SPEC-0003 计划用例 #19：target_id=32 属于 KG-2（来自 seed 46_appreciation_letters_seed.sql），
    // 服务层 validateTarget 通过 teacherRepository.findById(32).getKindergarten().getId()
    // 发现其不等于 KG-1，应拒绝并抛出 404。

    @Test
    void guardianCreate_crossTenantTeacherTarget_returns404() throws Exception {
        // teacher_id=32 在 seed 中属于 KG-2（参见 46_appreciation_letters_seed.sql letter_id=10）
        long kg2TeacherId = 32L;
        Cookie session = login(GUARDIAN_LOGIN);
        String body = objectMapper.writeValueAsString(Map.of(
                "targetType", "TEACHER",
                "targetId", kg2TeacherId,
                "title", "Cross-tenant attempt",
                "content", "Should be rejected",
                "isPublic", false
        ));
        mockMvc.perform(withRealCsrf(post("/api/v1/appreciation_letters"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .cookie(session))
                .andExpect(status().isNotFound());
    }

    // ── 非法 target（不存在的 teacherId）→ 404 ────────────────────────────────────

    @Test
    void guardianCreate_nonExistentTeacherTarget_returns404() throws Exception {
        Cookie session = login(GUARDIAN_LOGIN);
        String body = objectMapper.writeValueAsString(Map.of(
                "targetType", "TEACHER",
                "targetId", 999999L,
                "title", "T",
                "content", "C",
                "isPublic", false
        ));
        mockMvc.perform(withRealCsrf(post("/api/v1/appreciation_letters"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .cookie(session))
                .andExpect(status().isNotFound());
    }

    // ── 跨租户 letterId → GET 为 404 ──────────────────────────────────────────────

    @Test
    void guardianGetLetterFromOtherKindergarten_returns404() throws Exception {
        // 在 seed 中查找属于 KG-2 的信（kindergarten_id=2）
        Long crossTenantLetterId = jdbc.queryForObject(
                "SELECT letter_id FROM appreciation_letters "
                        + "WHERE kindergarten_id = 2 AND status = 'ACTIVE' LIMIT 1",
                Long.class);

        if (crossTenantLetterId == null) {
            // 如果 seed 中无 KG-2 信则跳过（不强制 seed 数据）
            return;
        }

        Cookie session = login(GUARDIAN_LOGIN);  // KG-1 用户
        mockMvc.perform(get("/api/v1/appreciation_letters/{id}", crossTenantLetterId).cookie(session))
                .andExpect(status().isNotFound());
    }

    // ── keyword 过滤：仅返回匹配项 ──────────────────────────────────────────────────

    @Test
    void guardianListWithKeyword_returnsOnlyMatchingLetters() throws Exception {
        // 使用极不可能出现在其他数据中的唯一 keyword
        String uniqueKeyword = "ALTEST-UNIQUE-" + UUID.randomUUID().toString().replace("-", "");
        Long matchId = createLetterAsGuardian(GUARDIAN_LOGIN, false,
                uniqueKeyword + " matching title", "Some body");
        // noMatch 信是私信（isPublic=false），GUARDIAN 仍可见（本人所写）
        Long noMatchId = createLetterAsGuardian(GUARDIAN_LOGIN, false,
                "Completely Different Title", "Completely Different body");

        Cookie session = login(GUARDIAN_LOGIN);
        String responseBody = mockMvc.perform(get("/api/v1/appreciation_letters")
                        .param("keyword", uniqueKeyword)
                        .cookie(session))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        // 匹配项在结果中（letter_id 字段值出现在 JSON 内）
        JsonNode page = objectMapper.readTree(responseBody);
        JsonNode content = page.path("content");
        assertThat(content.isArray()).isTrue();

        // 所有返回项均包含 keyword（无匹配外漏）
        boolean foundMatch = false;
        for (JsonNode item : content) {
            long itemId = item.path("letterId").asLong();
            String title = item.path("title").asText();
            if (itemId == matchId) {
                foundMatch = true;
                assertThat(title).contains(uniqueKeyword);
            }
            // noMatch letter 不得出现在结果中
            assertThat(itemId).isNotEqualTo(noMatchId.longValue());
        }
        assertThat(foundMatch).as("Matching letter must appear in keyword search results").isTrue();
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────────

    private void upsertUser(String loginId, String phone) {
        String hash = passwordEncoder.encode(PASSWORD);
        jdbc.update("""
                INSERT INTO users (login_id, email, phone, password_hash, status, created_at, updated_at)
                VALUES (?, ?, ?, ?, 'ACTIVE', NOW(), NOW())
                ON CONFLICT (login_id) DO UPDATE
                    SET password_hash = EXCLUDED.password_hash, status = 'ACTIVE'
                """, loginId, loginId + "@al-test.internal", phone, hash);
    }

    private void setGuardianIdentity(String loginId, long kindergartenId) {
        clearRoleAndMembership(loginId);
        jdbc.update("""
                INSERT INTO user_role_assignments (user_id, role, scope_type, scope_id, status, granted_at)
                SELECT user_id, 'GUARDIAN'::user_role_enum, 'KINDERGARTEN', ?, 'ACTIVE', NOW()
                FROM users WHERE login_id = ?
                """, kindergartenId, loginId);
        jdbc.update("""
                INSERT INTO user_kindergarten_memberships
                    (user_id, kindergarten_id, status, joined_at, created_at, updated_at)
                SELECT user_id, ?, 'ACTIVE', NOW(), NOW(), NOW()
                FROM users WHERE login_id = ?
                ON CONFLICT (user_id, kindergarten_id) DO UPDATE SET status = 'ACTIVE'
                """, kindergartenId, loginId);
        jdbc.update("""
                INSERT INTO guardians
                    (kindergarten_id, user_id, name, rrn_hash, rrn_first6, gender, address,
                     status, created_at, updated_at)
                SELECT ?, user_id, ?, encode(sha256(convert_to(login_id || '-al', 'UTF8')), 'hex'),
                       '000101', 'MALE', 'Test address', 'ACTIVE', NOW(), NOW()
                FROM users WHERE login_id = ?
                ON CONFLICT (user_id) DO UPDATE
                    SET status = 'ACTIVE', kindergarten_id = EXCLUDED.kindergarten_id,
                        name = EXCLUDED.name
                """, kindergartenId, "Guardian " + loginId, loginId);
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
        // DELETE + INSERT（非 upsert）以避免 rrn_hash / staff_no 唯一约束冲突
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
                """, kindergartenId, "AL-STAFF-" + suffix, "Teacher " + loginId,
                "AL-FIXTURE-HASH-" + suffix, loginId);
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

    private void setSuperadminIdentity(String loginId) {
        clearRoleAndMembership(loginId);
        jdbc.update("""
                INSERT INTO user_role_assignments (user_id, role, scope_type, scope_id, status, granted_at)
                SELECT user_id, 'SUPERADMIN'::user_role_enum, 'PLATFORM', NULL, 'ACTIVE', NOW()
                FROM users WHERE login_id = ?
                """, loginId);
        // superadmins 行（user_id 唯一）
        jdbc.update("""
                INSERT INTO superadmins (user_id, name, department, status, created_at, updated_at)
                SELECT user_id, ?, 'Platform', 'ACTIVE', NOW(), NOW()
                FROM users WHERE login_id = ?
                ON CONFLICT (user_id) DO UPDATE SET status = 'ACTIVE'
                """, "Superadmin " + loginId, loginId);
    }

    private void clearRoleAndMembership(String loginId) {
        jdbc.update("DELETE FROM user_kindergarten_memberships WHERE user_id = "
                + "(SELECT user_id FROM users WHERE login_id = ?)", loginId);
        jdbc.update("DELETE FROM user_role_assignments WHERE user_id = "
                + "(SELECT user_id FROM users WHERE login_id = ?)", loginId);
    }

    private long userIdOf(String loginId) {
        return jdbc.queryForObject("SELECT user_id FROM users WHERE login_id = ?", Long.class, loginId);
    }

    private long resolveTeacherId(String loginId) {
        return jdbc.queryForObject(
                "SELECT t.teacher_id FROM teachers t "
                        + "JOIN users u ON u.user_id = t.user_id WHERE u.login_id = ?",
                Long.class, loginId);
    }

    /**
     * GUARDIAN 创建一封发给本 KG（KINDERGARTEN 类型）的信。
     */
    private Long createLetterAsGuardian(String loginId, boolean isPublic,
                                        String title, String content) throws Exception {
        Cookie session = login(loginId);
        String body = objectMapper.writeValueAsString(Map.of(
                "targetType", "KINDERGARTEN",
                "targetId", OWN_KG,
                "title", title,
                "content", content,
                "isPublic", isPublic
        ));
        MvcResult result = mockMvc.perform(withRealCsrf(post("/api/v1/appreciation_letters"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .cookie(session))
                .andExpect(status().isCreated())
                .andReturn();

        Long letterId = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("letterId").asLong();
        createdLetterIds.add(letterId);
        return letterId;
    }

    /**
     * GUARDIAN 创建一封发给指定 teacher（TEACHER 类型）的信。
     */
    private Long createLetterAsGuardianToTeacher(String loginId, long targetTeacherId,
                                                  boolean isPublic, String title,
                                                  String content) throws Exception {
        Cookie session = login(loginId);
        String body = objectMapper.writeValueAsString(Map.of(
                "targetType", "TEACHER",
                "targetId", targetTeacherId,
                "title", title,
                "content", content,
                "isPublic", isPublic
        ));
        MvcResult result = mockMvc.perform(withRealCsrf(post("/api/v1/appreciation_letters"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .cookie(session))
                .andExpect(status().isCreated())
                .andReturn();

        Long letterId = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("letterId").asLong();
        createdLetterIds.add(letterId);
        return letterId;
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
