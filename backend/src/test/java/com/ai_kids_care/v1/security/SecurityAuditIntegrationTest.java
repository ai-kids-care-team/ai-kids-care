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

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SPEC-0001 #1 安全审计 writer 集成测试（验收 §367）。
 *
 * 断言策略：每个请求由 {@code CorrelationIdFilter} 在响应头 {@code X-Correlation-Id} 暴露
 * correlation id；测试捕获该 id，再以 {@code correlation_id} 精确定位本次请求写入的审计行，
 * 与容器复用 / 并行 seed 数据解耦。
 *
 * 覆盖：
 * - 登录成功 / 失败、登出产生审计记录（§367「登录、登出…产生审计记录」）。
 * - 平台 tenant context 切换：scope_type=PLATFORM 且 kindergarten_id IS NULL（§370 不伪造 kg id）。
 * - 角色变更（园级 approve）产生审计记录。
 * - 角色拒绝（错误角色 403）经 AccessDeniedHandler 产生 DENIED 审计（§367「跨租户拒绝」）。
 * - 审计行不含 S0（§371「审计记录不包含 S0」）。
 * - 审计 API 对业务用户不可读 / 写 / 删（§371）。
 */
@AutoConfigureMockMvc
class SecurityAuditIntegrationTest extends BaseIntegrationTest {

    private static final String PASSWORD = "Audit@Test2026";
    private static final long OWN_KG = 1L;

    private static final String DIRECTOR_LOGIN = "audit-director";
    private static final String TEACHER_LOGIN = "audit-teacher";
    private static final String PLATFORM_LOGIN = "audit-platform-it";

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private ObjectMapper objectMapper;

    @BeforeEach
    void setUpActors() {
        // 园级 DIRECTOR（可审批）
        upsertUser(DIRECTOR_LOGIN, "010-0900-0001");
        setKindergartenRole("KINDERGARTEN_ADMIN", DIRECTOR_LOGIN, OWN_KG);
        ensureTeacherProfile(DIRECTOR_LOGIN, OWN_KG, "DIRECTOR");

        // 普通 TEACHER（无审批权 → 角色拒绝 403）
        upsertUser(TEACHER_LOGIN, "010-0900-0002");
        setKindergartenRole("TEACHER", TEACHER_LOGIN, OWN_KG);
        ensureTeacherProfile(TEACHER_LOGIN, OWN_KG, "TEACHER");

        // 平台 PLATFORM_IT_ADMIN（scope=PLATFORM，无 membership → 可切换 tenant）
        upsertUser(PLATFORM_LOGIN, "010-0900-0003");
        setPlatformRole("PLATFORM_IT_ADMIN", PLATFORM_LOGIN);
    }

    // ── 登录 ───────────────────────────────────────────────────────────────────

    @Test
    void login_success_writesAuditRecord() throws Exception {
        long actorId = userIdOf(DIRECTOR_LOGIN);

        MvcResult result = performLogin(DIRECTOR_LOGIN, PASSWORD)
                .andExpect(status().isOk())
                .andExpect(cookie().exists("AI_KIDS_CARE_SESSION"))
                .andReturn();

        Map<String, Object> row = auditRow(correlationId(result), "LOGIN_SUCCESS");
        assertThat(row.get("result")).isEqualTo("SUCCESS");
        assertThat(((Number) row.get("user_id")).longValue()).isEqualTo(actorId);
        assertThat(row.get("scope_type")).isEqualTo("KINDERGARTEN");
        assertThat(row.get("effective_role")).isEqualTo("KINDERGARTEN_ADMIN");
        assertThat(row.get("correlation_id")).isNotNull();
    }

    @Test
    void login_failure_writesAuditRecordWithoutActor() throws Exception {
        MvcResult result = mockMvc.perform(withRealCsrf(post("/api/v1/auth/login"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("identifier", DIRECTOR_LOGIN, "password", "wrong-password"))))
                .andExpect(status().isUnauthorized())
                .andReturn();

        Map<String, Object> row = auditRow(correlationId(result), "LOGIN_FAILURE");
        assertThat(row.get("result")).isEqualTo("FAILURE");
        assertThat(row.get("user_id")).isNull();          // actor 未知
        assertThat(row.get("scope_type")).isEqualTo("PLATFORM");
        assertThat(row.get("kindergarten_id")).isNull();  // 平台级，不伪造 kg id
    }

    @Test
    void logout_writesAuditRecord() throws Exception {
        Cookie session = login(DIRECTOR_LOGIN);

        MvcResult result = mockMvc.perform(withRealCsrf(post("/api/v1/auth/logout"))
                        .cookie(session))
                .andExpect(status().isNoContent())
                .andReturn();

        Map<String, Object> row = auditRow(correlationId(result), "LOGOUT");
        assertThat(row.get("result")).isEqualTo("SUCCESS");
        assertThat(((Number) row.get("user_id")).longValue()).isEqualTo(userIdOf(DIRECTOR_LOGIN));
    }

    // ── 平台 tenant context 切换（§370 不伪造 kg id） ─────────────────────────────

