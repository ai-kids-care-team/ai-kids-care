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

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SPEC-0001 §3 / §349：Guardian 关系-scoped 儿童读取集成测试。
 *
 * 验收：
 * - Guardian 只能看到与自己有 ACTIVE 关系（end_date 窗）的同租户儿童。
 * - 无关系 / 关系已结束 / 跨租户 → 隐藏 404，且写 AUTHORIZATION_DENIED 审计（§3.4）。
 * - 未认证 → 401。（Teacher assignment-scoped 访问由 TeacherChildAuthorizationIntegrationTest 覆盖）
 * - 响应只含最小字段（childId/name/status），不含 RRN/address/birthDate/childNo。
 */
@AutoConfigureMockMvc
class GuardianChildAuthorizationIntegrationTest extends BaseIntegrationTest {

    private static final String PASSWORD = "Guardian@Test2026";
    private static final long OWN_KG = 1L;

    private static final String GUARDIAN_LOGIN = "gc-guardian";
    private static final String ADMIN_LOGIN = "gc-admin";

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private ObjectMapper objectMapper;

    @BeforeEach
    void setUpActors() {
        upsertUser(GUARDIAN_LOGIN, "010-0700-0001");
        setGuardianIdentity(GUARDIAN_LOGIN, OWN_KG);

        upsertUser(ADMIN_LOGIN, "010-0700-0002");
        setKindergartenAdminIdentity(ADMIN_LOGIN, OWN_KG);
    }

    // ── 列表：仅 ACTIVE 关系儿童 ────────────────────────────────────────────────

    @Test
    void listRelatedChildren_returnsOnlyActiveRelationshipChildren() throws Exception {
        long guardianId = guardianIdOf(GUARDIAN_LOGIN);
        long relatedChild = insertChild(OWN_KG, "Related Child");
        long unrelatedChild = insertChild(OWN_KG, "Unrelated Child");
        long endedChild = insertChild(OWN_KG, "Ended Child");
        linkGuardianChild(OWN_KG, relatedChild, guardianId, null);             // active
        linkGuardianChild(OWN_KG, endedChild, guardianId, "2020-01-01");        // ended (past)

        Cookie session = login(GUARDIAN_LOGIN);
        mockMvc.perform(get("/api/v1/children").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].childId").value(relatedChild))
                .andExpect(jsonPath("$[0].name").value("Related Child"))
                .andExpect(jsonPath("$[0].status").exists())
                // 最小字段：不得出现 S0/S1
                .andExpect(jsonPath("$[0].rrnFirst6").doesNotExist())
                .andExpect(jsonPath("$[0].rrnEncrypted").doesNotExist())
                .andExpect(jsonPath("$[0].address").doesNotExist())
                .andExpect(jsonPath("$[0].birthDate").doesNotExist())
                .andExpect(jsonPath("$[0].childNo").doesNotExist());

        deleteChild(relatedChild);
        deleteChild(unrelatedChild);
        deleteChild(endedChild);
    }

    // ── 详情：ACTIVE 关系 → 200 最小字段 ───────────────────────────────────────

