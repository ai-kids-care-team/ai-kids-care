package com.ai_kids_care.v1.notifications;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * wire-notification-read-state (UX-03/UX-04): HTTP contract tests for {@code PATCH
 * /api/v1/notifications/{id}/read} and {@code GET /api/v1/notifications/unread-count}.
 *
 * Mirrors the fixture style of {@link com.ai_kids_care.v1.detection.EventReviewApiTest}: seed
 * tenant users against the FIRST seeded detection_events row (own tenant) and a detection_events
 * row from a DIFFERENT kindergarten (foreign tenant), then insert {@code notifications} rows
 * directly via JDBC to control recipient / read state precisely.
 */
@AutoConfigureMockMvc
class NotificationReadStateApiTest extends BaseIntegrationTest {

    private static final String PW = "Test@NotifRead2026";
    private static final String OWNER_LOGIN = "nrs-owner";
    private static final String OTHER_LOGIN = "nrs-other";
    private static final String ADMIN_LOGIN = "nrs-admin";
    private static final String FOREIGN_LOGIN = "nrs-foreign";

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private ObjectMapper objectMapper;

    private long kindergartenId;
    private long eventId;
    private long foreignKindergartenId;

    @BeforeEach
    void setUp() {
        Map<String, Object> ev = jdbc.queryForMap(
                "SELECT event_id AS eid, kindergarten_id AS kg FROM detection_events ORDER BY event_id LIMIT 1");
        eventId = ((Number) ev.get("eid")).longValue();
        kindergartenId = ((Number) ev.get("kg")).longValue();

        // Seeded detection_events only populates kindergarten_id=1 (unlike `kindergartens`, which has
        // several rows) — mirror EventReviewApiTest and pick a genuinely different tenant from
        // `kindergartens` directly. Its own detection_events chain (needed by the one test that
        // inserts a foreign-tenant notification) is built lazily by insertForeignTenantEventId().
        foreignKindergartenId = jdbc.queryForObject(
                "SELECT kindergarten_id FROM kindergartens WHERE kindergarten_id <> ? ORDER BY kindergarten_id LIMIT 1",
                Long.class, kindergartenId);

        // 010-0000-791x is otherwise unused across the test suite (the PG testcontainer is shared,
        // no rollback, and uq_user_account_phone is a real unique constraint — see GraphReadApiTest's
        // comment on this exact pitfall).
        seedTenantUser(OWNER_LOGIN, "nrs-owner@test.local", "010-0000-7911", "GUARDIAN", kindergartenId);
        seedTenantUser(OTHER_LOGIN, "nrs-other@test.local", "010-0000-7912", "GUARDIAN", kindergartenId);
        seedTenantUser(ADMIN_LOGIN, "nrs-admin@test.local", "010-0000-7913", "KINDERGARTEN_ADMIN", kindergartenId);
        seedTenantUser(FOREIGN_LOGIN, "nrs-foreign@test.local", "010-0000-7914", "GUARDIAN", foreignKindergartenId);

        // The PG testcontainer is shared across test METHODS too (no per-test rollback) and
        // seedTenantUser upserts the SAME user rows every time, so notifications inserted by an
        // earlier test method would otherwise still be there (and still unread) when a later method
        // asserts an exact unread-count. Clear this test's own fixture rows before each method.
        jdbc.update("DELETE FROM notifications WHERE recipient_user_id IN "
                + "(SELECT user_id FROM users WHERE login_id IN (?, ?, ?, ?))",
                OWNER_LOGIN, OTHER_LOGIN, ADMIN_LOGIN, FOREIGN_LOGIN);
    }

    // ── mark-read ────────────────────────────────────────────────────────────

