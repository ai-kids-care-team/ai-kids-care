package com.ai_kids_care.v1.security;

import com.ai_kids_care.BaseIntegrationTest;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AN-READ：公告平台级广读授权集成测试。
 *
 * 验收：
 * - 任意已认证用户（GUARDIAN / TEACHER / KINDERGARTEN_ADMIN）可读 ACTIVE 公告列表（200）和详情（200）。
 * - TEACHER 可读公告——证明平台广读，不受 membership 过滤。
 * - 未认证 → 401。
 * - KINDERGARTEN_ADMIN POST/PUT/DELETE → 403（写操作需 PLATFORM_IT_ADMIN）。
 * - PLATFORM_IT_ADMIN POST → 201。
 * - 读取不存在 ID → 404。
 *
 * Phone 空间：010-0910-*（不与其他测试类冲突）。
 */
@AutoConfigureMockMvc
class AnnouncementAuthorizationIntegrationTest extends BaseIntegrationTest {

    private static final String PASSWORD = "Announce@Test2026";
    private static final long OWN_KG = 1L;

    private static final String GUARDIAN_LOGIN       = "an-guardian";
    private static final String TEACHER_LOGIN        = "an-teacher";
    private static final String KG_ADMIN_LOGIN       = "an-kg-admin";
    private static final String PLATFORM_ADMIN_LOGIN = "an-platform-admin";

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private ObjectMapper objectMapper;

    // announcement IDs created per test — cleaned up in @AfterEach
    private final List<Long> createdAnnouncementIds = new ArrayList<>();

    @BeforeEach
    void setUpActors() {
        // Phone 空间 010-0910-*
        upsertUser(GUARDIAN_LOGIN, "010-0910-0001");
        setGuardianIdentity(GUARDIAN_LOGIN, OWN_KG);

        upsertUser(TEACHER_LOGIN, "010-0910-0002");
        setTeacherIdentity(TEACHER_LOGIN, OWN_KG);

        upsertUser(KG_ADMIN_LOGIN, "010-0910-0003");
        setKindergartenAdminIdentity(KG_ADMIN_LOGIN, OWN_KG);

        upsertUser(PLATFORM_ADMIN_LOGIN, "010-0910-0004");
        setPlatformRole(PLATFORM_ADMIN_LOGIN, "PLATFORM_IT_ADMIN");
    }

    @AfterEach
    void cleanUpAnnouncements() {
        for (Long annId : createdAnnouncementIds) {
            jdbc.update("DELETE FROM announcements WHERE id = ?", annId);
        }
        createdAnnouncementIds.clear();
    }

    // ── 读操作：已认证用户可见 ACTIVE 公告 ─────────────────────────────────────

    @Test
    void listAnnouncements_guardian_returns200() throws Exception {
        long annId = insertAnnouncement("Guardian Test Title", "Body");
        Cookie session = login(GUARDIAN_LOGIN);
        mockMvc.perform(get("/api/v1/announcements").cookie(session))
                .andExpect(status().isOk());
    }

    @Test
    void getAnnouncement_guardian_returns200() throws Exception {
        long annId = insertAnnouncement("Guardian Detail Title", "Body");
        Cookie session = login(GUARDIAN_LOGIN);
        mockMvc.perform(get("/api/v1/announcements/{id}", annId).cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(annId))
                .andExpect(jsonPath("$.title").value("Guardian Detail Title"));
    }

    @Test
    void getAnnouncement_teacher_returns200_platformWideRead() throws Exception {
        // TEACHER 可读——证明平台广读，不受 membership 过滤
        long annId = insertAnnouncement("Teacher Visible Title", "Body");
        Cookie session = login(TEACHER_LOGIN);
        mockMvc.perform(get("/api/v1/announcements/{id}", annId).cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(annId));
    }

    @Test
    void getAnnouncement_kindergartenAdmin_returns200() throws Exception {
        long annId = insertAnnouncement("KG Admin Visible Title", "Body");
        Cookie session = login(KG_ADMIN_LOGIN);
        mockMvc.perform(get("/api/v1/announcements/{id}", annId).cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(annId));
    }

    // ── 未认证 → 401 ─────────────────────────────────────────────────────────