    @Test
    void getRelatedChild_activeRelationship_returnsMinimalFields() throws Exception {
        long guardianId = guardianIdOf(GUARDIAN_LOGIN);
        long childId = insertChild(OWN_KG, "My Child");
        linkGuardianChild(OWN_KG, childId, guardianId, null);

        Cookie session = login(GUARDIAN_LOGIN);
        mockMvc.perform(get("/api/v1/children/{id}", childId).cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.childId").value(childId))
                .andExpect(jsonPath("$.name").value("My Child"))
                .andExpect(jsonPath("$.status").exists())
                .andExpect(jsonPath("$.rrnFirst6").doesNotExist())
                .andExpect(jsonPath("$.rrnEncrypted").doesNotExist())
                .andExpect(jsonPath("$.address").doesNotExist())
                .andExpect(jsonPath("$.birthDate").doesNotExist())
                .andExpect(jsonPath("$.kindergartenId").doesNotExist());

        deleteChild(childId);
    }

    // ── 详情：无关系 → 隐藏 404 + 审计 DENIED ──────────────────────────────────

    @Test
    void getRelatedChild_noRelationship_returnsHidden404AndAuditsDenied() throws Exception {
        long childId = insertChild(OWN_KG, "Stranger Child");  // 不建立关系

        Cookie session = login(GUARDIAN_LOGIN);
        MvcResult result = mockMvc.perform(get("/api/v1/children/{id}", childId).cookie(session))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Resource not found"))
                .andReturn();

        String correlationId = result.getResponse().getHeader("X-Correlation-Id");
        assertThat(correlationId).isNotBlank();
        Integer denied = jdbc.queryForObject(
                "SELECT count(*) FROM audit_logs WHERE correlation_id = ? AND action = 'AUTHORIZATION_DENIED' "
                        + "AND result = 'DENIED' AND resource_type = 'CHILD' AND resource_id = ? AND user_id = ?",
                Integer.class, correlationId, childId, userIdOf(GUARDIAN_LOGIN));
        assertThat(denied).isEqualTo(1);

        deleteChild(childId);
    }

    // ── 详情：关系已结束 → 隐藏 404 ────────────────────────────────────────────

    @Test
    void getRelatedChild_endedRelationship_returnsHidden404() throws Exception {
        long guardianId = guardianIdOf(GUARDIAN_LOGIN);
        long childId = insertChild(OWN_KG, "Former Child");
        linkGuardianChild(OWN_KG, childId, guardianId, "2020-01-01");  // end_date 过去

        Cookie session = login(GUARDIAN_LOGIN);
        mockMvc.perform(get("/api/v1/children/{id}", childId).cookie(session))
                .andExpect(status().isNotFound());

        deleteChild(childId);
    }

    // ── 详情：跨租户儿童 → 隐藏 404 ────────────────────────────────────────────

    @Test
    void getRelatedChild_crossTenantChild_returnsHidden404() throws Exception {
        long foreignKg = insertActiveKindergarten();
        long childId = insertChild(foreignKg, "Foreign Child");

        Cookie session = login(GUARDIAN_LOGIN);  // scoped to OWN_KG
        mockMvc.perform(get("/api/v1/children/{id}", childId).cookie(session))
                .andExpect(status().isNotFound());

        deleteChild(childId);
    }

    // ── 角色门：KINDERGARTEN_ADMIN → 403（不在 CHILD_READ 门内，§351 非目标）──────────

    @Test
    void children_kindergartenAdminRole_returns403() throws Exception {
        Cookie session = login(ADMIN_LOGIN);
        mockMvc.perform(get("/api/v1/children").cookie(session))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/children/1").cookie(session))
                .andExpect(status().isForbidden());
    }

    // ── 未认证 → 401 ────────────────────────────────────────────────────────────

    @Test
    void children_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/children"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/children/1"))
                .andExpect(status().isUnauthorized());
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private void upsertUser(String loginId, String phone) {
        String hash = passwordEncoder.encode(PASSWORD);
        jdbc.update("""
                INSERT INTO users (login_id, email, phone, password_hash, status, created_at, updated_at)
                VALUES (?, ?, ?, ?, 'ACTIVE', NOW(), NOW())
                ON CONFLICT (login_id) DO UPDATE
                    SET password_hash = EXCLUDED.password_hash, status = 'ACTIVE'
                """, loginId, loginId + "@gc-test.internal", phone, hash);
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
                """, kindergartenId, loginId);
        // guardian 档案被 child_guardian_relationships FK 引用：upsert（不删）使 guardian_id 稳定，
        // 避免残留关系导致 @BeforeEach 删除时 FK 失败级联。
        jdbc.update("""
                INSERT INTO guardians
                    (kindergarten_id, user_id, name, rrn_hash, rrn_first6, gender, address,
                     status, created_at, updated_at)
                SELECT ?, user_id, ?, encode(sha256(convert_to(login_id, 'UTF8')), 'hex'), '000101', 'MALE', 'Test address', 'ACTIVE', NOW(), NOW()
                FROM users WHERE login_id = ?
                ON CONFLICT (user_id) DO UPDATE
                    SET status = 'ACTIVE', kindergarten_id = EXCLUDED.kindergarten_id
                """, kindergartenId, "Guardian " + loginId, loginId);
    }

    // KINDERGARTEN_ADMIN 身份：角色 + membership + DIRECTOR level 档案（与 AdminApproval 测试同范式）。
    // 用于断言 admin 不在 CHILD_READ 门内（GET /children → 403 在 @PreAuthorize 处即拒绝）。
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
                """, kindergartenId, loginId);
        jdbc.update("DELETE FROM teachers WHERE user_id = "
                + "(SELECT user_id FROM users WHERE login_id = ?)", loginId);
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 6);
        jdbc.update("""
                INSERT INTO teachers
                    (kindergarten_id, user_id, staff_no, name, gender, rrn_hash, rrn_first6,
                     level, start_date, status, created_at, updated_at)
                SELECT ?, user_id, ?, ?, 'MALE', ?, '000101', 'DIRECTOR'::level_enum,
                       '2025-03-01', 'ACTIVE', NOW(), NOW()
                FROM users WHERE login_id = ?
                """, kindergartenId, "STAFF-" + suffix, "Admin " + loginId, "FIXTURE-HASH-" + suffix, loginId);
    }

    private void clearRoleAndMembership(String loginId) {
        jdbc.update("DELETE FROM user_kindergarten_memberships WHERE user_id = "
                + "(SELECT user_id FROM users WHERE login_id = ?)", loginId);
        jdbc.update("DELETE FROM user_role_assignments WHERE user_id = "
                + "(SELECT user_id FROM users WHERE login_id = ?)", loginId);
    }

    private long insertChild(long kindergartenId, String name) {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        return jdbc.queryForObject("""
                INSERT INTO children
                    (kindergarten_id, name, child_no, rrn_first6, rrn_hash, birth_date, gender,
                     address, enroll_date, status, created_at, updated_at)
                VALUES (?, ?, ?, '200101', ?, '2020-01-01', 'MALE', 'Child address',
                        '2024-03-01', 'ACTIVE', NOW(), NOW())
                RETURNING child_id
                """, Long.class, kindergartenId, name, "CNO-" + suffix, "FIXTURE-HASH-CHILD-" + suffix);
    }

    private void linkGuardianChild(long kindergartenId, long childId, long guardianId, String endDate) {
        jdbc.update("""
                INSERT INTO child_guardian_relationships
                    (kindergarten_id, child_id, guardian_id, relationship, is_primary, priority,
                     start_date, end_date, created_at, updated_at)
                VALUES (?, ?, ?, 'FATHER'::relationship_enum, true, 1, '2024-03-01', ?::date, NOW(), NOW())
                """, kindergartenId, childId, guardianId, endDate);
    }

    private void deleteChild(long childId) {
        jdbc.update("DELETE FROM child_guardian_relationships WHERE child_id = ?", childId);
        jdbc.update("DELETE FROM audit_logs WHERE resource_id = ?", childId);
        jdbc.update("DELETE FROM children WHERE child_id = ?", childId);
    }

    private long insertActiveKindergarten() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        return jdbc.queryForObject("""
                INSERT INTO kindergartens
                    (name, address, region_code, code, business_registration_no,
                     contact_name, contact_phone, contact_email, status, created_at, updated_at)
                VALUES (?, ?, 'TEST', ?, ?, 'Contact', '01000000000', ?, 'ACTIVE', NOW(), NOW())
                RETURNING kindergarten_id
                """, Long.class, "Test KG " + suffix, "Test address", "TCODE-" + suffix,
                "TBRN-" + suffix, "tkg-" + suffix + "@test.internal");
    }

    private long userIdOf(String loginId) {
        return jdbc.queryForObject("SELECT user_id FROM users WHERE login_id = ?", Long.class, loginId);
    }

    private long guardianIdOf(String loginId) {
        return jdbc.queryForObject(
                "SELECT guardian_id FROM guardians WHERE user_id = "
                        + "(SELECT user_id FROM users WHERE login_id = ?)",
                Long.class, loginId);
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
        String token = objectMapper.readTree(csrf.getResponse().getContentAsString())
                .get("token").asText();
        return request
                .cookie(csrf.getResponse().getCookie("XSRF-TOKEN"))
                .header("X-XSRF-TOKEN", token);
    }
}
