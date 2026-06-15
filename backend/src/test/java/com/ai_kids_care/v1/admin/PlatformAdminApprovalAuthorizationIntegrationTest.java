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
 * SPEC-0002 Slice B: 平台级审批授权集成测试。
 *
 * 测试隔离策略（镜像 AdminApprovalAuthorizationIntegrationTest）：
 * - 每个角色用唯一 LOGIN_ID（常量前缀，容器复用场景下 ON CONFLICT upsert）。
 * - @BeforeEach 先 DELETE 该用户的所有 role/membership，再插 1 条，确保至多 1 ACTIVE（ADR-0021 约束）。
 * - "目标申请"用独立唯一用户（UUID 后缀），造 PENDING SUPERADMIN 全图：
 *     user(PENDING) + superadmins(PENDING) + user_role_assignments(PENDING, PLATFORM, scope_id=NULL, role=SUPERADMIN)
 * - 平台账户无 membership，无 user_kindergarten_memberships 行。
 *
 * 验收覆盖（SPEC-0002 §验收标准平台级部分）：
 * - PLATFORM_IT_ADMIN 可批准 PENDING SUPERADMIN → ACTIVE，被批准账号可登录
 * - PLATFORM_IT_ADMIN 可拒绝 PENDING SUPERADMIN → REJECTED，无法登录
 * - 非平台角色（KINDERGARTEN_ADMIN / TEACHER）→ 403
 * - 未认证 → 401
 * - 自审 → 403
 * - 非 SUPERADMIN 目标（PLATFORM_IT_ADMIN 的 userId）→ 隐藏 404
 * - disable → DISABLED + 旧 session 401
 * - 重复批准幂等隐藏 404
 * - 列表无 S1
 */
@AutoConfigureMockMvc
class PlatformAdminApprovalAuthorizationIntegrationTest extends BaseIntegrationTest {

    // ── 常量 ─────────────────────────────────────────────────────────────────

    private static final String PASSWORD = "Test@Platform2026";

    // 各角色的唯一 LOGIN_ID（容器复用时通过 ON CONFLICT upsert 保持幂等）
    private static final String PLATFORM_ADMIN_LOGIN = "plt-appr-it-admin";
    private static final String KG_ADMIN_LOGIN      = "plt-appr-kg-admin";
    private static final String TEACHER_LOGIN        = "plt-appr-teacher";

    // 用于自审测试的 PLATFORM_IT_ADMIN（同一账户）
    // 其 userId 作为 targetUserId，期望 403
    // 注意：PLATFORM_IT_ADMIN 的 role != SUPERADMIN，条件更新会 0 行 → 隐藏 404，
    // 但 platformPolicy.requireNotSelf 先触发 403（禁自审优先于 role 门）
    // ── 以同一 PLATFORM_ADMIN_LOGIN 账号的 userId 做自审测试即可

    private static final long OWN_KG = 1L;  // 园级测试用，确保该 kindergarten 稳定存在

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private ObjectMapper objectMapper;

    // ── Setup ─────────────────────────────────────────────────────────────────

    @BeforeEach
    void setUpActors() {
        // PLATFORM_IT_ADMIN
        upsertUser(PLATFORM_ADMIN_LOGIN, "030-0001-0001");
        setPlatformRole(PLATFORM_ADMIN_LOGIN, "PLATFORM_IT_ADMIN");

        // KINDERGARTEN_ADMIN（无平台权限，期望 403）
        upsertUser(KG_ADMIN_LOGIN, "030-0001-0002");
        setKindergartenAdminRole(KG_ADMIN_LOGIN, OWN_KG);
        ensureTeacherProfile(KG_ADMIN_LOGIN, OWN_KG, "DIRECTOR");

        // TEACHER（无平台权限，期望 403）
        upsertUser(TEACHER_LOGIN, "030-0001-0003");
        setKindergartenRole("TEACHER", TEACHER_LOGIN, OWN_KG);
        ensureTeacherProfile(TEACHER_LOGIN, OWN_KG, "TEACHER");
    }

    // ── 1. 列出 PENDING SUPERADMIN 申请 ──────────────────────────────────────

    @Test
    void listRegistrations_platformAdmin_canSeePendingSuperadmins() throws Exception {
        long pendingUserId = createPendingSuperadminRegistration();
        Cookie session = login(PLATFORM_ADMIN_LOGIN);

        mockMvc.perform(get("/api/v1/admin/platform/superadmin-registrations?status=PENDING")
                        .cookie(session))
                .andExpect(status().isOk());

        deletePendingUser(pendingUserId);
    }

