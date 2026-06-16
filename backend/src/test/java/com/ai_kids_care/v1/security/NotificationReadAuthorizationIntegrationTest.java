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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SPEC-0001 / ADR-0018 A3d：通知读取 tenant-scoped 集成测试。
 *
 * 验收：
 * - 受体（GUARDIAN）只能看到发给自己的通知（列表 + 详情）。
 * - 另一受体的通知（同租户）→ 对当前受体隐藏 404 + AUTHORIZATION_DENIED 审计。
 * - KINDERGARTEN_ADMIN 可见其园的全部通知（含其他人的）及详情 → 200。
 * - 跨租户通知 → 受体 404、Admin 404。
 * - 未认证 → 401。
 * - 响应只含最小字段（notificationId/title/body/status/createdAt），
 *   不含 channel/dedupeKey/sentAt/failReason/retryCount/recipientUserId/kindergartenId。
 */
@AutoConfigureMockMvc
class NotificationReadAuthorizationIntegrationTest extends BaseIntegrationTest {

    private static final String PASSWORD = "Notif@Test2026";
    private static final long OWN_KG = 1L;

    // actor login IDs
    private static final String RECIPIENT_LOGIN = "nr-recipient";
    private static final String OTHER_LOGIN = "nr-other";
    private static final String ADMIN_LOGIN = "nr-admin";

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private ObjectMapper objectMapper;

    // notification ids inserted per test — cleaned up in @AfterEach
    private final List<Long> createdNotificationIds = new ArrayList<>();

    @BeforeEach
    void setUpActors() {
        // 唯一 phone：避开 TeacherChild(010-0800-*) / GuardianChild(010-0700-*)——users.phone 唯一、共享容器。
        upsertUser(RECIPIENT_LOGIN, "010-0905-0001");
        setGuardianIdentity(RECIPIENT_LOGIN, OWN_KG);

        upsertUser(OTHER_LOGIN, "010-0905-0002");
        setGuardianIdentity(OTHER_LOGIN, OWN_KG);

        upsertUser(ADMIN_LOGIN, "010-0905-0003");
        setKindergartenAdminIdentity(ADMIN_LOGIN, OWN_KG);
    }

    @AfterEach
    void cleanUpNotifications() {
        for (Long notifId : createdNotificationIds) {
            jdbc.update("DELETE FROM audit_logs WHERE resource_id = ? AND resource_type = 'NOTIFICATION'", notifId);
            jdbc.update("DELETE FROM notifications WHERE notification_id = ?", notifId);
        }
        createdNotificationIds.clear();
    }

    // ── 列表：受体只能看到发给自己的通知 ────────────────────────────────────────────

    @Test
    void listNotifications_recipientSeesOnlyOwnNotifications() throws Exception {
        long recipientUserId = userIdOf(RECIPIENT_LOGIN);
        long otherUserId = userIdOf(OTHER_LOGIN);
        long ownNotifId = insertNotification(OWN_KG, recipientUserId, "Own Title", "Own Body");
        insertNotification(OWN_KG, otherUserId, "Other Title", "Other Body");  // 他人通知（应不可见）

        Cookie session = login(RECIPIENT_LOGIN);
        mockMvc.perform(get("/api/v1/notifications").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].notificationId").value(ownNotifId))
                .andExpect(jsonPath("$[0].title").value("Own Title"))
                .andExpect(jsonPath("$[0].body").value("Own Body"))
                .andExpect(jsonPath("$[0].status").exists())
                .andExpect(jsonPath("$[0].createdAt").exists())
                // 不含内部/S0 字段
                .andExpect(jsonPath("$[0].channel").doesNotExist())
                .andExpect(jsonPath("$[0].dedupeKey").doesNotExist())
                .andExpect(jsonPath("$[0].sentAt").doesNotExist())
                .andExpect(jsonPath("$[0].failReason").doesNotExist())
                .andExpect(jsonPath("$[0].retryCount").doesNotExist())
                .andExpect(jsonPath("$[0].recipientUserId").doesNotExist())
                .andExpect(jsonPath("$[0].kindergartenId").doesNotExist());
        // $.length()==1 + $[0].notificationId==ownNotifId 已证明他人通知不在列表中。
    }

