package com.ai_kids_care.v1.notifications;

import com.ai_kids_care.BaseIntegrationTest;
import com.ai_kids_care.v1.event.EventReviewedEvent;
import com.ai_kids_care.v1.service.PushoverService;
import com.ai_kids_care.v1.service.SmsPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Duration;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Step ③a end-to-end regression net: an authenticated HTTP review confirmation
 * ({@code POST /api/v1/event_reviews}) → {@code EventReviewedEvent} → the real
 * {@code @Async @TransactionalEventListener(AFTER_COMMIT)} in {@link com.ai_kids_care.v1.service.GuardianNotificationService}
 * → relationship-graph guardian resolution → {@code NotificationService.dispatch} → {@code notifications}
 * row. Unlike {@code GuardianNotificationServiceTest} (which calls the synchronous overload directly and
 * bypasses the transaction/thread boundary) this drives the whole production chain over the wire, so it
 * uses Awaitility to poll the table after the async listener completes.
 *
 * <p>Seed demo chain (kg1): event 1 in room 1 (교실) → class 1 → child 1 → guardian (user 121);
 * event 2 in room 3 (놀이터, public space — no active class_room_assignment) resolved via affectedChildIds.
 * PushoverService is mocked so an immediate dispatch reaches SENT in-process. Self-cleaning on the shared
 * container; the kindergarten's quiet config is restored to NULL on teardown.</p>
 */
@AutoConfigureMockMvc
@RecordApplicationEvents
class GuardianNotificationConfirmDispatchE2EIT extends BaseIntegrationTest {

    private static final String PW = "Test@E2eDispatch2026";
    private static final String ADMIN_LOGIN = "director-kg1";

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter HHMM = DateTimeFormatter.ofPattern("HH:mm");

    private static final long KG = 1L;
    private static final long EVENT_CLASSROOM = 1L;
    private static final long EVENT_PUBLIC = 2L;
    private static final long GUARDIAN_USER = 121L;
    private static final long CHILD = 1L;
    private static final String SUB_ADDRESS = "pushover-e2e-g1-key";

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private ObjectMapper objectMapper;
    @MockBean private PushoverService pushoverService; // sendToUser is a no-op → immediate dispatch reaches SENT
    @MockBean private SmsPort smsPort; // mocked → ESCALATED guardian SMS never hits real Solapi

    @BeforeEach
    void setUp() {
        // Reset the events under test back to OPEN (seed leaves event 1 RESOLVED) so each confirm transitions.
        jdbc.update("UPDATE detection_events SET status = 'OPEN'::event_status_enum WHERE event_id IN (?, ?)",
                EVENT_CLASSROOM, EVENT_PUBLIC);
        jdbc.update("DELETE FROM notifications WHERE event_id IN (?, ?)", EVENT_CLASSROOM, EVENT_PUBLIC);
        // Guardian has no seed push subscription; insert one so dispatch can reach SENT.
        jdbc.update("DELETE FROM push_subscriptions WHERE user_id = ? AND address = ?", GUARDIAN_USER, SUB_ADDRESS);
        jdbc.update("INSERT INTO push_subscriptions (user_id, provider, address, status, created_at) "
                + "VALUES (?, 'PUSHOVER'::push_provider_enum, ?, 'ACTIVE'::status_enum, NOW())",
                GUARDIAN_USER, SUB_ADDRESS);
        // The acting admin: a KINDERGARTEN_ADMIN scoped to kg1.
        seedTenantUser(ADMIN_LOGIN, "director-kg1@test.local", "010-0000-6611", "KINDERGARTEN_ADMIN", KG);
        // Start each test outside quiet hours (restored to NULL); the quiet-hours scenario opts in.
        jdbc.update("UPDATE kindergartens SET notification_quiet_hours_json = NULL WHERE kindergarten_id = ?", KG);
    }

    @AfterEach
    void tearDown() {
        jdbc.update("UPDATE kindergartens SET notification_quiet_hours_json = NULL WHERE kindergarten_id = ?", KG);
        jdbc.update("DELETE FROM push_subscriptions WHERE user_id = ? AND address = ?", GUARDIAN_USER, SUB_ADDRESS);
    }

