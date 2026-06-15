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
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Teacher class access is narrowed to the principal's effective assignments
 * (ADR-0019 §7 TeacherAssignmentPolicy): a TEACHER may only list/read classes
 * covered by an ACTIVE class_teacher_assignment whose date window contains today,
 * while a KINDERGARTEN_ADMIN keeps kindergarten-wide access. A same-tenant class
 * the teacher is not assigned to is hidden with a 404, not merely role-allowed.
 *
 * Note: ClassVO.classId is intentionally null in the current mapper, so list
 * membership is asserted by element count and get-by-id reachability is asserted by
 * the path id (200 for assigned / own-scope, hidden 404 otherwise).
 */
@AutoConfigureMockMvc
class TeacherAssignmentAuthorizationIntegrationTest extends BaseIntegrationTest {

    private static final String LOGIN_ID = "teacher-assignment-test-user";
    private static final String PASSWORD = "Test@Assign2024";
    private static final long KG = 1L;

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private ObjectMapper objectMapper;

    @BeforeEach
    void upsertUserAndClearTeacherArtifacts() {
        jdbc.update("""
                INSERT INTO users (login_id, email, phone, password_hash, status, created_at, updated_at)
                VALUES (?, ?, ?, ?, 'ACTIVE', NOW(), NOW())
                ON CONFLICT (login_id) DO UPDATE
                    SET password_hash = EXCLUDED.password_hash, status = 'ACTIVE'
                """,
                LOGIN_ID, LOGIN_ID + "@test-assign.internal", "010-0000-9995",
                passwordEncoder.encode(PASSWORD));
        clearTeacherArtifacts();
    }

    @Test
    void teacherSeesOnlyActivelyAssignedClasses() throws Exception {
        long[] classes = twoKindergartenClasses();
        long assigned = classes[0];
        long unassigned = classes[1];
        long teacherId = createActiveTeacher();
        assignTeacherToClass(teacherId, assigned, "ACTIVE",
                LocalDate.now().minusDays(1), null);
        configureKindergartenRole("TEACHER");
        Cookie session = login();

        mockMvc.perform(get("/api/v1/classes/{id}", assigned).cookie(session))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/classes/{id}", unassigned).cookie(session))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Resource not found"));
        assertThat(listedClassCount(session)).isEqualTo(1);
    }

    @Test
    void teacherWithDisabledOrExpiredAssignmentSeesNoClasses() throws Exception {
        long[] classes = twoKindergartenClasses();
        long teacherId = createActiveTeacher();
        assignTeacherToClass(teacherId, classes[0], "DISABLED",
                LocalDate.now().minusDays(10), null);
        assignTeacherToClass(teacherId, classes[1], "ACTIVE",
                LocalDate.now().minusDays(10), LocalDate.now().minusDays(1));
        configureKindergartenRole("TEACHER");
        Cookie session = login();

        assertThat(listedClassCount(session)).isZero();
        mockMvc.perform(get("/api/v1/classes/{id}", classes[0]).cookie(session))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/classes/{id}", classes[1]).cookie(session))
                .andExpect(status().isNotFound());
    }

    @Test
    void kindergartenAdminSeesAllKindergartenClasses() throws Exception {
        long[] classes = twoKindergartenClasses();
        configureKindergartenRole("KINDERGARTEN_ADMIN");
        Cookie session = login();

        long total = kindergartenClassCount();
        assertThat(total).isGreaterThan(1);
        assertThat(listedClassCount(session)).isEqualTo((int) total);
        mockMvc.perform(get("/api/v1/classes/{id}", classes[1]).cookie(session))
                .andExpect(status().isOk());
    }

    private long[] twoKindergartenClasses() {
        List<Long> ids = jdbc.queryForList(
                "SELECT class_id FROM classes WHERE kindergarten_id = ? ORDER BY class_id",
                Long.class, KG);
        return new long[]{ids.get(0), ids.get(1)};
    }

    private long kindergartenClassCount() {
        return jdbc.queryForObject(
                "SELECT count(*) FROM classes WHERE kindergarten_id = ?", Long.class, KG);
    }

    private int listedClassCount(Cookie session) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/classes").cookie(session))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("content")
                .size();
    }

    private long createActiveTeacher() {
        return jdbc.queryForObject("""
                INSERT INTO teachers
                    (kindergarten_id, user_id, staff_no, name, gender, rrn_encrypted,
                     rrn_first6, level, start_date, status, created_at, updated_at)
                SELECT ?, user_id, ?, 'Assignment Teacher', 'MALE'::gender_enum, 'enc',
                       '900101', 'TEACHER'::level_enum, DATE '2024-03-01',
                       'ACTIVE'::status_enum, NOW(), NOW()
                FROM users WHERE login_id = ?
                RETURNING teacher_id
                """,
                Long.class,
                KG,
                "STAFF-" + UUID.randomUUID().toString().substring(0, 8),
                LOGIN_ID);
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
                """, KG, classId, teacherId, start, end, status, LOGIN_ID);
    }

    private void clearTeacherArtifacts() {
        jdbc.update("""
                DELETE FROM class_teacher_assignments
                WHERE teacher_id IN (
                    SELECT teacher_id FROM teachers
                    WHERE user_id = (SELECT user_id FROM users WHERE login_id = ?))
                """, LOGIN_ID);
        jdbc.update("""
                DELETE FROM teachers
                WHERE user_id = (SELECT user_id FROM users WHERE login_id = ?)
                """, LOGIN_ID);
    }

    private void configureKindergartenRole(String role) {
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
                """, role, KG, LOGIN_ID);
        jdbc.update("""
                INSERT INTO user_kindergarten_memberships
                    (user_id, kindergarten_id, status, joined_at, created_at, updated_at)
                SELECT user_id, ?, 'ACTIVE', NOW(), NOW(), NOW()
                FROM users WHERE login_id = ?
                """, KG, LOGIN_ID);
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