    // ── 詳情：受体读自己的通知 → 200 最小字段 ──────────────────────────────────────

    @Test
    void getNotification_ownNotification_returns200WithMinimalFields() throws Exception {
        long recipientUserId = userIdOf(RECIPIENT_LOGIN);
        long notifId = insertNotification(OWN_KG, recipientUserId, "My Notification", "My Body");

        Cookie session = login(RECIPIENT_LOGIN);
        mockMvc.perform(get("/api/v1/notifications/{id}", notifId).cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notificationId").value(notifId))
                .andExpect(jsonPath("$.title").value("My Notification"))
                .andExpect(jsonPath("$.body").value("My Body"))
                .andExpect(jsonPath("$.status").exists())
                .andExpect(jsonPath("$.createdAt").exists())
                // 不含内部/S0 字段
                .andExpect(jsonPath("$.channel").doesNotExist())
                .andExpect(jsonPath("$.dedupeKey").doesNotExist())
                .andExpect(jsonPath("$.sentAt").doesNotExist())
                .andExpect(jsonPath("$.failReason").doesNotExist())
                .andExpect(jsonPath("$.retryCount").doesNotExist())
                .andExpect(jsonPath("$.recipientUserId").doesNotExist())
                .andExpect(jsonPath("$.kindergartenId").doesNotExist());
    }

    // ── 詳情：受体读他人通知（同租户）→ 隐藏 404 + 审计 DENIED ─────────────────────

    @Test
    void getNotification_otherRecipientSameTenant_returnsHidden404AndAuditsDenied() throws Exception {
        long otherUserId = userIdOf(OTHER_LOGIN);
        long otherNotifId = insertNotification(OWN_KG, otherUserId, "Other Title", "Other Body");

        Cookie session = login(RECIPIENT_LOGIN);
        MvcResult result = mockMvc.perform(get("/api/v1/notifications/{id}", otherNotifId).cookie(session))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Resource not found"))
                .andReturn();

        String correlationId = result.getResponse().getHeader("X-Correlation-Id");
        assertThat(correlationId).isNotBlank();
        Integer denied = jdbc.queryForObject(
                "SELECT count(*) FROM audit_logs "
                        + "WHERE correlation_id = ? AND action = 'AUTHORIZATION_DENIED' "
                        + "AND result = 'DENIED' AND resource_type = 'NOTIFICATION' "
                        + "AND resource_id = ? AND user_id = ?",
                Integer.class, correlationId, otherNotifId, userIdOf(RECIPIENT_LOGIN));
        assertThat(denied).isEqualTo(1);
    }

    // ── KINDERGARTEN_ADMIN：列表包含他人通知，GET任何本园通知→200 ────────────────────

    @Test
    void listNotifications_kindergartenAdminSeesAllKindergartenNotifications() throws Exception {
        long recipientUserId = userIdOf(RECIPIENT_LOGIN);
        long otherUserId = userIdOf(OTHER_LOGIN);
        long notif1 = insertNotification(OWN_KG, recipientUserId, "Title A", "Body A");
        long notif2 = insertNotification(OWN_KG, otherUserId, "Title B", "Body B");

        Cookie session = login(ADMIN_LOGIN);
        String body = mockMvc.perform(get("/api/v1/notifications").cookie(session))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        // Admin can see both notifications (and potentially seed notifications)
        assertThat(body).contains(String.valueOf(notif1));
        assertThat(body).contains(String.valueOf(notif2));
    }

    @Test
    void getNotification_kindergartenAdminGetsAnyInKindergartenNotification_returns200() throws Exception {
        long otherUserId = userIdOf(OTHER_LOGIN);
        long notifId = insertNotification(OWN_KG, otherUserId, "Admin Can Read", "Admin Body");

        Cookie session = login(ADMIN_LOGIN);
        mockMvc.perform(get("/api/v1/notifications/{id}", notifId).cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notificationId").value(notifId))
                .andExpect(jsonPath("$.title").value("Admin Can Read"))
                // 不含内部/S0 字段
                .andExpect(jsonPath("$.channel").doesNotExist())
                .andExpect(jsonPath("$.dedupeKey").doesNotExist())
                .andExpect(jsonPath("$.sentAt").doesNotExist())
                .andExpect(jsonPath("$.failReason").doesNotExist())
                .andExpect(jsonPath("$.retryCount").doesNotExist())
                .andExpect(jsonPath("$.recipientUserId").doesNotExist())
                .andExpect(jsonPath("$.kindergartenId").doesNotExist());
    }

    // 跨租户 404：seed 的 detection_events 仅在 kindergarten 1，无法廉价地在外园造 notification
    // （notifications 有 composite FK (kindergarten_id, event_id) → detection_events）。租户过滤
    // `n.kindergarten.id = :kgId` 与上面 recipient/admin 查询同源，且 GuardianChild/TeacherChild
    // 已覆盖同范式的跨租户隐藏 404，故此处不重复造外园数据。

    // ── 未认证 → 401 ─────────────────────────────────────────────────────────────────

    @Test
    void notifications_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/notifications"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/notifications/1"))
                .andExpect(status().isUnauthorized());
    }

    // ── Helpers ────────────────────────────────────────────────────────────────────

    private void upsertUser(String loginId, String phone) {
        String hash = passwordEncoder.encode(PASSWORD);
        jdbc.update("""
                INSERT INTO users (login_id, email, phone, password_hash, status, created_at, updated_at)
                VALUES (?, ?, ?, ?, 'ACTIVE', NOW(), NOW())
                ON CONFLICT (login_id) DO UPDATE
                    SET password_hash = EXCLUDED.password_hash, status = 'ACTIVE'
                """, loginId, loginId + "@nr-test.internal", phone, hash);
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
                    (kindergarten_id, user_id, name, rrn_encrypted, rrn_first6, gender, address,
                     status, created_at, updated_at)
                SELECT ?, user_id, ?, 'ENC', '000101', 'MALE', 'Test address', 'ACTIVE', NOW(), NOW()
                FROM users WHERE login_id = ?
                ON CONFLICT (user_id) DO UPDATE
                    SET status = 'ACTIVE', kindergarten_id = EXCLUDED.kindergarten_id
                """, kindergartenId, "Guardian " + loginId, loginId);
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

    private void clearRoleAndMembership(String loginId) {
        jdbc.update("DELETE FROM user_kindergarten_memberships WHERE user_id = "
                + "(SELECT user_id FROM users WHERE login_id = ?)", loginId);
        jdbc.update("DELETE FROM user_role_assignments WHERE user_id = "
                + "(SELECT user_id FROM users WHERE login_id = ?)", loginId);
    }

    /**
     * Insert a test notification row. Uses the minimum existing event_id from seed data.
     * sent_at and fail_reason are nullable (V3 migration).
     * dedupe_key must be unique per (kindergarten_id, dedupe_key).
     */
    private long insertNotification(long kindergartenId, long recipientUserId, String title, String body) {
        String dedupeKey = UUID.randomUUID().toString();
        Long notifId = jdbc.queryForObject("""
                INSERT INTO notifications
                    (kindergarten_id, event_id, recipient_user_id, channel, title, body,
                     status, dedupe_key, retry_count, created_at)
                VALUES (?, (SELECT event_id FROM detection_events WHERE kindergarten_id = ? LIMIT 1),
                        ?, 'PUSH'::notification_channel_enum,
                        ?, ?, 'QUEUED'::notification_status_enum,
                        ?, 0, NOW())
                RETURNING notification_id
                """, Long.class,
                kindergartenId, kindergartenId, recipientUserId, title, body, dedupeKey);
        createdNotificationIds.add(notifId);
        return notifId;
    }

    private long userIdOf(String loginId) {
        return jdbc.queryForObject("SELECT user_id FROM users WHERE login_id = ?", Long.class, loginId);
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
