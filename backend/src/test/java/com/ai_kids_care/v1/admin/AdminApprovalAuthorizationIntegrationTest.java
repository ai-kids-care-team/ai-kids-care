package com.ai_kids_care.v1.admin;

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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * SPEC-0002 Slice A: 园级审批授权集成测试。
 *
 * 测试隔离策略（参考 AuthEndpointTest / TenantIsolationIntegrationTest）：
 * - 每个角色用**唯一 LOGIN_ID**（前缀+UUID 后缀）。
 * - @BeforeEach 先 DELETE 该用户的所有 role/membership，再插 1 条，确保至多 1 ACTIVE（ADR-0021 约束）。
 * - "目标申请"用单独的唯一用户（独立创建 PENDING graph）。
 * - 幼儿园使用 seed 中已存在的 kindergarten_id=1（确保稳定存在），跨园测试插入新的 kindergarten。
 *
 * 验收覆盖（SPEC-0002 §验收标准）：
 * - director / vice_director 可批准 PENDING → ACTIVE，被批准账号可登录
 * - 普通 TEACHER → 403；非 director level 的 KINDERGARTEN_ADMIN → 403
 * - 他园 admin（跨园目标）→ 隐藏 404
 * - 未认证 → 401
 * - 自审 → 403
 * - reject → REJECTED，无法登录
 * - disable → DISABLED + 提交后 session 失效（下一请求 401）
 * - 跨园目标 → 隐藏 404
 * - 并发/重复批准不破坏单-ACTIVE
 */
@AutoConfigureMockMvc
class AdminApprovalAuthorizationIntegrationTest extends BaseIntegrationTest {

    // ── 常量 ─────────────────────────────────────────────────────────────────

    private static final String PASSWORD = "Test@Admin2026";
    private static final long OWN_KG = 1L;

    // 各角色的唯一 LOGIN_ID（UUID 后缀在测试启动时固定，容器复用场景下 ON CONFLICT upsert）
    private static final String DIRECTOR_LOGIN = "admin-appr-director";
    private static final String VICE_LOGIN = "admin-appr-vice";
    private static final String TEACHER_LOGIN = "admin-appr-teacher";
    private static final String PLAIN_ADMIN_LOGIN = "admin-appr-plain";  // level=OTHER
    private static final String OTHER_KG_ADMIN_LOGIN = "admin-appr-other-kg";

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private ObjectMapper objectMapper;

    // ── Setup ─────────────────────────────────────────────────────────────────

    @BeforeEach
    void setUpActors() {
        // DIRECTOR（本园 kindergarten_id=1，level=DIRECTOR）
        upsertUser(DIRECTOR_LOGIN, "010-0001-0001");
        setKindergartenAdminRole(DIRECTOR_LOGIN, OWN_KG);
        ensureTeacherProfile(DIRECTOR_LOGIN, OWN_KG, "DIRECTOR");

        // VICE_DIRECTOR
        upsertUser(VICE_LOGIN, "010-0001-0002");
        setKindergartenAdminRole(VICE_LOGIN, OWN_KG);
        ensureTeacherProfile(VICE_LOGIN, OWN_KG, "VICE_DIRECTOR");

        // TEACHER（普通教师，无审批权）
        upsertUser(TEACHER_LOGIN, "010-0001-0003");
        setKindergartenRole("TEACHER", TEACHER_LOGIN, OWN_KG);
        ensureTeacherProfile(TEACHER_LOGIN, OWN_KG, "TEACHER");

        // KINDERGARTEN_ADMIN but level=OTHER（不满足 level 条件）
        upsertUser(PLAIN_ADMIN_LOGIN, "010-0001-0004");
        setKindergartenAdminRole(PLAIN_ADMIN_LOGIN, OWN_KG);
        ensureTeacherProfile(PLAIN_ADMIN_LOGIN, OWN_KG, "OTHER");

        // 他园 KINDERGARTEN_ADMIN（无法操作本园目标）
        long otherKg = insertActiveKindergarten();
        upsertUser(OTHER_KG_ADMIN_LOGIN, "010-0001-0005");
        setKindergartenAdminRole(OTHER_KG_ADMIN_LOGIN, otherKg);
        ensureTeacherProfile(OTHER_KG_ADMIN_LOGIN, otherKg, "DIRECTOR");

        // 确保 OTHER_KG_ADMIN 有 teacher 档案（使用另一个幼儿园 id）
        // 上面 ensureTeacherProfile 已做；此处无需重复。
    }

