package com.ai_kids_care.v1.graph;

import com.ai_kids_care.GraphIntegrationTest;
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

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP contract + real-Cypher tenant-isolation tests for the relationship-graph read API
 * (activate-graph-service-read-api). A minimal two-tenant graph fixture is seeded into a Neo4j
 * Testcontainer (each tenant: one kindergarten + teacher + class + child + guardian), keyed to two
 * real kindergartens from the PG seed so that the active tenant context (from login) matches the
 * graph node {@code kindergarten_id}.
 *
 * <p>Covers: own-tenant 200 with correct VO fields and no PII; cross-tenant 404 == non-existent 404
 * (existence hidden); wrong role (GUARDIAN) 403; anonymous 401; teacher-centric path same isolation.
 */
@AutoConfigureMockMvc
class GraphReadApiTest extends GraphIntegrationTest {

    private static final String PW = "Test@Graph2026";
    private static final String ADMIN_A = "graph-admin-a";
    private static final String TEACHER_A = "graph-teacher-a";
    private static final String GUARDIAN_A = "graph-guardian-a";
    private static final String ADMIN_B = "graph-admin-b";

    // Synthetic graph ids — independent of PG ids; only kindergarten_id ties a node to a tenant.
    private static final long CHILD_A = 9_000_001L, CLASS_A = 9_000_011L, TEACHER_NODE_A = 9_000_021L, GUARDIAN_A_ID = 9_000_031L;
    private static final long CHILD_B = 9_000_002L, CLASS_B = 9_000_012L, TEACHER_NODE_B = 9_000_022L, GUARDIAN_B_ID = 9_000_032L;
    private static final long ABSENT_CHILD = 9_999_999L;
    private static final long ABSENT_TEACHER = 9_999_998L;

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private ObjectMapper objectMapper;

    private long kgA;
    private long kgB;

    @BeforeEach
    void setUp() {
        kgA = jdbc.queryForObject(
                "SELECT kindergarten_id FROM kindergartens ORDER BY kindergarten_id LIMIT 1", Long.class);
        kgB = jdbc.queryForObject(
                "SELECT kindergarten_id FROM kindergartens WHERE kindergarten_id <> ? ORDER BY kindergarten_id LIMIT 1",
                Long.class, kgA);

        seedTenantUser(ADMIN_A, "graph-admin-a@test.local", "KINDERGARTEN_ADMIN", kgA);
        seedTenantUser(TEACHER_A, "graph-teacher-a@test.local", "TEACHER", kgA);
        seedTenantUser(GUARDIAN_A, "graph-guardian-a@test.local", "GUARDIAN", kgA);
        seedTenantUser(ADMIN_B, "graph-admin-b@test.local", "KINDERGARTEN_ADMIN", kgB);

        clearGraph();
        seedGraphTenant(kgA, "유치원A", TEACHER_NODE_A, "김교사", CLASS_A, "햇님반", CHILD_A, "아이A", "A-001", GUARDIAN_A_ID, "엄마A");
        seedGraphTenant(kgB, "유치원B", TEACHER_NODE_B, "이교사", CLASS_B, "달님반", CHILD_B, "아이B", "B-001", GUARDIAN_B_ID, "엄마B");
    }

    // ── child graph ──────────────────────────────────────────────────────────────

    @Test
    void childGraph_ownTenantAdmin_returns200WithFields() throws Exception {
        Cookie admin = login(ADMIN_A);
        mockMvc.perform(get("/api/v1/graph/children/{id}", CHILD_A).cookie(admin))
                .andExpect(status().isOk())
                // cast to int: JsonPath reads in-range JSON integers as Integer; value(Long) would mismatch.
                .andExpect(jsonPath("$.child.childId").value((int) CHILD_A))
                .andExpect(jsonPath("$.child.name").value("아이A"))
                .andExpect(jsonPath("$.child.childNo").value("A-001"))
                .andExpect(jsonPath("$.classInfo.classId").value((int) CLASS_A))
                .andExpect(jsonPath("$.teacher.teacherId").value((int) TEACHER_NODE_A))
                .andExpect(jsonPath("$.kindergarten.kindergartenId").value((int) kgA))
                .andExpect(jsonPath("$.guardians[0].guardianId").value((int) GUARDIAN_A_ID))
                .andExpect(jsonPath("$.guardians[0].relationship").value("MOTHER"));
    }