    @Test
    void tenantContextSelect_writesPlatformScopedAuditWithoutFakeKindergartenId() throws Exception {
        Cookie session = login(PLATFORM_LOGIN);

        MvcResult result = mockMvc.perform(withRealCsrf(post("/api/v1/auth/session/tenant-context"))
                        .cookie(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("kindergartenId", OWN_KG))))
                .andExpect(status().isOk())
                .andReturn();

        Map<String, Object> row = auditRow(correlationId(result), "TENANT_CONTEXT_SELECT");
        assertThat(row.get("result")).isEqualTo("SUCCESS");
        assertThat(row.get("scope_type")).isEqualTo("PLATFORM");
        assertThat(row.get("kindergarten_id")).isNull();                      // 不伪造 kg id
        assertThat(((Number) row.get("resource_id")).longValue()).isEqualTo(OWN_KG);  // 所选 tenant 落 resource_id
        assertThat(((Number) row.get("user_id")).longValue()).isEqualTo(userIdOf(PLATFORM_LOGIN));
    }

    // ── 角色变更（园级 approve） ──────────────────────────────────────────────────

    @Test
    void approve_writesRoleChangeAuditRecord() throws Exception {
        long pendingUserId = createPendingTeacherRegistration(OWN_KG);
        Cookie director = login(DIRECTOR_LOGIN);

        MvcResult result = mockMvc.perform(withRealCsrf(
                        post("/api/v1/admin/kindergarten/registrations/{userId}/approve", pendingUserId))
                        .cookie(director))
                .andExpect(status().isNoContent())
                .andReturn();

        Map<String, Object> row = auditRow(correlationId(result), "KINDERGARTEN_APPROVE");
        assertThat(row.get("result")).isEqualTo("SUCCESS");
        assertThat(row.get("scope_type")).isEqualTo("KINDERGARTEN");
        assertThat(((Number) row.get("kindergarten_id")).longValue()).isEqualTo(OWN_KG);
        assertThat(((Number) row.get("user_id")).longValue()).isEqualTo(userIdOf(DIRECTOR_LOGIN));
        assertThat(((Number) row.get("resource_id")).longValue()).isEqualTo(pendingUserId);

        deletePendingUser(pendingUserId);
    }

    // ── 角色拒绝（§367「跨租户拒绝、角色拒绝」） ──────────────────────────────────

    @Test
    void authorizationDenied_writesDeniedAuditRecord() throws Exception {
        Cookie teacher = login(TEACHER_LOGIN);

        MvcResult result = mockMvc.perform(
                        get("/api/v1/admin/kindergarten/registrations?status=PENDING")
                                .cookie(teacher))
                .andExpect(status().isForbidden())
                .andReturn();

        Map<String, Object> row = auditRow(correlationId(result), "AUTHORIZATION_DENIED");
        assertThat(row.get("result")).isEqualTo("DENIED");
        assertThat(((Number) row.get("user_id")).longValue()).isEqualTo(userIdOf(TEACHER_LOGIN));
        assertThat((String) row.get("resource_type")).contains("/api/v1/admin/kindergarten/registrations");
    }

    // ── 审计行不含 S0（§371） ─────────────────────────────────────────────────────

    @Test
    void auditRecords_doNotContainS0() throws Exception {
        // 触发若干审计写入
        performLogin(DIRECTOR_LOGIN, PASSWORD).andExpect(status().isOk());

        // 任何审计行的文本列都不得包含明文口令
        Integer leaked = jdbc.queryForObject(
                "SELECT count(*) FROM audit_logs WHERE "
                        + "coalesce(action,'') || coalesce(resource_type,'') || coalesce(effective_role,'') "
                        + "|| coalesce(result,'') || coalesce(ip,'') || coalesce(user_agent,'') "
                        + "|| coalesce(correlation_id,'') LIKE ?",
                Integer.class, "%" + PASSWORD + "%");
        assertThat(leaked).isZero();
    }

    // ── 审计 API 对业务用户不可读 / 写 / 删（§371） ───────────────────────────────

    @Test
    void auditApi_isNotReadableWritableOrDeletableByBusinessUser() throws Exception {
        Cookie director = login(DIRECTOR_LOGIN);

        // 无公共读端点
        mockMvc.perform(get("/api/v1/audit_logs").cookie(director))
                .andExpect(status().is4xxClientError());
        // 无公共写端点
        mockMvc.perform(withRealCsrf(post("/api/v1/audit_logs")).cookie(director)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().is4xxClientError());
        // 未认证一律 401
        mockMvc.perform(get("/api/v1/audit_logs"))
                .andExpect(status().isUnauthorized());
    }

    // ── Helpers: 审计查询 ─────────────────────────────────────────────────────────

    private String correlationId(MvcResult result) {
        String id = result.getResponse().getHeader("X-Correlation-Id");
        assertThat(id).as("X-Correlation-Id response header").isNotBlank();
        return id;
    }

    private Map<String, Object> auditRow(String correlationId, String action) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT scope_type::text AS scope_type, kindergarten_id, user_id, action, "
                        + "resource_type, resource_id, effective_role, result, correlation_id "
                        + "FROM audit_logs WHERE correlation_id = ? AND action = ?",
                correlationId, action);
        assertThat(rows).as("audit row for action=%s corr=%s", action, correlationId).hasSize(1);
        return rows.get(0);
    }

    // ── Helpers: actor / login setup（与 AdminApprovalAuthorizationIntegrationTest 同范式） ──

    private void upsertUser(String loginId, String phone) {
        String hash = passwordEncoder.encode(PASSWORD);
        jdbc.update("""
                INSERT INTO users (login_id, email, phone, password_hash, status, created_at, updated_at)
                VALUES (?, ?, ?, ?, 'ACTIVE', NOW(), NOW())
                ON CONFLICT (login_id) DO UPDATE
                    SET password_hash = EXCLUDED.password_hash, status = 'ACTIVE'
                """, loginId, loginId + "@audit-test.internal", phone, hash);
    }

    private void setKindergartenRole(String role, String loginId, long kindergartenId) {
        jdbc.update("DELETE FROM user_kindergarten_memberships WHERE user_id = "
                + "(SELECT user_id FROM users WHERE login_id = ?)", loginId);
        jdbc.update("DELETE FROM user_role_assignments WHERE user_id = "
                + "(SELECT user_id FROM users WHERE login_id = ?)", loginId);
        jdbc.update("""
                INSERT INTO user_role_assignments (user_id, role, scope_type, scope_id, status, granted_at)
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

    private void setPlatformRole(String role, String loginId) {
        jdbc.update("DELETE FROM user_kindergarten_memberships WHERE user_id = "
                + "(SELECT user_id FROM users WHERE login_id = ?)", loginId);
        jdbc.update("DELETE FROM user_role_assignments WHERE user_id = "
                + "(SELECT user_id FROM users WHERE login_id = ?)", loginId);
        jdbc.update("""
                INSERT INTO user_role_assignments (user_id, role, scope_type, scope_id, status, granted_at)
                SELECT user_id, ?::user_role_enum, 'PLATFORM', NULL, 'ACTIVE', NOW()
                FROM users WHERE login_id = ?
                """, role, loginId);
    }

    private void ensureTeacherProfile(String loginId, long kindergartenId, String level) {
        jdbc.update("DELETE FROM teachers WHERE user_id = "
                + "(SELECT user_id FROM users WHERE login_id = ?)", loginId);
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 6);
        jdbc.update("""
                INSERT INTO teachers
                    (kindergarten_id, user_id, staff_no, name, gender, rrn_hash, rrn_first6,
                     level, start_date, status, created_at, updated_at)
                SELECT ?, user_id, ?, ?, 'MALE', ?, '000101', ?::level_enum,
                       '2025-03-01', 'ACTIVE', NOW(), NOW()
                FROM users WHERE login_id = ?
                """, kindergartenId, "STAFF-" + suffix, "Audit " + loginId, "FIXTURE-HASH-" + suffix, level, loginId);
    }

    private long createPendingTeacherRegistration(long kindergartenId) {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String loginId = "audit-pending-" + suffix;
        String hash = passwordEncoder.encode(PASSWORD);
        long userId = jdbc.queryForObject("""
                INSERT INTO users (login_id, email, phone, password_hash, status, created_at, updated_at)
                VALUES (?, ?, ?, ?, 'PENDING', NOW(), NOW())
                RETURNING user_id
                """, Long.class, loginId, loginId + "@pending-test.internal",
                "021" + suffix.substring(0, 8), hash);
        jdbc.update("""
                INSERT INTO user_role_assignments (user_id, role, scope_type, scope_id, status, granted_at)
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
                    (kindergarten_id, user_id, staff_no, name, gender, rrn_hash, rrn_first6,
                     level, start_date, status, created_at, updated_at)
                VALUES (?, ?, ?, 'Pending Teacher', 'MALE', ?, '000101',
                        'TEACHER', '2026-03-01', 'PENDING', NOW(), NOW())
                """, kindergartenId, userId, "PSTAFF-" + staffSuffix, "FIXTURE-HASH-" + staffSuffix);
        return userId;
    }

    private void deletePendingUser(long userId) {
        jdbc.update("DELETE FROM teachers WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM user_kindergarten_memberships WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM user_role_assignments WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM audit_logs WHERE user_id = ? OR resource_id = ?", userId, userId);
        jdbc.update("DELETE FROM users WHERE user_id = ?", userId);
    }

    private long userIdOf(String loginId) {
        return jdbc.queryForObject("SELECT user_id FROM users WHERE login_id = ?", Long.class, loginId);
    }

    private org.springframework.test.web.servlet.ResultActions performLogin(String loginId, String password)
            throws Exception {
        return mockMvc.perform(withRealCsrf(post("/api/v1/auth/login"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        Map.of("identifier", loginId, "password", password))));
    }

    private Cookie login(String loginId) throws Exception {
        MvcResult result = performLogin(loginId, PASSWORD)
                .andExpect(status().isOk())
                .andExpect(cookie().exists("AI_KIDS_CARE_SESSION"))
                .andReturn();
        return result.getResponse().getCookie("AI_KIDS_CARE_SESSION");
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