    @Test
    void announcements_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/announcements"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/announcements/1"))
                .andExpect(status().isUnauthorized());
    }

    // ── 写操作：KINDERGARTEN_ADMIN → 403 ─────────────────────────────────────

    @Test
    void createAnnouncement_kindergartenAdmin_returns403() throws Exception {
        Cookie session = login(KG_ADMIN_LOGIN);
        mockMvc.perform(withRealCsrf(post("/api/v1/announcements"))
                        .cookie(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "KG Admin Should Fail",
                                "body", "Body",
                                "isPinned", false,
                                "status", "ACTIVE"
                        ))))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateAnnouncement_kindergartenAdmin_returns403() throws Exception {
        long annId = insertAnnouncement("Update Target", "Body");
        Cookie session = login(KG_ADMIN_LOGIN);
        mockMvc.perform(withRealCsrf(put("/api/v1/announcements/{id}", annId))
                        .cookie(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "Updated Title",
                                "body", "Updated Body",
                                "isPinned", false,
                                "status", "ACTIVE"
                        ))))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteAnnouncement_kindergartenAdmin_returns403() throws Exception {
        long annId = insertAnnouncement("Delete Target", "Body");
        Cookie session = login(KG_ADMIN_LOGIN);
        mockMvc.perform(withRealCsrf(delete("/api/v1/announcements/{id}", annId))
                        .cookie(session))
                .andExpect(status().isForbidden());
    }

    // ── 写操作：PLATFORM_IT_ADMIN POST → 201 ─────────────────────────────────

    @Test
    void createAnnouncement_platformAdmin_returns201() throws Exception {
        Cookie session = login(PLATFORM_ADMIN_LOGIN);
        MvcResult result = mockMvc.perform(withRealCsrf(post("/api/v1/announcements"))
                        .cookie(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "Platform Admin Created",
                                "body", "Body Content",
                                "isPinned", false,
                                "status", "ACTIVE"
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Platform Admin Created"))
                .andReturn();

        // 追踪新建公告 ID 以便清理
        Long newId = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("id").asLong();
        createdAnnouncementIds.add(newId);
    }

    // ── 不存在 ID → 404 ────────────────────────────────────────────────────

    @Test
    void getAnnouncement_nonExistentId_returns404() throws Exception {
        Cookie session = login(GUARDIAN_LOGIN);
        mockMvc.perform(get("/api/v1/announcements/999999999").cookie(session))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Resource not found"));
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private void upsertUser(String loginId, String phone) {
        String hash = passwordEncoder.encode(PASSWORD);
        jdbc.update("""
                INSERT INTO users (login_id, email, phone, password_hash, status, created_at, updated_at)
                VALUES (?, ?, ?, ?, 'ACTIVE', NOW(), NOW())
                ON CONFLICT (login_id) DO UPDATE
                    SET password_hash = EXCLUDED.password_hash, status = 'ACTIVE'
                """, loginId, loginId + "@an-test.internal", phone, hash);
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
                SELECT ?, user_id, ?, encode(sha256(convert_to(login_id, 'UTF8')), 'hex'), '000101', 'MALE', 'Test address', 'ACTIVE', NOW(), NOW()
                FROM users WHERE login_id = ?
                ON CONFLICT (user_id) DO UPDATE
                    SET status = 'ACTIVE', kindergarten_id = EXCLUDED.kindergarten_id
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

    private void clearRoleAndMembership(String loginId) {
        jdbc.update("DELETE FROM user_kindergarten_memberships WHERE user_id = "
                + "(SELECT user_id FROM users WHERE login_id = ?)", loginId);
        jdbc.update("DELETE FROM user_role_assignments WHERE user_id = "
                + "(SELECT user_id FROM users WHERE login_id = ?)", loginId);
    }

    /**
     * Insert a test announcement row. Author is set to the PLATFORM_IT_ADMIN user
     * (any valid user_id satisfies the author_id NOT NULL FK).
     * Returns the generated announcement ID.
     */
    private long insertAnnouncement(String title, String body) {
        long authorId = jdbc.queryForObject(
                "SELECT user_id FROM users WHERE login_id = ?", Long.class, PLATFORM_ADMIN_LOGIN);
        Long annId = jdbc.queryForObject("""
                INSERT INTO announcements
                    (author_id, title, body, is_pinned, status, view_count, created_at, updated_at)
                VALUES (?, ?, ?, false, 'ACTIVE'::status_enum, 0, NOW(), NOW())
                RETURNING id
                """, Long.class, authorId, title, body);
        createdAnnouncementIds.add(annId);
        return annId;
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