    @Test
    void childGraph_response_carriesNoPii() throws Exception {
        Cookie teacher = login(TEACHER_A);
        MvcResult res = mockMvc.perform(get("/api/v1/graph/children/{id}", CHILD_A).cookie(teacher))
                .andExpect(status().isOk())
                .andReturn();
        String body = res.getResponse().getContentAsString();
        assertThat(body).doesNotContainIgnoringCase("rrn");
        assertThat(body).doesNotContainIgnoringCase("birth");
        assertThat(body).doesNotContainIgnoringCase("address");
        assertThat(body).doesNotContainIgnoringCase("phone");
        assertThat(body).doesNotContainIgnoringCase("email");
        assertThat(body).doesNotContainIgnoringCase("password");
    }

    @Test
    void childGraph_teacher_returns200() throws Exception {
        Cookie teacher = login(TEACHER_A);
        mockMvc.perform(get("/api/v1/graph/children/{id}", CHILD_A).cookie(teacher))
                .andExpect(status().isOk());
    }

    @Test
    void childGraph_crossTenant_returns404() throws Exception {
        // Admin of kindergarten A requests a child that exists only in kindergarten B.
        Cookie adminA = login(ADMIN_A);
        mockMvc.perform(get("/api/v1/graph/children/{id}", CHILD_B).cookie(adminA))
                .andExpect(status().isNotFound());
    }

    @Test
    void childGraph_nonExistent_returns404_sameAsCrossTenant() throws Exception {
        Cookie adminA = login(ADMIN_A);
        mockMvc.perform(get("/api/v1/graph/children/{id}", ABSENT_CHILD).cookie(adminA))
                .andExpect(status().isNotFound());
    }

    @Test
    void childGraph_guardian_returns403() throws Exception {
        Cookie guardian = login(GUARDIAN_A);
        mockMvc.perform(get("/api/v1/graph/children/{id}", CHILD_A).cookie(guardian))
                .andExpect(status().isForbidden());
    }