    @Test
    void markRead_own_persistsReadAtAndReturns200() throws Exception {
        long userId = userIdOf(OWNER_LOGIN);
        long notificationId = insertNotification(kindergartenId, eventId, userId, "SENT");

        Cookie owner = login(OWNER_LOGIN);
        mockMvc.perform(withCsrf(patch("/api/v1/notifications/{id}/read", notificationId)).cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notificationId").value(notificationId))
                .andExpect(jsonPath("$.readAt").exists())
                .andExpect(jsonPath("$.status").value("SENT"));

        java.sql.Timestamp readAt = jdbc.queryForObject(
                "SELECT read_at FROM notifications WHERE notification_id = ?", java.sql.Timestamp.class, notificationId);
        assertThat(readAt).isNotNull();
    }

    @Test
    void markRead_crossUser_returnsHidden404AndAuditsDenied() throws Exception {
        long ownerId = userIdOf(OWNER_LOGIN);
        long notificationId = insertNotification(kindergartenId, eventId, ownerId, "SENT");

        Cookie other = login(OTHER_LOGIN);
        MvcResult result = mockMvc.perform(withCsrf(patch("/api/v1/notifications/{id}/read", notificationId)).cookie(other))
                .andExpect(status().isNotFound())
                .andReturn();

        String correlationId = result.getResponse().getHeader("X-Correlation-Id");
        assertThat(correlationId).isNotBlank();
        Integer denied = jdbc.queryForObject(
                "SELECT count(*) FROM audit_logs WHERE correlation_id = ? AND action = 'AUTHORIZATION_DENIED' "
                        + "AND result = 'DENIED' AND resource_type = 'NOTIFICATION' AND resource_id = ? AND user_id = ?",
                Integer.class, correlationId, notificationId, userIdOf(OTHER_LOGIN));
        assertThat(denied).isEqualTo(1);

        // The other user's failed attempt must not have mutated the owner's row.
        java.sql.Timestamp readAt = jdbc.queryForObject(
                "SELECT read_at FROM notifications WHERE notification_id = ?", java.sql.Timestamp.class, notificationId);
        assertThat(readAt).isNull();
    }

    @Test
    void markRead_crossTenant_returnsHidden404() throws Exception {
        long ownerId = userIdOf(OWNER_LOGIN);
        long notificationId = insertNotification(kindergartenId, eventId, ownerId, "SENT");

        // FOREIGN_LOGIN's active tenant is a different kindergarten entirely — the kindergarten_id
        // predicate alone must hide this notification's existence, independent of recipient identity.
        Cookie foreign = login(FOREIGN_LOGIN);
        mockMvc.perform(withCsrf(patch("/api/v1/notifications/{id}/read", notificationId)).cookie(foreign))
                .andExpect(status().isNotFound());
    }

    @Test
    void markRead_missingNotification_returnsHidden404() throws Exception {
        Cookie owner = login(OWNER_LOGIN);
        mockMvc.perform(withCsrf(patch("/api/v1/notifications/{id}/read", 999_999_999L)).cookie(owner))
                .andExpect(status().isNotFound());
    }

    @Test
    void markRead_alreadyRead_isIdempotent200AndReadAtStable() throws Exception {
        long userId = userIdOf(OWNER_LOGIN);
        long notificationId = insertNotification(kindergartenId, eventId, userId, "SENT");
        Cookie owner = login(OWNER_LOGIN);

        mockMvc.perform(withCsrf(patch("/api/v1/notifications/{id}/read", notificationId)).cookie(owner))
                .andExpect(status().isOk());
        java.sql.Timestamp firstReadAt = jdbc.queryForObject(
                "SELECT read_at FROM notifications WHERE notification_id = ?", java.sql.Timestamp.class, notificationId);
        assertThat(firstReadAt).isNotNull();

        // Re-call: still 200 (idempotent no-op), and the original read_at is not disturbed.
        mockMvc.perform(withCsrf(patch("/api/v1/notifications/{id}/read", notificationId)).cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.readAt").exists());
        java.sql.Timestamp secondReadAt = jdbc.queryForObject(
                "SELECT read_at FROM notifications WHERE notification_id = ?", java.sql.Timestamp.class, notificationId);
        assertThat(secondReadAt).isEqualTo(firstReadAt);
    }

    @Test
    void anonymousMarkRead_isRejected() throws Exception {
        // No CSRF token + no session: the CSRF filter rejects a state-changing request (403) before
        // Spring Security's entry point would return 401 — same as EventReviewApiTest's anonymous
        // POST case. Either way it is a 4xx client error, never anonymous business data.
        long userId = userIdOf(OWNER_LOGIN);
        long notificationId = insertNotification(kindergartenId, eventId, userId, "SENT");
        mockMvc.perform(patch("/api/v1/notifications/{id}/read", notificationId))
                .andExpect(status().is4xxClientError());
    }

    // ── unread-count ─────────────────────────────────────────────────────────

    @Test
    void unreadCount_countsOnlyOwnUnreadNotifications() throws Exception {
        long ownerId = userIdOf(OWNER_LOGIN);
        long otherId = userIdOf(OTHER_LOGIN);
        insertNotification(kindergartenId, eventId, ownerId, "SENT");
        insertNotification(kindergartenId, eventId, ownerId, "SENT");
        long readOne = insertNotification(kindergartenId, eventId, ownerId, "SENT");
        jdbc.update("UPDATE notifications SET read_at = NOW() WHERE notification_id = ?", readOne);
        // Another recipient's unread notifications must not leak into owner's count.
        insertNotification(kindergartenId, eventId, otherId, "SENT");

        Cookie owner = login(OWNER_LOGIN);
        mockMvc.perform(get("/api/v1/notifications/unread-count").cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(2));
    }

    @Test
    void unreadCount_adminBadgeCountsOwnOnly_whileListIsWholeKindergarten() throws Exception {
        long adminId = userIdOf(ADMIN_LOGIN);
        long ownerId = userIdOf(OWNER_LOGIN);
        insertNotification(kindergartenId, eventId, adminId, "SENT");
        insertNotification(kindergartenId, eventId, ownerId, "SENT");
        insertNotification(kindergartenId, eventId, ownerId, "SENT");

        Cookie admin = login(ADMIN_LOGIN);
        // Badge: only the admin's own notification.
        mockMvc.perform(get("/api/v1/notifications/unread-count").cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(1));

        // List: KINDERGARTEN_ADMIN sees the whole kindergarten (own + owner's), unaffected by the badge.
        mockMvc.perform(get("/api/v1/notifications").cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(3)));
    }

    @Test
    void unreadCount_tenantIsolation_foreignTenantUnreadNotCounted() throws Exception {
        long ownerId = userIdOf(OWNER_LOGIN);
        long foreignId = userIdOf(FOREIGN_LOGIN);
        long foreignEventId = insertForeignTenantEventId();
        insertNotification(kindergartenId, eventId, ownerId, "SENT");
        insertNotification(foreignKindergartenId, foreignEventId, foreignId, "SENT");

        Cookie owner = login(OWNER_LOGIN);
        mockMvc.perform(get("/api/v1/notifications/unread-count").cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(1));
    }

    @Test
    void anonymousUnreadCount_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/notifications/unread-count")).andExpect(status().isUnauthorized());
    }

