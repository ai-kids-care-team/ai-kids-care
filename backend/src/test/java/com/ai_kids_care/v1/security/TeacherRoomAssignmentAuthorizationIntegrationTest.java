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
 * Teacher room access is narrowed along the assignment chain (ADR-0019 §7): a TEACHER
 * may only see rooms that an ACTIVE class_room_assignment links to a class the teacher
 * covers through an ACTIVE class_teacher_assignment, with both assignment windows
 * containing now. A KINDERGARTEN_ADMIN keeps kindergarten-wide room access. Rooms
 * outside the teacher's assignment chain are hidden with a 404.
 *
 * Fresh class/room/assignment rows are created so the teacher's visible set is exactly
 * one controlled room; seeded rooms are genuine-but-unassigned for this principal.
 */
@AutoConfigureMockMvc
class TeacherRoomAssignmentAuthorizationIntegrationTest extends BaseIntegrationTest {

    private static final String LOGIN_ID = "teacher-room-test-user";
    private static final String PASSWORD = "Test@Room2024";
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
                LOGIN_ID, LOGIN_ID + "@test-room.internal", "010-0000-9994",
                passwordEncoder.encode(PASSWORD));
        clearTeacherArtifacts();
    }

    @Test
    void teacherSeesOnlyRoomsOfActivelyAssignedClasses() throws Exception {
        long classId = createClass();
        long room = createRoom();
        long teacherId = createActiveTeacher();
        assignTeacherToClass(teacherId, classId);
        assignClassToRoom(classId, room, "ACTIVE");
        long unassignedSeedRoom = aSeededRoomOtherThan(room);
        configureKindergartenRole("TEACHER");
        Cookie session = login();

        assertThat(listedRoomCount(session)).isEqualTo(1);
        mockMvc.perform(get("/api/v1/rooms/{id}", room).cookie(session))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/rooms/{id}", unassignedSeedRoom).cookie(session))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Resource not found"));
    }

    @Test
    void teacherWithDisabledRoomLinkSeesNoRooms() throws Exception {
        long classId = createClass();
        long room = createRoom();
        long teacherId = createActiveTeacher();
        assignTeacherToClass(teacherId, classId);
        assignClassToRoom(classId, room, "DISABLED");
        configureKindergartenRole("TEACHER");
        Cookie session = login();

        assertThat(listedRoomCount(session)).isZero();
        mockMvc.perform(get("/api/v1/rooms/{id}", room).cookie(session))
                .andExpect(status().isNotFound());
    }

    @Test
    void kindergartenAdminSeesAllKindergartenRooms() throws Exception {
        long room = createRoom();
        configureKindergartenRole("KINDERGARTEN_ADMIN");
        Cookie session = login();

        long total = kindergartenRoomCount();
        assertThat(total).isGreaterThan(1);
        assertThat(listedRoomCount(session)).isEqualTo((int) total);
        mockMvc.perform(get("/api/v1/rooms/{id}", room).cookie(session))
                .andExpect(status().isOk());
    }

    private long createClass() {
        return jdbc.queryForObject("""
                INSERT INTO classes
                    (kindergarten_id, name, grade, academic_year, start_date, end_date,
                     status, created_at, updated_at)
                VALUES (?, 'Room-chain Class', 'AGE_5', 2026, DATE '2026-03-01',
                        DATE '2027-02-28', 'ACTIVE'::status_enum, NOW(), NOW())
                RETURNING class_id
                """, Long.class, KG);
    }

    private long createRoom() {
        return jdbc.queryForObject("""
                INSERT INTO rooms (kindergarten_id, name, room_type, status, created_at, updated_at)
                VALUES (?, 'Room-chain Room', '교실', 'ACTIVE'::status_enum, NOW(), NOW())
                RETURNING room_id
                """, Long.class, KG);
    }

    private long aSeededRoomOtherThan(long room) {
        return jdbc.queryForObject(
                "SELECT room_id FROM rooms WHERE kindergarten_id = ? AND room_id <> ? ORDER BY room_id LIMIT 1",
                Long.class, KG, room);
    }

    private long kindergartenRoomCount() {
        return jdbc.queryForObject(
                "SELECT count(*) FROM rooms WHERE kindergarten_id = ?", Long.class, KG);
    }

    private int listedRoomCount(Cookie session) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/rooms").cookie(session))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("content")
                .size();
    }

    private long createActiveTeacher() {
        String staffSuffix = UUID.randomUUID().toString().substring(0, 8);
        return jdbc.queryForObject("""
                INSERT INTO teachers
                    (kindergarten_id, user_id, staff_no, name, gender, rrn_hash,
                     rrn_first6, level, start_date, status, created_at, updated_at)
                SELECT ?, user_id, ?, 'Room Teacher', 'MALE'::gender_enum, ?,
                       '900101', 'TEACHER'::level_enum, DATE '2024-03-01',
                       'ACTIVE'::status_enum, NOW(), NOW()
                FROM users WHERE login_id = ?
                RETURNING teacher_id
                """,
                Long.class,
                KG,
                "STAFF-" + staffSuffix,
                "FIXTURE-HASH-" + staffSuffix,
                LOGIN_ID);
    }

    private void assignTeacherToClass(long teacherId, long classId) {
        jdbc.update("""
                INSERT INTO class_teacher_assignments
                    (kindergarten_id, class_id, teacher_id, role, start_date, end_date,
                     status, created_by_user_id, created_at, updated_at)
                SELECT ?, ?, ?, 'HOMEROOM'::class_teacher_role_enum,
                       DATE '2024-03-01', NULL, 'ACTIVE'::status_enum, user_id, NOW(), NOW()
                FROM users WHERE login_id = ?
                """, KG, classId, teacherId, LOGIN_ID);
    }

    private void assignClassToRoom(long classId, long roomId, String status) {
        jdbc.update("""
                INSERT INTO class_room_assignments
                    (kindergarten_id, class_id, room_id, start_at, end_at, status,
                     created_by_user_id, created_at, updated_at)
                SELECT ?, ?, ?, NOW() - INTERVAL '1 day', NULL, ?::status_enum,
                       user_id, NOW(), NOW()
                FROM users WHERE login_id = ?
                """, KG, classId, roomId, status, LOGIN_ID);
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