    @Test
    void escalatedConfirmation_deliversImmediately(ApplicationEvents events) throws Exception {
        Cookie admin = login(ADMIN_LOGIN);
        confirm(admin, Map.of("eventId", EVENT_CLASSROOM, "resultStatus", "ESCALATED", "notifyGuardians", true));

        // The event is published synchronously within confirm's transaction.
        assertThat(events.stream(EventReviewedEvent.class)
                .filter(e -> e.eventId().equals(EVENT_CLASSROOM)).count())
                .as("EventReviewedEvent published for the confirmed classroom event").isEqualTo(1);

        // The async AFTER_COMMIT listener resolves guardian user 121 via the room→class→child→guardian graph
        // and dispatches; PushoverService is mocked so the row reaches SENT.
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                assertThat(guardianStatus(EVENT_CLASSROOM))
                        .as("guardian notification reaches terminal SENT via the async path").isEqualTo("SENT"));
    }

    @Test
    void resolvedDuringQuietHours_isDeferred(ApplicationEvents events) throws Exception {
        setCoveringQuietWindow(); // a window around "now" in Asia/Seoul that always contains the current run time

        Cookie admin = login(ADMIN_LOGIN);
        confirm(admin, Map.of("eventId", EVENT_CLASSROOM, "resultStatus", "RESOLVED", "notifyGuardians", true));

        assertThat(events.stream(EventReviewedEvent.class)
                .filter(e -> e.eventId().equals(EVENT_CLASSROOM)).count())
                .as("EventReviewedEvent published for the RESOLVED confirmation").isEqualTo(1);

        // RESOLVED within quiet hours → persisted DEFERRED and dispatch skipped (deferred_until set).
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                assertThat(guardianStatus(EVENT_CLASSROOM))
                        .as("RESOLVED-during-quiet-hours guardian notification is DEFERRED").isEqualTo("DEFERRED"));
        assertThat(deferredUntil(EVENT_CLASSROOM))
                .as("deferred notification carries a future deferred_until").isAfter(OffsetDateTime.now());
    }

    @Test
    void publicSpaceEvent_resolvesViaAffectedChildIds(ApplicationEvents events) throws Exception {
        Cookie admin = login(ADMIN_LOGIN);
        // Event 2 is in room 3 (놀이터) with no active class_room_assignment; the graph would resolve nobody,
        // so the guardian must be reached through the explicit affectedChildIds list.
        confirm(admin, Map.of(
                "eventId", EVENT_PUBLIC,
                "resultStatus", "ESCALATED",
                "notifyGuardians", true,
                "affectedChildIds", java.util.List.of(CHILD)));

        assertThat(events.stream(EventReviewedEvent.class)
                .filter(e -> e.eventId().equals(EVENT_PUBLIC)).count())
                .as("EventReviewedEvent published for the public-space event").isEqualTo(1);

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            assertThat(guardianCount(EVENT_PUBLIC))
                    .as("guardian resolved from affectedChildIds for the public-space event").isEqualTo(1);
            assertThat(guardianStatus(EVENT_PUBLIC)).isEqualTo("SENT");
        });
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private void confirm(Cookie admin, Map<String, Object> body) throws Exception {
        mockMvc.perform(withCsrf(post("/api/v1/event_reviews")).cookie(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated());
    }

    /** A quiet window covering [now-2h, now+2h] in Asia/Seoul — always contains the current run time. */
    private void setCoveringQuietWindow() {
        LocalTime now = OffsetDateTime.now().atZoneSameInstant(SEOUL).toLocalTime();
        LocalTime start = now.minusHours(2);
        LocalTime end = now.plusHours(2);
        String json = "{\"start\":\"" + start.format(HHMM) + "\",\"end\":\"" + end.format(HHMM) + "\"}";
        jdbc.update("UPDATE kindergartens SET notification_quiet_hours_json = ? WHERE kindergarten_id = ?", json, KG);
    }

    // Helpers are scoped to the PUSH channel: this class verifies the PUSH delivery lifecycle (the
    // unchanged behavior). An ESCALATED confirm also creates an additive SMS row (guardian 121 has a
    // phone) dispatched via the mocked SmsPort — covered by GuardianNotificationServiceTest — so PUSH
    // scoping keeps these single-row queries deterministic.
    private int guardianCount(long eventId) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM notifications WHERE event_id = ? AND recipient_user_id = ? "
                        + "AND channel = 'PUSH'::notification_channel_enum",
                Integer.class, eventId, GUARDIAN_USER);
    }

    private String guardianStatus(long eventId) {
        return jdbc.queryForObject(
                "SELECT status FROM notifications WHERE event_id = ? AND recipient_user_id = ? "
                        + "AND channel = 'PUSH'::notification_channel_enum",
                String.class, eventId, GUARDIAN_USER);
    }

    private OffsetDateTime deferredUntil(long eventId) {
        return jdbc.queryForObject(
                "SELECT deferred_until FROM notifications WHERE event_id = ? AND recipient_user_id = ? "
                        + "AND channel = 'PUSH'::notification_channel_enum",
                OffsetDateTime.class, eventId, GUARDIAN_USER);
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
