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

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SPEC-0001 §351：Teacher assignment-scoped 儿童读取集成测试。
 *
 * 验收：
 * - TEACHER 只能看到通过 ACTIVE class_teacher_assignment → ACTIVE child_class_assignment
 *   链（两个窗口均含 today）可达的同租户 ACTIVE 儿童。
 * - 无分配 / 分配已结束 / 跨租户儿童 → 隐藏 404，且写 AUTHORIZATION_DENIED 审计（§3.4）。
 * - 未认证 → 401；响应只含最小字段（childId/name/status），不含 RRN/address/birthDate。
 */
@AutoConfigureMockMvc
class TeacherChildAuthorizationIntegrationTest extends BaseIntegrationTest {

    private static final String PASSWORD = "Teacher@Child2026";
    private static final long OWN_KG = 1L;

    private static final String TEACHER_LOGIN = "tc-teacher";

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private ObjectMapper objectMapper;

    @BeforeEach
    void setUpTeacher() {
        upsertUser(TEACHER_LOGIN, "010-0800-0001");
        setTeacherIdentity(TEACHER_LOGIN, OWN_KG);
    }

    // ── 列表：仅 ACTIVE assignment 班级内儿童 ─────────────────────────────────────

    @Test
    void listAssignedChildren_returnsOnlyChildrenInActivelyAssignedClasses() throws Exception {
        long teacherUserId = userIdOf(TEACHER_LOGIN);
        long teacherId = teacherIdOf(TEACHER_LOGIN);

        // Class A: teacher is actively assigned
        long classA = insertClass(OWN_KG, "ClassA");
        // Class B: teacher is NOT assigned
        long classB = insertClass(OWN_KG, "ClassB");

        assignTeacherToClass(teacherId, classA, "ACTIVE",
                LocalDate.now().minusDays(1), null);

        long child1 = insertChild(OWN_KG, "Child In ClassA");
        long child2 = insertChild(OWN_KG, "Child In ClassB");
        long child3 = insertChild(OWN_KG, "Child Ended Assignment ClassA");

        // child1 in classA — visible
        insertChildClassAssignment(OWN_KG, child1, classA, "ACTIVE",
                LocalDate.now().minusDays(1), null, teacherUserId);
        // child2 in classB — teacher not assigned to classB, not visible
        insertChildClassAssignment(OWN_KG, child2, classB, "ACTIVE",
                LocalDate.now().minusDays(1), null, teacherUserId);
        // child3 in classA but assignment already ended — not visible
        insertChildClassAssignment(OWN_KG, child3, classA, "ACTIVE",
                LocalDate.now().minusDays(30), LocalDate.now().minusDays(1), teacherUserId);

        Cookie session = login(TEACHER_LOGIN);
        mockMvc.perform(get("/api/v1/children").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].childId").value(child1))
                .andExpect(jsonPath("$[0].name").value("Child In ClassA"))
                .andExpect(jsonPath("$[0].status").exists())
                // 最小字段：不得出现 S0/S1
                .andExpect(jsonPath("$[0].rrnFirst6").doesNotExist())
                .andExpect(jsonPath("$[0].rrnEncrypted").doesNotExist())
                .andExpect(jsonPath("$[0].address").doesNotExist())
                .andExpect(jsonPath("$[0].birthDate").doesNotExist())
                .andExpect(jsonPath("$[0].childNo").doesNotExist());

        deleteChildWithAssignments(child1);
        deleteChildWithAssignments(child2);
        deleteChildWithAssignments(child3);
        deleteClassTeacherAssignments(teacherId);
        deleteClass(classA);
        deleteClass(classB);
    }

    // ── 详情：ACTIVE assignment → 200 最小字段 ───────────────────────────────────