    // ── VO shape: readAt exposed, internal delivery fields hidden ──────────────

    @Test
    void readVO_exposesReadAt_andHidesInternalDeliveryFields() throws Exception {
        long userId = userIdOf(OWNER_LOGIN);
        long notificationId = insertNotification(kindergartenId, eventId, userId, "SENT");

        Cookie owner = login(OWNER_LOGIN);
        mockMvc.perform(get("/api/v1/notifications/{id}", notificationId).cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notificationId").value(notificationId))
                .andExpect(jsonPath("$.title").exists())
                .andExpect(jsonPath("$.body").exists())
                .andExpect(jsonPath("$.status").value("SENT"))
                .andExpect(jsonPath("$.readAt").doesNotExist())  // unread -> null, Jackson omits it
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.channel").doesNotExist())
                .andExpect(jsonPath("$.dedupeKey").doesNotExist())
                .andExpect(jsonPath("$.sentAt").doesNotExist())
                .andExpect(jsonPath("$.failReason").doesNotExist())
                .andExpect(jsonPath("$.retryCount").doesNotExist())
                .andExpect(jsonPath("$.recipientUserId").doesNotExist())
                .andExpect(jsonPath("$.kindergartenId").doesNotExist());

        // After marking read, readAt becomes a populated field on the very same read path.
        mockMvc.perform(withCsrf(patch("/api/v1/notifications/{id}/read", notificationId)).cookie(owner))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/notifications/{id}", notificationId).cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.readAt").exists())
                .andExpect(jsonPath("$.channel").doesNotExist());
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    /**
     * Builds a minimal detection_sessions + detection_events chain scoped to
     * {@code foreignKindergartenId}, satisfying notifications' composite
     * {@code (kindergarten_id, event_id)} FK, and returns the new event_id. Seeded detection_events
     * only covers kindergarten_id=1, so the one test that needs a genuine foreign-tenant notification
     * builds its own event row lazily rather than requiring it to already exist in fixtures.
     */
    private long insertForeignTenantEventId() {
        long cameraId = jdbc.queryForObject(
                "SELECT camera_id FROM cctv_cameras WHERE kindergarten_id = ? ORDER BY camera_id LIMIT 1",
                Long.class, foreignKindergartenId);
        long roomId = jdbc.queryForObject(
                "SELECT room_id FROM rooms WHERE kindergarten_id = ? ORDER BY room_id LIMIT 1",
                Long.class, foreignKindergartenId);
        long streamId = jdbc.queryForObject(
                "SELECT stream_id FROM camera_streams WHERE kindergarten_id = ? ORDER BY stream_id LIMIT 1",
                Long.class, foreignKindergartenId);
        long modelId = jdbc.queryForObject("SELECT model_id FROM ai_models ORDER BY model_id LIMIT 1", Long.class);

        long sessionId = jdbc.queryForObject("""
                INSERT INTO detection_sessions (kindergarten_id, camera_id, stream_id, model_id, started_at, status)
                VALUES (?, ?, ?, ?, NOW(), 'ACTIVE'::status_enum)
                RETURNING session_id
                """, Long.class, foreignKindergartenId, cameraId, streamId, modelId);

        return jdbc.queryForObject("""
                INSERT INTO detection_events (kindergarten_id, camera_id, room_id, session_id, event_type,
                    severity, confidence, detected_at, start_time, end_time, status, dedup_key)
                VALUES (?, ?, ?, ?, 'OTHER'::event_type_enum, 1, 0.5, NOW(), NOW(), NOW(),
                    'RESOLVED'::event_status_enum, ?)
                RETURNING event_id
                """, Long.class, foreignKindergartenId, cameraId, roomId, sessionId,
                "nrs-test-foreign-" + UUID.randomUUID());
    }