    @Test
    void childGraph_anonymous_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/graph/children/{id}", CHILD_A))
                .andExpect(status().isUnauthorized());
    }

    // ── teacher graph ────────────────────────────────────────────────────────────

    @Test
    void teacherGraph_ownTenant_returns200WithClassesAndChildren() throws Exception {
        Cookie admin = login(ADMIN_A);
        mockMvc.perform(get("/api/v1/graph/teachers/{id}", TEACHER_NODE_A).cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.teacher.teacherId").value((int) TEACHER_NODE_A))
                .andExpect(jsonPath("$.kindergarten.kindergartenId").value((int) kgA))
                .andExpect(jsonPath("$.classes[0].classInfo.classId").value((int) CLASS_A))
                .andExpect(jsonPath("$.classes[0].children[0].childId").value((int) CHILD_A));
    }

    @Test
    void teacherGraph_crossTenant_returns404() throws Exception {
        Cookie adminA = login(ADMIN_A);
        mockMvc.perform(get("/api/v1/graph/teachers/{id}", TEACHER_NODE_B).cookie(adminA))
                .andExpect(status().isNotFound());
    }

    @Test
    void teacherGraph_nonExistent_returns404() throws Exception {
        Cookie adminA = login(ADMIN_A);
        mockMvc.perform(get("/api/v1/graph/teachers/{id}", ABSENT_TEACHER).cookie(adminA))
                .andExpect(status().isNotFound());
    }

    @Test
    void teacherGraph_anonymous_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/graph/teachers/{id}", TEACHER_NODE_A))
                .andExpect(status().isUnauthorized());
    }

    // ── helpers ──────────────────────────────────────────────────────────────────

    private void seedGraphTenant(long kgId, String kgName, long teacherId, String teacherName,
                                 long classId, String className, long childId, String childName,
                                 String childNo, long guardianId, String guardianName) {
        Map<String, Object> p = new HashMap<>();
        p.put("kgId", kgId);
        p.put("kgName", kgName);
        p.put("teacherId", teacherId);
        p.put("teacherName", teacherName);
        p.put("classId", classId);
        p.put("className", className);
        p.put("childId", childId);
        p.put("childName", childName);
        p.put("childNo", childNo);
        p.put("guardianId", guardianId);
        p.put("guardianName", guardianName);
        p.put("academicYear", 2026L);
        writeCypher("""
                CREATE (k:Kindergarten {kindergarten_id: $kgId, name: $kgName, status: 'ACTIVE'})
                CREATE (t:Teacher {teacher_id: $teacherId, kindergarten_id: $kgId, name: $teacherName, level: 'LEAD', status: 'ACTIVE'})
                CREATE (c:Class {class_id: $classId, kindergarten_id: $kgId, name: $className, grade: '만5세', academic_year: $academicYear, status: 'ACTIVE'})
                CREATE (ch:Child {child_id: $childId, kindergarten_id: $kgId, name: $childName, child_no: $childNo, gender: 'M', status: 'ACTIVE'})
                CREATE (g:Guardian {guardian_id: $guardianId, kindergarten_id: $kgId, name: $guardianName, gender: 'F', status: 'ACTIVE'})
                CREATE (k)-[:HAS_TEACHER]->(t)
                CREATE (t)-[:HAS_CLASS]->(c)
                CREATE (c)-[:HAS_CHILD]->(ch)
                CREATE (ch)-[:HAS_GUARDIAN {relationship: 'MOTHER', is_primary: true, priority: 1}]->(g)
                """, p);
    }

    private Cookie login(String loginId) throws Exception {
        MvcResult login = mockMvc.perform(withCsrf(post("/api/v1/auth/login"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("identifier", loginId, "password", PW))))
                .andExpect(status().isOk())
                .andReturn();
        return login.getResponse().getCookie("AI_KIDS_CARE_SESSION");
    }

    private MockHttpServletRequestBuilder withCsrf(MockHttpServletRequestBuilder request) throws Exception {
        MvcResult csrf = mockMvc.perform(get("/api/v1/auth/csrf")).andExpect(status().isOk()).andReturn();
        String token = objectMapper.readTree(csrf.getResponse().getContentAsString()).get("token").asText();
        return request.cookie(csrf.getResponse().getCookie("XSRF-TOKEN")).header("X-XSRF-TOKEN", token);
    }

    private void seedTenantUser(String loginId, String email, String role, long kgId) {
        // phone is left NULL: uq_user_account_phone is unique, the PG testcontainer is shared across
        // test classes (no rollback), and a synthetic phone like 010-0000-770x is also used by other
        // fixtures — a non-null value here collides. These graph reads don't need a phone; NULLs do
        // not conflict under a unique index in PostgreSQL.
        jdbc.update("""
                INSERT INTO users (login_id, email, phone, password_hash, status, created_at, updated_at)
                VALUES (?, ?, NULL, ?, 'ACTIVE', NOW(), NOW())
                ON CONFLICT (login_id) DO UPDATE SET password_hash = EXCLUDED.password_hash, status = 'ACTIVE'
                """, loginId, email, passwordEncoder.encode(PW));
        jdbc.update("DELETE FROM user_role_assignments WHERE user_id = (SELECT user_id FROM users WHERE login_id = ?)", loginId);
        jdbc.update("DELETE FROM user_kindergarten_memberships WHERE user_id = (SELECT user_id FROM users WHERE login_id = ?)", loginId);
        jdbc.update("""
                INSERT INTO user_role_assignments (user_id, role, scope_type, scope_id, status, granted_at)
                SELECT user_id, ?::user_role_enum, 'KINDERGARTEN', ?, 'ACTIVE', NOW() FROM users WHERE login_id = ?
                """, role, kgId, loginId);
        jdbc.update("""
                INSERT INTO user_kindergarten_memberships (user_id, kindergarten_id, status, joined_at, created_at, updated_at)
                SELECT user_id, ?, 'ACTIVE', NOW(), NOW(), NOW() FROM users WHERE login_id = ?
                """, kgId, loginId);
    }
}