    // ── 1. 列出本园 PENDING 申请 ───────────────────────────────────────────

    @Test
    void listRegistrations_directorCanSeeOwnKindergarten() throws Exception {
        long pendingUserId = createPendingTeacherRegistration(OWN_KG);
        Cookie session = login(DIRECTOR_LOGIN);

        mockMvc.perform(get("/api/v1/admin/kindergarten/registrations?status=PENDING")
                        .cookie(session))
                .andExpect(status().isOk());

        // 清理
        deletePendingUser(pendingUserId);
    }

    @Test
    void listRegistrations_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/admin/kindergarten/registrations?status=PENDING"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listRegistrations_teacher_returns403() throws Exception {
        Cookie session = login(TEACHER_LOGIN);
        mockMvc.perform(get("/api/v1/admin/kindergarten/registrations?status=PENDING")
                        .cookie(session))
                .andExpect(status().isForbidden());
    }

    @Test
    void listRegistrations_adminWithOtherLevel_returns403() throws Exception {
        Cookie session = login(PLAIN_ADMIN_LOGIN);
        mockMvc.perform(get("/api/v1/admin/kindergarten/registrations?status=PENDING")
                        .cookie(session))
                .andExpect(status().isForbidden());
    }

    // ── 2. 批准 PENDING → ACTIVE ───────────────────────────────────────────

    @Test
    void approve_director_approvesPendingTeacher_allowsSubsequentLogin() throws Exception {
        long pendingUserId = createPendingTeacherRegistration(OWN_KG);
        String pendingLoginId = pendingLoginIdForUser(pendingUserId);

        Cookie directorSession = login(DIRECTOR_LOGIN);
        mockMvc.perform(withRealCsrf(post("/api/v1/admin/kindergarten/registrations/{userId}/approve", pendingUserId))
                        .cookie(directorSession))
                .andExpect(status().isNoContent());

        // 验证：user 变为 ACTIVE
        assertThat(statusOf("users", "user_id", pendingUserId)).isEqualTo("ACTIVE");
        assertThat(statusOf("user_role_assignments", "user_id", pendingUserId)).isEqualTo("ACTIVE");
        assertThat(statusOf("user_kindergarten_memberships", "user_id", pendingUserId)).isEqualTo("ACTIVE");

        // 被批准用户可登录
        mockMvc.perform(withRealCsrf(post("/api/v1/auth/login"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("identifier", pendingLoginId, "password", PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.effectiveRole").value("TEACHER"));

        // 清理
        deletePendingUser(pendingUserId);
    }

    @Test
    void approve_viceDirector_canApprovePendingRegistration() throws Exception {
        long pendingUserId = createPendingTeacherRegistration(OWN_KG);

        Cookie session = login(VICE_LOGIN);
        mockMvc.perform(withRealCsrf(post("/api/v1/admin/kindergarten/registrations/{userId}/approve", pendingUserId))
                        .cookie(session))
                .andExpect(status().isNoContent());

        assertThat(statusOf("users", "user_id", pendingUserId)).isEqualTo("ACTIVE");
        deletePendingUser(pendingUserId);
    }

    @Test
    void approve_teacher_returns403() throws Exception {
        long pendingUserId = createPendingTeacherRegistration(OWN_KG);
        Cookie session = login(TEACHER_LOGIN);
        mockMvc.perform(withRealCsrf(post("/api/v1/admin/kindergarten/registrations/{userId}/approve", pendingUserId))
                        .cookie(session))
                .andExpect(status().isForbidden());
        deletePendingUser(pendingUserId);
    }

    @Test
    void approve_adminWithOtherLevel_returns403() throws Exception {
        long pendingUserId = createPendingTeacherRegistration(OWN_KG);
        Cookie session = login(PLAIN_ADMIN_LOGIN);
        mockMvc.perform(withRealCsrf(post("/api/v1/admin/kindergarten/registrations/{userId}/approve", pendingUserId))
                        .cookie(session))
                .andExpect(status().isForbidden());
        deletePendingUser(pendingUserId);
    }

    @Test
    void approve_unauthenticated_returns401() throws Exception {
        long pendingUserId = createPendingTeacherRegistration(OWN_KG);
        mockMvc.perform(withRealCsrf(post("/api/v1/admin/kindergarten/registrations/{userId}/approve", pendingUserId)))
                .andExpect(status().isUnauthorized());
        deletePendingUser(pendingUserId);
    }

    @Test
    void approve_selfApproval_returns403() throws Exception {
        // DIRECTOR 尝试批准自己的申请（此场景下 DIRECTOR 已是 ACTIVE，故需构造同一 userId 的 PENDING 状态）
        // 实践中，director 无法批准自身 userId（自审检查）
        Cookie session = login(DIRECTOR_LOGIN);
        long directorUserId = userIdOf(DIRECTOR_LOGIN);

        mockMvc.perform(withRealCsrf(post("/api/v1/admin/kindergarten/registrations/{userId}/approve", directorUserId))
                        .cookie(session))
                .andExpect(status().isForbidden());
    }

    @Test
    void approve_crossTenantTarget_returnsHidden404() throws Exception {
        // 目标是另一个幼儿园的 PENDING 用户
        long foreignKg = insertActiveKindergarten();
        long pendingUserId = createPendingTeacherRegistration(foreignKg);

        Cookie session = login(DIRECTOR_LOGIN);  // scoped to OWN_KG=1
        mockMvc.perform(withRealCsrf(post("/api/v1/admin/kindergarten/registrations/{userId}/approve", pendingUserId))
                        .cookie(session))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Resource not found"));

        // 验证：目标仍为 PENDING（未被修改）
        assertThat(statusOf("users", "user_id", pendingUserId)).isEqualTo("PENDING");
        deletePendingUser(pendingUserId);
    }

    @Test
    void approve_otherKgAdmin_crossTenantAccess_hiddenNotFound() throws Exception {
        // 他园 admin 无法操作本园 PENDING
        long pendingUserId = createPendingTeacherRegistration(OWN_KG);

        Cookie session = login(OTHER_KG_ADMIN_LOGIN);
        mockMvc.perform(withRealCsrf(post("/api/v1/admin/kindergarten/registrations/{userId}/approve", pendingUserId))
                        .cookie(session))
                .andExpect(status().isNotFound());

        assertThat(statusOf("users", "user_id", pendingUserId)).isEqualTo("PENDING");
        deletePendingUser(pendingUserId);
    }

    @Test
    void approve_duplicateApprove_idempotentHidden404() throws Exception {
        // 重复批准（第一次成功 → ACTIVE；第二次条件更新 0 行 → 隐藏 404）
        long pendingUserId = createPendingTeacherRegistration(OWN_KG);
        Cookie session = login(DIRECTOR_LOGIN);

        mockMvc.perform(withRealCsrf(post("/api/v1/admin/kindergarten/registrations/{userId}/approve", pendingUserId))
                        .cookie(session))
                .andExpect(status().isNoContent());

        // 第二次
        mockMvc.perform(withRealCsrf(post("/api/v1/admin/kindergarten/registrations/{userId}/approve", pendingUserId))
                        .cookie(session))
                .andExpect(status().isNotFound());

        // 验证：仍只有 1 条 ACTIVE role（单-ACTIVE 约束）
        int activeRoles = jdbc.queryForObject(
                "SELECT count(*) FROM user_role_assignments WHERE user_id = ? AND status = 'ACTIVE'",
                Integer.class, pendingUserId);
        assertThat(activeRoles).isEqualTo(1);

        deletePendingUser(pendingUserId);
    }

    // ── 3. 拒绝 PENDING → REJECTED ────────────────────────────────────────

    @Test
    void reject_director_rejectsPendingRegistration_cannotLogin() throws Exception {
        long pendingUserId = createPendingTeacherRegistration(OWN_KG);
        String pendingLoginId = pendingLoginIdForUser(pendingUserId);

        Cookie session = login(DIRECTOR_LOGIN);
        mockMvc.perform(withRealCsrf(post("/api/v1/admin/kindergarten/registrations/{userId}/reject", pendingUserId))
                        .cookie(session))
                .andExpect(status().isNoContent());

        assertThat(statusOf("users", "user_id", pendingUserId)).isEqualTo("REJECTED");
        assertThat(statusOf("user_role_assignments", "user_id", pendingUserId)).isEqualTo("REJECTED");

        // 被拒绝用户无法登录
        mockMvc.perform(withRealCsrf(post("/api/v1/auth/login"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("identifier", pendingLoginId, "password", PASSWORD))))
                .andExpect(status().isUnauthorized());

        deletePendingUser(pendingUserId);
    }

    @Test
    void reject_teacher_returns403() throws Exception {
        long pendingUserId = createPendingTeacherRegistration(OWN_KG);
        Cookie session = login(TEACHER_LOGIN);
        mockMvc.perform(withRealCsrf(post("/api/v1/admin/kindergarten/registrations/{userId}/reject", pendingUserId))
                        .cookie(session))
                .andExpect(status().isForbidden());
        deletePendingUser(pendingUserId);
    }

    @Test
    void reject_unauthenticated_returns401() throws Exception {
        long pendingUserId = createPendingTeacherRegistration(OWN_KG);
        mockMvc.perform(withRealCsrf(post("/api/v1/admin/kindergarten/registrations/{userId}/reject", pendingUserId)))
                .andExpect(status().isUnauthorized());
        deletePendingUser(pendingUserId);
    }

    @Test
    void reject_selfRejection_returns403() throws Exception {
        Cookie session = login(DIRECTOR_LOGIN);
        long directorUserId = userIdOf(DIRECTOR_LOGIN);
        mockMvc.perform(withRealCsrf(post("/api/v1/admin/kindergarten/registrations/{userId}/reject", directorUserId))
                        .cookie(session))
                .andExpect(status().isForbidden());
    }

    @Test
    void reject_crossTenantTarget_returnsHidden404() throws Exception {
        long foreignKg = insertActiveKindergarten();
        long pendingUserId = createPendingTeacherRegistration(foreignKg);

        Cookie session = login(DIRECTOR_LOGIN);
        mockMvc.perform(withRealCsrf(post("/api/v1/admin/kindergarten/registrations/{userId}/reject", pendingUserId))
                        .cookie(session))
                .andExpect(status().isNotFound());

        assertThat(statusOf("users", "user_id", pendingUserId)).isEqualTo("PENDING");
        deletePendingUser(pendingUserId);
    }

    // ── 4. 停用 ACTIVE → DISABLED + 会话吊销 ──────────────────────────────

    @Test
    void disable_director_disablesActiveMember_sessionRevoked() throws Exception {
        // 先批准一个成员
        long targetUserId = createPendingTeacherRegistration(OWN_KG);
        approveAsDirector(targetUserId);
        String targetLoginId = pendingLoginIdForUser(targetUserId);

        // 目标用户登录获取 session
        Cookie targetSession = loginAsUser(targetLoginId, PASSWORD);
        // 验证目标 session 有效
        mockMvc.perform(get("/api/v1/auth/session").cookie(targetSession))
                .andExpect(status().isOk());

        // DIRECTOR 停用目标成员
        Cookie directorSession = login(DIRECTOR_LOGIN);
        mockMvc.perform(withRealCsrf(post("/api/v1/admin/kindergarten/members/{userId}/disable", targetUserId))
                        .cookie(directorSession))
                .andExpect(status().isNoContent());

        // 验证 DB 状态
        assertThat(statusOf("users", "user_id", targetUserId)).isEqualTo("DISABLED");
        assertThat(statusOf("user_role_assignments", "user_id", targetUserId)).isEqualTo("DISABLED");
        assertThat(statusOf("user_kindergarten_memberships", "user_id", targetUserId)).isEqualTo("DISABLED");

        // 验证：目标的旧 session 已失效（ADR-0019 §6 事务提交后吊销）
        mockMvc.perform(get("/api/v1/auth/session").cookie(targetSession))
                .andExpect(status().isUnauthorized());

        deletePendingUser(targetUserId);
    }

    @Test
    void disable_teacher_returns403() throws Exception {
        long targetUserId = createPendingTeacherRegistration(OWN_KG);
        approveAsDirector(targetUserId);

        Cookie session = login(TEACHER_LOGIN);
        mockMvc.perform(withRealCsrf(post("/api/v1/admin/kindergarten/members/{userId}/disable", targetUserId))
                        .cookie(session))
                .andExpect(status().isForbidden());

        deletePendingUser(targetUserId);
    }

    @Test
    void disable_unauthenticated_returns401() throws Exception {
        long targetUserId = createPendingTeacherRegistration(OWN_KG);
        approveAsDirector(targetUserId);

        mockMvc.perform(withRealCsrf(post("/api/v1/admin/kindergarten/members/{userId}/disable", targetUserId)))
                .andExpect(status().isUnauthorized());

        deletePendingUser(targetUserId);
    }

    @Test
    void disable_selfDisable_returns403() throws Exception {
        Cookie session = login(DIRECTOR_LOGIN);
        long directorUserId = userIdOf(DIRECTOR_LOGIN);
        mockMvc.perform(withRealCsrf(post("/api/v1/admin/kindergarten/members/{userId}/disable", directorUserId))
                        .cookie(session))
                .andExpect(status().isForbidden());
    }

    @Test
    void disable_crossTenantTarget_returnsHidden404() throws Exception {
        long foreignKg = insertActiveKindergarten();
        long targetUserId = createPendingTeacherRegistration(foreignKg);
        // 手动将外园用户激活（直接 SQL 更新，绕过本园 policy）
        jdbc.update("UPDATE users SET status = 'ACTIVE' WHERE user_id = ?", targetUserId);
        jdbc.update("UPDATE user_role_assignments SET status = 'ACTIVE' WHERE user_id = ?", targetUserId);
        jdbc.update("UPDATE user_kindergarten_memberships SET status = 'ACTIVE' WHERE user_id = ?", targetUserId);
        jdbc.update("UPDATE teachers SET status = 'ACTIVE' WHERE user_id = ?", targetUserId);

        Cookie session = login(DIRECTOR_LOGIN);
        mockMvc.perform(withRealCsrf(post("/api/v1/admin/kindergarten/members/{userId}/disable", targetUserId))
                        .cookie(session))
                .andExpect(status().isNotFound());

        // 验证目标未被修改
        assertThat(statusOf("users", "user_id", targetUserId)).isEqualTo("ACTIVE");
        deletePendingUser(targetUserId);
    }

    @Test
    void disable_duplicateDisable_idempotentHidden404() throws Exception {
        long targetUserId = createPendingTeacherRegistration(OWN_KG);
        approveAsDirector(targetUserId);

        Cookie session = login(DIRECTOR_LOGIN);
        mockMvc.perform(withRealCsrf(post("/api/v1/admin/kindergarten/members/{userId}/disable", targetUserId))
                        .cookie(session))
                .andExpect(status().isNoContent());

        // 重复停用 → 隐藏 404
        mockMvc.perform(withRealCsrf(post("/api/v1/admin/kindergarten/members/{userId}/disable", targetUserId))
                        .cookie(session))
                .andExpect(status().isNotFound());

        deletePendingUser(targetUserId);
    }

    // ── 5. 响应不含 S1 ────────────────────────────────────────────────────

    @Test
    void listRegistrations_responseDoesNotContainS1Fields() throws Exception {
        long pendingUserId = createPendingTeacherRegistration(OWN_KG);
        Cookie session = login(DIRECTOR_LOGIN);

        MvcResult result = mockMvc.perform(get("/api/v1/admin/kindergarten/registrations?status=PENDING")
                        .cookie(session))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        // S1 字段不得出现
        assertThat(body).doesNotContain("rrn");
        assertThat(body).doesNotContain("phone");
        assertThat(body).doesNotContain("email");
        assertThat(body).doesNotContain("address");
        assertThat(body).doesNotContain("password");

        deletePendingUser(pendingUserId);
    }

    // ── Helper: user/role setup ───────────────────────────────────────────

    private void upsertUser(String loginId, String phone) {
        String hash = passwordEncoder.encode(PASSWORD);
        jdbc.update("""
                INSERT INTO users (login_id, email, phone, password_hash, status, created_at, updated_at)
                VALUES (?, ?, ?, ?, 'ACTIVE', NOW(), NOW())
                ON CONFLICT (login_id) DO UPDATE
                    SET password_hash = EXCLUDED.password_hash, status = 'ACTIVE'
                """,
                loginId,
                loginId + "@admin-test.internal",
                phone,
                hash);
    }

    private void setKindergartenAdminRole(String loginId, long kindergartenId) {
        setKindergartenRole("KINDERGARTEN_ADMIN", loginId, kindergartenId);
    }

    private void setKindergartenRole(String role, String loginId, long kindergartenId) {
        jdbc.update("""
                DELETE FROM user_kindergarten_memberships
                WHERE user_id = (SELECT user_id FROM users WHERE login_id = ?)
                """, loginId);
        jdbc.update("""
                DELETE FROM user_role_assignments
                WHERE user_id = (SELECT user_id FROM users WHERE login_id = ?)
                """, loginId);
        jdbc.update("""
                INSERT INTO user_role_assignments
                    (user_id, role, scope_type, scope_id, status, granted_at)
                SELECT user_id, ?::user_role_enum, 'KINDERGARTEN', ?, 'ACTIVE', NOW()
                FROM users WHERE login_id = ?
                """, role, kindergartenId, loginId);
        jdbc.update("""
                INSERT INTO user_kindergarten_memberships
                    (user_id, kindergarten_id, status, joined_at, created_at, updated_at)
                SELECT user_id, ?, 'ACTIVE', NOW(), NOW(), NOW()
                FROM users WHERE login_id = ?
                """, kindergartenId, loginId);
    }

    private void ensureTeacherProfile(String loginId, long kindergartenId, String level) {
        // 先删旧档案，再插入（容器复用时保持幂等）
        jdbc.update("""
                DELETE FROM teachers
                WHERE user_id = (SELECT user_id FROM users WHERE login_id = ?)
                """, loginId);
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 6);
        jdbc.update("""
                INSERT INTO teachers
                    (kindergarten_id, user_id, staff_no, name, gender, rrn_encrypted, rrn_first6,
                     level, start_date, status, created_at, updated_at)
                SELECT ?, user_id, ?, ?, 'MALE', 'ENC', '000101', ?::level_enum,
                       '2025-03-01', 'ACTIVE', NOW(), NOW()
                FROM users WHERE login_id = ?
                """,
                kindergartenId,
                "STAFF-" + suffix,
                "Admin " + loginId,
                level,
                loginId);
    }

    // ── Helper: pending registration ──────────────────────────────────────

    /**
     * 创建一个本园的 PENDING TEACHER 申请（模拟 AuthService.register 的效果）。
     * 返回 target userId。
     */
    private long createPendingTeacherRegistration(long kindergartenId) {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String loginId = "pending-" + suffix;
        String phone = "020" + suffix.substring(0, 8);
        String hash = passwordEncoder.encode(PASSWORD);

        long userId = jdbc.queryForObject("""
                INSERT INTO users (login_id, email, phone, password_hash, status, created_at, updated_at)
                VALUES (?, ?, ?, ?, 'PENDING', NOW(), NOW())
                RETURNING user_id
                """,
                Long.class,
                loginId,
                loginId + "@pending-test.internal",
                phone,
                hash);

        jdbc.update("""
                INSERT INTO user_role_assignments
                    (user_id, role, scope_type, scope_id, status, granted_at)
                VALUES (?, 'TEACHER', 'KINDERGARTEN', ?, 'PENDING', NOW())
                """, userId, kindergartenId);

        jdbc.update("""
                INSERT INTO user_kindergarten_memberships
                    (user_id, kindergarten_id, status, joined_at, created_at, updated_at)
                VALUES (?, ?, 'PENDING', NOW(), NOW(), NOW())
                """, userId, kindergartenId);

        String staffSuffix = UUID.randomUUID().toString().replace("-", "").substring(0, 6);
        jdbc.update("""
                INSERT INTO teachers
                    (kindergarten_id, user_id, staff_no, name, gender, rrn_encrypted, rrn_first6,
                     level, start_date, status, created_at, updated_at)
                VALUES (?, ?, ?, 'Pending Teacher', 'MALE', 'ENC', '000101',
                        'TEACHER', '2026-03-01', 'PENDING', NOW(), NOW())
                """,
                kindergartenId, userId, "PSTAFF-" + staffSuffix);

        return userId;
    }

    private void deletePendingUser(long userId) {
        jdbc.update("DELETE FROM teachers WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM guardians WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM user_kindergarten_memberships WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM user_role_assignments WHERE user_id = ?", userId);
        // SPEC-0001 #1 安全审计：approve/disable 及目标登录会写 audit_logs（user_id FK → users）；
        // 先清该 user 相关审计行（actor 或 resource）再删 user，避免 FK 违例。
        jdbc.update("DELETE FROM audit_logs WHERE user_id = ? OR resource_id = ?", userId, userId);
        jdbc.update("DELETE FROM users WHERE user_id = ?", userId);
    }

    private void approveAsDirector(long targetUserId) throws Exception {
        Cookie session = login(DIRECTOR_LOGIN);
        mockMvc.perform(withRealCsrf(post("/api/v1/admin/kindergarten/registrations/{userId}/approve", targetUserId))
                        .cookie(session))
                .andExpect(status().isNoContent());
    }

    // ── Helper: login / id lookup ────────────────────────────────────────

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

    private Cookie loginAsUser(String loginId, String password) throws Exception {
        MvcResult result = mockMvc.perform(withRealCsrf(post("/api/v1/auth/login"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("identifier", loginId, "password", password))))
                .andExpect(status().isOk())
                .andReturn();
        return result.getResponse().getCookie("AI_KIDS_CARE_SESSION");
    }

    private long userIdOf(String loginId) {
        return jdbc.queryForObject(
                "SELECT user_id FROM users WHERE login_id = ?", Long.class, loginId);
    }

    private String pendingLoginIdForUser(long userId) {
        return jdbc.queryForObject(
                "SELECT login_id FROM users WHERE user_id = ?", String.class, userId);
    }

    private String statusOf(String table, String idCol, long id) {
        return jdbc.queryForObject(
                "SELECT status::text FROM " + table + " WHERE " + idCol + " = ?",
                String.class, id);
    }

    private long insertActiveKindergarten() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        return jdbc.queryForObject("""
                INSERT INTO kindergartens
                    (name, address, region_code, code, business_registration_no,
                     contact_name, contact_phone, contact_email, status, created_at, updated_at)
                VALUES (?, ?, 'TEST', ?, ?, 'Contact', '01000000000', ?, 'ACTIVE', NOW(), NOW())
                RETURNING kindergarten_id
                """,
                Long.class,
                "Test Kindergarten " + suffix,
                "Test address",
                "TCODE-" + suffix,
                "TBRN-" + suffix,
                "tkg-" + suffix + "@test.internal");
    }

    private MockHttpServletRequestBuilder withRealCsrf(MockHttpServletRequestBuilder request) throws Exception {
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