    private long insertNotification(long kgId, long evId, long recipientUserId, String status) {
        return jdbc.queryForObject("""
                INSERT INTO notifications (kindergarten_id, event_id, recipient_user_id, channel, title, body, status, dedupe_key, created_at)
                VALUES (?, ?, ?, 'PUSH'::notification_channel_enum, 'Test title', 'Test body', ?::notification_status_enum, ?, NOW())
                RETURNING notification_id
                """, Long.class, kgId, evId, recipientUserId, status, "nrs-test-" + UUID.randomUUID());
    }

    private long userIdOf(String loginId) {
        return jdbc.queryForObject("SELECT user_id FROM users WHERE login_id = ?", Long.class, loginId);
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

    private void seedTenantUser(String loginId, String email, String phone, String role, long kgId) {
        // Upsert the user (do NOT delete — audit_logs FK-references it after login). Only the
        // role assignment / membership are rebuilt (not referenced by audit_logs).
        jdbc.update("""
                INSERT INTO users (login_id, email, phone, password_hash, status, created_at, updated_at)
                VALUES (?, ?, ?, ?, 'ACTIVE', NOW(), NOW())
                ON CONFLICT (login_id) DO UPDATE SET password_hash = EXCLUDED.password_hash, status = 'ACTIVE'
                """, loginId, email, phone, passwordEncoder.encode(PW));
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