    @Test
    void getAssignedChild_returns200MinimalFields() throws Exception {
        long teacherUserId = userIdOf(TEACHER_LOGIN);
        long teacherId = teacherIdOf(TEACHER_LOGIN);

        long classA = insertClass(OWN_KG, "ClassA-Get");
        assignTeacherToClass(teacherId, classA, "ACTIVE",
                LocalDate.now().minusDays(1), null);

        long childId = insertChild(OWN_KG, "My Assigned Child");
        insertChildClassAssignment(OWN_KG, childId, classA, "ACTIVE",
                LocalDate.now().minusDays(1), null, teacherUserId);

        Cookie session = login(TEACHER_LOGIN);
        mockMvc.perform(get("/api/v1/children/{id}", childId).cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.childId").value(childId))
                .andExpect(jsonPath("$.name").value("My Assigned Child"))
                .andExpect(jsonPath("$.status").exists())
                .andExpect(jsonPath("$.rrnFirst6").doesNotExist())
                .andExpect(jsonPath("$.rrnEncrypted").doesNotExist())
                .andExpect(jsonPath("$.address").doesNotExist())
                .andExpect(jsonPath("$.birthDate").doesNotExist())
                .andExpect(jsonPath("$.kindergartenId").doesNotExist());

        deleteChildWithAssignments(childId);
        deleteClassTeacherAssignments(teacherId);
        deleteClass(classA);
    }

    // ── 详情：无 assignment（同租户）→ 隐藏 404 + 审计 DENIED ─────────────────────

    @Test
    void getUnassignedChild_sameTenant_returnsHidden404AndAuditsDenied() throws Exception {
        // No class assignment for this child — teacher cannot see it
        long childId = insertChild(OWN_KG, "Unassigned Child");

        Cookie session = login(TEACHER_LOGIN);
        MvcResult result = mockMvc.perform(get("/api/v1/children/{id}", childId).cookie(session))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Resource not found"))
                .andReturn();

        String correlationId = result.getResponse().getHeader("X-Correlation-Id");
        assertThat(correlationId).isNotBlank();
        Integer denied = jdbc.queryForObject(
                "SELECT count(*) FROM audit_logs WHERE correlation_id = ? AND action = 'AUTHORIZATION_DENIED' "
                        + "AND result = 'DENIED' AND resource_type = 'CHILD' AND resource_id = ? AND user_id = ?",
                Integer.class, correlationId, childId, userIdOf(TEACHER_LOGIN));
        assertThat(denied).isEqualTo(1);

        deleteChildWithAssignments(childId);
    }

    // ── 详情：class_teacher_assignment 已结束 → 隐藏 404 ─────────────────────────

    @Test
    void getChild_endedClassTeacherAssignment_returnsHidden404() throws Exception {
        long teacherUserId = userIdOf(TEACHER_LOGIN);
        long teacherId = teacherIdOf(TEACHER_LOGIN);

        long classA = insertClass(OWN_KG, "ClassA-Ended-CTA");
        // CTA ended in the past
        assignTeacherToClass(teacherId, classA, "ACTIVE",
                LocalDate.now().minusDays(30), LocalDate.now().minusDays(1));

        long childId = insertChild(OWN_KG, "Child In Ended CTA Class");
        insertChildClassAssignment(OWN_KG, childId, classA, "ACTIVE",
                LocalDate.now().minusDays(30), null, teacherUserId);

        Cookie session = login(TEACHER_LOGIN);
        mockMvc.perform(get("/api/v1/children/{id}", childId).cookie(session))
                .andExpect(status().isNotFound());

        deleteChildWithAssignments(childId);
        deleteClassTeacherAssignments(teacherId);
        deleteClass(classA);
    }

    // ── 详情：跨租户儿童 → 隐藏 404 ─────────────────────────────────────────────

    @Test
    void getChild_crossTenantChild_returnsHidden404() throws Exception {
        long foreignKg = insertActiveKindergarten();
        long childId = insertChild(foreignKg, "Foreign Child");

        Cookie session = login(TEACHER_LOGIN);  // scoped to OWN_KG
        mockMvc.perform(get("/api/v1/children/{id}", childId).cookie(session))
                .andExpect(status().isNotFound());

        deleteChildWithAssignments(childId);
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
                """, loginId, loginId + "@tc-test.internal", phone, hash);
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
                """, kindergartenId, loginId);
        // Upsert teacher profile (stable teacher_id across @BeforeEach)
        jdbc.update("""
                DELETE FROM class_teacher_assignments
                WHERE teacher_id IN (
                    SELECT teacher_id FROM teachers
                    WHERE user_id = (SELECT user_id FROM users WHERE login_id = ?))
                """, loginId);
        jdbc.update("""
                DELETE FROM teachers
                WHERE user_id = (SELECT user_id FROM users WHERE login_id = ?)
                """, loginId);
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 6);
        jdbc.update("""
                INSERT INTO teachers
                    (kindergarten_id, user_id, staff_no, name, gender, rrn_encrypted, rrn_first6,
                     level, start_date, status, created_at, updated_at)
                SELECT ?, user_id, ?, ?, 'MALE', 'ENC', '000101', 'TEACHER'::level_enum,
                       '2025-03-01', 'ACTIVE', NOW(), NOW()
                FROM users WHERE login_id = ?
                """, kindergartenId, "STAFF-" + suffix, "Teacher " + loginId, loginId);
    }

    private void clearRoleAndMembership(String loginId) {
        jdbc.update("DELETE FROM user_kindergarten_memberships WHERE user_id = "
                + "(SELECT user_id FROM users WHERE login_id = ?)", loginId);
        jdbc.update("DELETE FROM user_role_assignments WHERE user_id = "
                + "(SELECT user_id FROM users WHERE login_id = ?)", loginId);
    }

    private long insertClass(long kindergartenId, String name) {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 6);
        return jdbc.queryForObject("""
                INSERT INTO classes
                    (kindergarten_id, name, grade, academic_year, start_date, end_date, status,
                     created_at, updated_at)
                VALUES (?, ?, 'Grade1', 2026, '2026-03-01', '2026-12-31', 'ACTIVE', NOW(), NOW())
                RETURNING class_id
                """, Long.class, kindergartenId, name + "-" + suffix);
    }

    private void assignTeacherToClass(
            long teacherId, long classId, String status, LocalDate start, LocalDate end) {
        jdbc.update("""
                INSERT INTO class_teacher_assignments
                    (kindergarten_id, class_id, teacher_id, role, start_date, end_date,
                     status, created_by_user_id, created_at, updated_at)
                SELECT ?, ?, ?, 'HOMEROOM'::class_teacher_role_enum, ?, ?,
                       ?::status_enum, user_id, NOW(), NOW()
                FROM users WHERE login_id = ?
                """, OWN_KG, classId, teacherId, start, end, status, TEACHER_LOGIN);
    }

    private long insertChild(long kindergartenId, String name) {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        return jdbc.queryForObject("""
                INSERT INTO children
                    (kindergarten_id, name, child_no, rrn_first6, rrn_encrypted, birth_date, gender,
                     address, enroll_date, status, created_at, updated_at)
                VALUES (?, ?, ?, '200101', 'ENC', '2020-01-01', 'MALE', 'Child address',
                        '2024-03-01', 'ACTIVE', NOW(), NOW())
                RETURNING child_id
                """, Long.class, kindergartenId, name, "CNO-" + suffix);
    }

    private void insertChildClassAssignment(
            long kindergartenId, long childId, long classId, String status,
            LocalDate start, LocalDate end, long createdByUserId) {
        jdbc.update("""
                INSERT INTO child_class_assignments
                    (kindergarten_id, child_id, class_id, start_date, end_date, status,
                     created_by_user_id, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?::status_enum, ?, NOW(), NOW())
                """, kindergartenId, childId, classId, start, end, status, createdByUserId);
    }

    private void deleteChildWithAssignments(long childId) {
        jdbc.update("DELETE FROM child_class_assignments WHERE child_id = ?", childId);
        jdbc.update("DELETE FROM audit_logs WHERE resource_id = ?", childId);
        jdbc.update("DELETE FROM children WHERE child_id = ?", childId);
    }

    private void deleteClassTeacherAssignments(long teacherId) {
        jdbc.update("DELETE FROM class_teacher_assignments WHERE teacher_id = ?", teacherId);
    }

    private void deleteClass(long classId) {
        jdbc.update("DELETE FROM classes WHERE class_id = ?", classId);
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
        return jdbc.queryForObject("SELECT user_id FROM users WHERE login_id = ?",
                Long.class, loginId);
    }

    private long teacherIdOf(String loginId) {
        return jdbc.queryForObject(
                "SELECT teacher_id FROM teachers WHERE user_id = "
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
