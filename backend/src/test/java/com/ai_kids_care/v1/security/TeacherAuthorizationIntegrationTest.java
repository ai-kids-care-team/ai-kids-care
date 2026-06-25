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
 * GT-1 / SEC-07: the teacher roster read endpoints must enforce the coarse
 * {@code TENANT_S2_READ} gate (KINDERGARTEN_ADMIN + TEACHER) plus a mandatory
 * active-kindergarten filter. A principal sees only same-kindergarten teachers;
 * cross-tenant teachers are hidden with a 404; roles outside the gate (e.g.
 * GUARDIAN) are rejected; and an authenticated principal without an active
 * kindergarten role context cannot read at all.
 *
 * Unlike Class/Room reads, the teacher roster is intentionally NOT narrowed by
 * assignment — a TEACHER may view colleagues across their own kindergarten.
 *
 * Fresh teacher rows are created in the principal's kindergarten (KG 1) and in a
 * different kindergarten (KG 2) so the visibility boundary is asserted on rows this
 * test fully controls, independent of seed data.
 */
@AutoConfigureMockMvc
class TeacherAuthorizationIntegrationTest extends BaseIntegrationTest {

    private static final String LOGIN_ID = "teacher-roster-test-user";
    private static final String PASSWORD = "Test@Roster2024";
    private static final long HOME_KG = 1L;
    private static final long OTHER_KG = 2L;

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private ObjectMapper objectMapper;

    @BeforeEach
    void upsertPrincipalAndClearFixtures() {
        jdbc.update("""
                INSERT INTO users (login_id, email, phone, password_hash, status, created_at, updated_at)
                VALUES (?, ?, ?, ?, 'ACTIVE', NOW(), NOW())
                ON CONFLICT (login_id) DO UPDATE
                    SET password_hash = EXCLUDED.password_hash, status = 'ACTIVE'
                """,
                LOGIN_ID, LOGIN_ID + "@test-roster.internal", "010-0000-9993",
                passwordEncoder.encode(PASSWORD));
        clearFixtureTeachers();
    }

    // ---- (d) same-kindergarten roster is readable by a TEACHER ----