    @Test
    void listRegistrations_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/admin/platform/superadmin-registrations?status=PENDING"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listRegistrations_teacher_returns403() throws Exception {
        Cookie session = login(TEACHER_LOGIN);
        mockMvc.perform(get("/api/v1/admin/platform/superadmin-registrations?status=PENDING")
                        .cookie(session))
                .andExpect(status().isForbidden());
    }

    @Test
    void listRegistrations_kindergartenAdmin_returns403() throws Exception {
        Cookie session = login(KG_ADMIN_LOGIN);
        mockMvc.perform(get("/api/v1/admin/platform/superadmin-registrations?status=PENDING")
                        .cookie(session))
                .andExpect(status().isForbidden());
    }

    // ── 2. 批准 PENDING SUPERADMIN → ACTIVE ──────────────────────────────────

    @Test
    void approve_platformAdmin_approvesPendingSuperadmin_allowsSubsequentLogin() throws Exception {
        long pendingUserId = createPendingSuperadminRegistration();
        String pendingLoginId = loginIdOf(pendingUserId);

        Cookie session = login(PLATFORM_ADMIN_LOGIN);
        mockMvc.perform(withRealCsrf(post("/api/v1/admin/platform/superadmin-registrations/{userId}/approve", pendingUserId))
                        .cookie(session))
                .andExpect(status().isNoContent());

        // 验证：user 变为 ACTIVE
        assertThat(statusOf("users", "user_id", pendingUserId)).isEqualTo("ACTIVE");
        assertThat(statusOf("user_role_assignments", "user_id", pendingUserId)).isEqualTo("ACTIVE");
        assertThat(statusOf("superadmins", "user_id", pendingUserId)).isEqualTo("ACTIVE");

        // 验证：无 membership 行（平台账户无园级 membership）
        int memberCount = jdbc.queryForObject(
                "SELECT count(*) FROM user_kindergarten_memberships WHERE user_id = ?",
                Integer.class, pendingUserId);
        assertThat(memberCount).isZero();

        // 被批准用户可登录
        mockMvc.perform(withRealCsrf(post("/api/v1/auth/login"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("identifier", pendingLoginId, "password", PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.effectiveRole").value("SUPERADMIN"));

        deletePendingUser(pendingUserId);
    }

    @Test
    void approve_teacher_returns403() throws Exception {
        long pendingUserId = createPendingSuperadminRegistration();
        Cookie session = login(TEACHER_LOGIN);
        mockMvc.perform(withRealCsrf(post("/api/v1/admin/platform/superadmin-registrations/{userId}/approve", pendingUserId))
                        .cookie(session))
                .andExpect(status().isForbidden());
        deletePendingUser(pendingUserId);
    }

    @Test
    void approve_kindergartenAdmin_returns403() throws Exception {
        long pendingUserId = createPendingSuperadminRegistration();
        Cookie session = login(KG_ADMIN_LOGIN);
        mockMvc.perform(withRealCsrf(post("/api/v1/admin/platform/superadmin-registrations/{userId}/approve", pendingUserId))
                        .cookie(session))
                .andExpect(status().isForbidden());
        deletePendingUser(pendingUserId);
    }

    @Test
    void approve_unauthenticated_returns401() throws Exception {
        long pendingUserId = createPendingSuperadminRegistration();
        mockMvc.perform(withRealCsrf(post("/api/v1/admin/platform/superadmin-registrations/{userId}/approve", pendingUserId)))
                .andExpect(status().isUnauthorized());
        deletePendingUser(pendingUserId);
    }

    @Test
    void approve_selfApproval_returns403() throws Exception {
        // PLATFORM_IT_ADMIN 尝试批准自身 userId → platformPolicy.requireNotSelf 抛 403
        Cookie session = login(PLATFORM_ADMIN_LOGIN);
        long ownUserId = userIdOf(PLATFORM_ADMIN_LOGIN);

        mockMvc.perform(withRealCsrf(post("/api/v1/admin/platform/superadmin-registrations/{userId}/approve", ownUserId))
                        .cookie(session))
                .andExpect(status().isForbidden());
    }

    @Test
    void approve_targetIsNotSuperadmin_returnsHidden404() throws Exception {
        // 目标是 PLATFORM_IT_ADMIN（role != SUPERADMIN）→ platformConditionalUpdateStatus 0 行 → 隐藏 404
        // 使用另一个 PLATFORM_IT_ADMIN 用户作为目标
        String targetLogin = "plt-appr-target-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        upsertUser(targetLogin, "030-0002-0001");
        setPlatformRole(targetLogin, "PLATFORM_IT_ADMIN");
        long targetUserId = userIdOf(targetLogin);

        Cookie session = login(PLATFORM_ADMIN_LOGIN);
        mockMvc.perform(withRealCsrf(post("/api/v1/admin/platform/superadmin-registrations/{userId}/approve", targetUserId))
                        .cookie(session))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Resource not found"));

        // 目标状态未被修改（仍为 ACTIVE，因为我们 setPlatformRole 插了 ACTIVE 的 role）
        assertThat(statusOf("users", "user_id", targetUserId)).isEqualTo("ACTIVE");

        // 清理目标用户
        deleteUser(targetUserId);
    }

    @Test
    void approve_duplicateApprove_idempotentHidden404() throws Exception {
        long pendingUserId = createPendingSuperadminRegistration();
        Cookie session = login(PLATFORM_ADMIN_LOGIN);

        // 第一次批准 → 204
        mockMvc.perform(withRealCsrf(post("/api/v1/admin/platform/superadmin-registrations/{userId}/approve", pendingUserId))
                        .cookie(session))
                .andExpect(status().isNoContent());

        // 第二次批准 → 隐藏 404（条件更新 0 行）
        mockMvc.perform(withRealCsrf(post("/api/v1/admin/platform/superadmin-registrations/{userId}/approve", pendingUserId))
                        .cookie(session))
                .andExpect(status().isNotFound());

        // 验证：仍只有 1 条 ACTIVE role（单-ACTIVE 约束）
        int activeRoles = jdbc.queryForObject(
                "SELECT count(*) FROM user_role_assignments WHERE user_id = ? AND status = 'ACTIVE'",
                Integer.class, pendingUserId);
        assertThat(activeRoles).isEqualTo(1);

        deletePendingUser(pendingUserId);
    }

    // ── 3. 拒绝 PENDING SUPERADMIN → REJECTED ────────────────────────────────

    @Test
    void reject_platformAdmin_rejectsPendingSuperadmin_cannotLogin() throws Exception {
        long pendingUserId = createPendingSuperadminRegistration();
        String pendingLoginId = loginIdOf(pendingUserId);

        Cookie session = login(PLATFORM_ADMIN_LOGIN);
        mockMvc.perform(withRealCsrf(post("/api/v1/admin/platform/superadmin-registrations/{userId}/reject", pendingUserId))
                        .cookie(session))
                .andExpect(status().isNoContent());

        assertThat(statusOf("users", "user_id", pendingUserId)).isEqualTo("REJECTED");
        assertThat(statusOf("user_role_assignments", "user_id", pendingUserId)).isEqualTo("REJECTED");
        assertThat(statusOf("superadmins", "user_id", pendingUserId)).isEqualTo("REJECTED");

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
        long pendingUserId = createPendingSuperadminRegistration();
        Cookie session = login(TEACHER_LOGIN);
        mockMvc.perform(withRealCsrf(post("/api/v1/admin/platform/superadmin-registrations/{userId}/reject", pendingUserId))
                        .cookie(session))
                .andExpect(status().isForbidden());
        deletePendingUser(pendingUserId);
    }

    @Test
    void reject_unauthenticated_returns401() throws Exception {
        long pendingUserId = createPendingSuperadminRegistration();
        mockMvc.perform(withRealCsrf(post("/api/v1/admin/platform/superadmin-registrations/{userId}/reject", pendingUserId)))
                .andExpect(status().isUnauthorized());
        deletePendingUser(pendingUserId);
    }

    @Test
    void reject_selfRejection_returns403() throws Exception {
        Cookie session = login(PLATFORM_ADMIN_LOGIN);
        long ownUserId = userIdOf(PLATFORM_ADMIN_LOGIN);
        mockMvc.perform(withRealCsrf(post("/api/v1/admin/platform/superadmin-registrations/{userId}/reject", ownUserId))
                        .cookie(session))
                .andExpect(status().isForbidden());
    }

    // ── 4. 停用 ACTIVE → DISABLED + 会话吊销 ─────────────────────────────────

    @Test
    void disable_platformAdmin_disablesActiveSuperadmin_sessionRevoked() throws Exception {
        // 先批准一个 SUPERADMIN
        long targetUserId = createPendingSuperadminRegistration();
        approveAsItAdmin(targetUserId);
        String targetLoginId = loginIdOf(targetUserId);

        // 目标用户登录获取 session
        Cookie targetSession = loginAsUser(targetLoginId, PASSWORD);
        // 验证目标 session 有效
        mockMvc.perform(get("/api/v1/auth/session").cookie(targetSession))
                .andExpect(status().isOk());

        // PLATFORM_IT_ADMIN 停用目标
        Cookie itAdminSession = login(PLATFORM_ADMIN_LOGIN);
        mockMvc.perform(withRealCsrf(post("/api/v1/admin/platform/users/{userId}/disable", targetUserId))
                        .cookie(itAdminSession))
                .andExpect(status().isNoContent());

        // 验证 DB 状态
        assertThat(statusOf("users", "user_id", targetUserId)).isEqualTo("DISABLED");
        assertThat(statusOf("user_role_assignments", "user_id", targetUserId)).isEqualTo("DISABLED");
        assertThat(statusOf("superadmins", "user_id", targetUserId)).isEqualTo("DISABLED");

        // 验证：目标旧 session 已失效（ADR-0019 §6 事务提交后吊销）
        mockMvc.perform(get("/api/v1/auth/session").cookie(targetSession))
                .andExpect(status().isUnauthorized());

        deletePendingUser(targetUserId);
    }

    @Test
    void disable_teacher_returns403() throws Exception {
        long targetUserId = createPendingSuperadminRegistration();
        approveAsItAdmin(targetUserId);

        Cookie session = login(TEACHER_LOGIN);
        mockMvc.perform(withRealCsrf(post("/api/v1/admin/platform/users/{userId}/disable", targetUserId))
                        .cookie(session))
                .andExpect(status().isForbidden());

        deletePendingUser(targetUserId);
    }

    @Test
    void disable_unauthenticated_returns401() throws Exception {
        long targetUserId = createPendingSuperadminRegistration();
        approveAsItAdmin(targetUserId);

        mockMvc.perform(withRealCsrf(post("/api/v1/admin/platform/users/{userId}/disable", targetUserId)))
                .andExpect(status().isUnauthorized());

        deletePendingUser(targetUserId);
    }

    @Test
    void disable_selfDisable_returns403() throws Exception {
        Cookie session = login(PLATFORM_ADMIN_LOGIN);
        long ownUserId = userIdOf(PLATFORM_ADMIN_LOGIN);
        mockMvc.perform(withRealCsrf(post("/api/v1/admin/platform/users/{userId}/disable", ownUserId))
                        .cookie(session))
                .andExpect(status().isForbidden());
    }

    @Test
    void disable_duplicateDisable_idempotentHidden404() throws Exception {
        long targetUserId = createPendingSuperadminRegistration();
        approveAsItAdmin(targetUserId);

        Cookie session = login(PLATFORM_ADMIN_LOGIN);
        // 第一次停用
        mockMvc.perform(withRealCsrf(post("/api/v1/admin/platform/users/{userId}/disable", targetUserId))
                        .cookie(session))
                .andExpect(status().isNoContent());

        // 第二次停用 → 隐藏 404
        // 注意：第一次停用后，PLATFORM_IT_ADMIN 的 session 本身未被吊销（吊销的是 targetUserId 的 session）
        // 需重新获取 session 因为 session 本身可能还有效
        Cookie freshSession = login(PLATFORM_ADMIN_LOGIN);
        mockMvc.perform(withRealCsrf(post("/api/v1/admin/platform/users/{userId}/disable", targetUserId))
                        .cookie(freshSession))
                .andExpect(status().isNotFound());

        deletePendingUser(targetUserId);
    }

    // ── 5. 响应不含 S1 ────────────────────────────────────────────────────────

    @Test
    void listRegistrations_responseDoesNotContainS1Fields() throws Exception {
        long pendingUserId = createPendingSuperadminRegistration();
        Cookie session = login(PLATFORM_ADMIN_LOGIN);

        MvcResult result = mockMvc.perform(get("/api/v1/admin/platform/superadmin-registrations?status=PENDING")
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
        assertThat(body).doesNotContain("department");  // superadmin 内部字段

        deletePendingUser(pendingUserId);
    }

    // ── Helper: user/role setup ────────────────────────────────────────────────

    private void upsertUser(String loginId, String phone) {
        String hash = passwordEncoder.encode(PASSWORD);
        jdbc.update("""
                INSERT INTO users (login_id, email, phone, password_hash, status, created_at, updated_at)
                VALUES (?, ?, ?, ?, 'ACTIVE', NOW(), NOW())
                ON CONFLICT (login_id) DO UPDATE
                    SET password_hash = EXCLUDED.password_hash, status = 'ACTIVE'
                """,
                loginId,
                loginId + "@platform-test.internal",
                phone,
                hash);
    }

    private void setPlatformRole(String loginId, String role) {
        // 先清 membership（平台角色无园级 membership，但保险清一次）
        jdbc.update("""
                DELETE FROM user_kindergarten_memberships
                WHERE user_id = (SELECT user_id FROM users WHERE login_id = ?)
                """, loginId);
        jdbc.update("""
                DELETE FROM user_role_assignments
                WHERE user_id = (SELECT user_id FROM users WHERE login_id = ?)
                """, loginId);
        // PLATFORM scope_id = NULL（ADR-0021 CHECK 约束要求）
        jdbc.update("""
                INSERT INTO user_role_assignments
                    (user_id, role, scope_type, scope_id, status, granted_at)
                SELECT user_id, ?::user_role_enum, 'PLATFORM', NULL, 'ACTIVE', NOW()
                FROM users WHERE login_id = ?
                """, role, loginId);
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

    // ── Helper: PENDING SUPERADMIN 目标用户 ───────────────────────────────────

    /**
     * 创建一个 PENDING SUPERADMIN 申请（模拟 AuthService.register 的平台路径效果）。
     * 造：user(PENDING) + superadmins(PENDING) + user_role_assignments(PENDING, PLATFORM, scope_id=NULL, role=SUPERADMIN)
     * 平台账户无 membership。
     * 返回 target userId。
     */
    private long createPendingSuperadminRegistration() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String loginId = "plt-pending-" + suffix;
        String phone = "050" + suffix.substring(0, 8);
        String hash = passwordEncoder.encode(PASSWORD);

        long userId = jdbc.queryForObject("""
                INSERT INTO users (login_id, email, phone, password_hash, status, created_at, updated_at)
                VALUES (?, ?, ?, ?, 'PENDING', NOW(), NOW())
                RETURNING user_id
                """,
                Long.class,
                loginId,
                loginId + "@pending-platform-test.internal",
                phone,
                hash);

        // PLATFORM role assignment: scope_id = NULL（ADR-0021 CHECK 约束）
        jdbc.update("""
                INSERT INTO user_role_assignments
                    (user_id, role, scope_type, scope_id, status, granted_at)
                VALUES (?, 'SUPERADMIN', 'PLATFORM', NULL, 'PENDING', NOW())
                """, userId);

        // Superadmin 档案（PENDING）
        String suffix2 = UUID.randomUUID().toString().replace("-", "").substring(0, 6);
        jdbc.update("""
                INSERT INTO superadmins
                    (user_id, name, department, status, created_at, updated_at)
                VALUES (?, ?, 'Test Dept', 'PENDING', NOW(), NOW())
                """, userId, "Pending Superadmin " + suffix2);

        // 平台账户无 membership，不插 user_kindergarten_memberships

        return userId;
    }

    private void deletePendingUser(long userId) {
        jdbc.update("DELETE FROM superadmins WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM teachers WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM guardians WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM user_kindergarten_memberships WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM user_role_assignments WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM users WHERE user_id = ?", userId);
    }

    private void deleteUser(long userId) {
        deletePendingUser(userId);
    }

    private void approveAsItAdmin(long targetUserId) throws Exception {
        Cookie session = login(PLATFORM_ADMIN_LOGIN);
        mockMvc.perform(withRealCsrf(post("/api/v1/admin/platform/superadmin-registrations/{userId}/approve", targetUserId))
                        .cookie(session))
                .andExpect(status().isNoContent());
    }

    // ── Helper: login / id lookup ──────────────────────────────────────────────

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

    private String loginIdOf(long userId) {
        return jdbc.queryForObject(
                "SELECT login_id FROM users WHERE user_id = ?", String.class, userId);
    }

    private String statusOf(String table, String idCol, long id) {
        return jdbc.queryForObject(
                "SELECT status::text FROM " + table + " WHERE " + idCol + " = ?",
                String.class, id);
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