    @Test
    void teacherReadsSameKindergartenColleagueListAndDetail() throws Exception {
        long sameKgTeacher = createTeacher(HOME_KG, "Colleague Alpha");
        configureKindergartenRole("TEACHER", HOME_KG);
        Cookie session = login();

        // list contains the same-kindergarten teacher
        MvcResult list = mockMvc.perform(get("/api/v1/teachers").cookie(session))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(list.getResponse().getContentAsString()).contains("Colleague Alpha");

        // detail of a same-kindergarten teacher is visible
        mockMvc.perform(get("/api/v1/teachers/{id}", sameKgTeacher).cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kindergartenId").value((int) HOME_KG))
                .andExpect(jsonPath("$.name").value("Colleague Alpha"));
    }

    // ---- (a) cross-tenant isolation: list excludes other-KG teacher; detail 404 ----

    @Test
    void crossTenantTeacherIsHiddenFromListAndDetail() throws Exception {
        long sameKgTeacher = createTeacher(HOME_KG, "Home Teacher");
        long otherKgTeacher = createTeacher(OTHER_KG, "Foreign Teacher");
        configureKindergartenRole("TEACHER", HOME_KG);
        Cookie session = login();

        String body = mockMvc.perform(get("/api/v1/teachers").cookie(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(body).contains("Home Teacher");
        assertThat(body).doesNotContain("Foreign Teacher");
        assertThat(body).doesNotContain(String.valueOf(otherKgTeacher));

        // detail of cross-tenant teacher leaks nothing — 404, not 200
        mockMvc.perform(get("/api/v1/teachers/{id}", otherKgTeacher).cookie(session))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Resource not found"));

        // sanity: home teacher detail still reachable
        mockMvc.perform(get("/api/v1/teachers/{id}", sameKgTeacher).cookie(session))
                .andExpect(status().isOk());
    }

    @Test
    void byUserLookupIsTenantScoped() throws Exception {
        long otherKgTeacher = createTeacher(OTHER_KG, "Foreign ByUser");
        long otherUserId = userIdOfTeacher(otherKgTeacher);
        configureKindergartenRole("TEACHER", HOME_KG);
        Cookie session = login();

        // a teacher belonging to a different kindergarten is not resolvable by userId
        mockMvc.perform(get("/api/v1/teachers/by-user/{userId}", otherUserId).cookie(session))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Resource not found"));
    }

    // ---- (c) role outside the TENANT_S2_READ gate is rejected ----

    @Test
    void guardianRoleIsForbidden() throws Exception {
        createTeacher(HOME_KG, "Hidden Teacher");
        configureKindergartenRole("GUARDIAN", HOME_KG);
        Cookie session = login();

        mockMvc.perform(get("/api/v1/teachers").cookie(session))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/teachers/1").cookie(session))
                .andExpect(status().isForbidden());
    }

    // ---- (b) authenticated but no active kindergarten (tenant) context is rejected ----

    @Test
    void authenticatedWithoutActiveTenantContextIsRejected() throws Exception {
        createTeacher(HOME_KG, "Hidden Teacher 2");
        // A PLATFORM_IT_ADMIN logs in successfully but has scopeType=PLATFORM and a null
        // activeKindergartenId, so it lacks the tenant identity the gate requires.
        // (A role-less user cannot be used here: identity resolution requires exactly one
        // active role assignment, so login itself would fail with 401, not the endpoint.)
        configurePlatformRole();
        Cookie session = login();

        mockMvc.perform(get("/api/v1/teachers").cookie(session))
                .andExpect(status().isForbidden());
    }

    @Test
    void unauthenticatedIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/teachers"))
                .andExpect(status().isUnauthorized());
    }

    // ---------------------------------------------------------------- helpers

    private long createTeacher(long kindergartenId, String name) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        // Dedicated user per fixture teacher (teachers.user_id is unique).
        Long userId = jdbc.queryForObject("""
                INSERT INTO users (login_id, email, phone, password_hash, status, created_at, updated_at)
                VALUES (?, ?, ?, 'x', 'ACTIVE', NOW(), NOW())
                RETURNING user_id
                """,
                Long.class,
                "fixture-teacher-" + suffix,
                "fixture-teacher-" + suffix + "@test-roster.internal",
                "010-00-" + suffix.substring(0, 6));
        return jdbc.queryForObject("""
                INSERT INTO teachers
                    (kindergarten_id, user_id, staff_no, name, gender, rrn_hash,
                     rrn_first6, level, start_date, status, created_at, updated_at)
                VALUES (?, ?, ?, ?, 'FEMALE'::gender_enum, ?, '900101',
                        'TEACHER'::level_enum, DATE '2024-03-01', 'ACTIVE'::status_enum,
                        NOW(), NOW())
                RETURNING teacher_id
                """,
                Long.class,
                kindergartenId, userId, "STAFF-" + suffix, name, "FIXTURE-HASH-" + suffix);
    }

    private long userIdOfTeacher(long teacherId) {
        return jdbc.queryForObject(
                "SELECT user_id FROM teachers WHERE teacher_id = ?", Long.class, teacherId);
    }

    private void clearFixtureTeachers() {
        jdbc.update("""
                DELETE FROM teachers
                WHERE user_id IN (SELECT user_id FROM users WHERE login_id LIKE 'fixture-teacher-%')
                """);
        jdbc.update("DELETE FROM users WHERE login_id LIKE 'fixture-teacher-%'");
    }

    private void configureKindergartenRole(String role, long kindergartenId) {
        clearRoleContext();
        jdbc.update("""
                INSERT INTO user_role_assignments
                    (user_id, role, scope_type, scope_id, status, granted_at)
                SELECT user_id, ?::user_role_enum, 'KINDERGARTEN', ?, 'ACTIVE', NOW()
                FROM users WHERE login_id = ?
                """, role, kindergartenId, LOGIN_ID);
        jdbc.update("""
                INSERT INTO user_kindergarten_memberships
                    (user_id, kindergarten_id, status, joined_at, created_at, updated_at)
                SELECT user_id, ?, 'ACTIVE', NOW(), NOW(), NOW()
                FROM users WHERE login_id = ?
                """, kindergartenId, LOGIN_ID);
    }

    private void configurePlatformRole() {
        clearRoleContext();
        jdbc.update("""
                INSERT INTO user_role_assignments
                    (user_id, role, scope_type, scope_id, status, granted_at)
                SELECT user_id, 'PLATFORM_IT_ADMIN'::user_role_enum, 'PLATFORM', NULL, 'ACTIVE', NOW()
                FROM users WHERE login_id = ?
                """, LOGIN_ID);
    }

    private void clearRoleContext() {
        jdbc.update("""
                DELETE FROM user_kindergarten_memberships
                WHERE user_id = (SELECT user_id FROM users WHERE login_id = ?)
                """, LOGIN_ID);
        jdbc.update("""
                DELETE FROM user_role_assignments
                WHERE user_id = (SELECT user_id FROM users WHERE login_id = ?)
                """, LOGIN_ID);
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
