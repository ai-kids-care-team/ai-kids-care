package com.ai_kids_care.v1.notifications;

import com.ai_kids_care.BaseIntegrationTest;
import com.ai_kids_care.v1.entity.Notification;
import com.ai_kids_care.v1.repository.NotificationRepository;
import com.ai_kids_care.v1.service.NotificationDeliveryStore;
import com.ai_kids_care.v1.service.NotificationService;
import com.ai_kids_care.v1.service.PushoverService;
import com.ai_kids_care.v1.service.SmsPort;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * PRF-02 delivery-atomicity invariants proven against a real PostgreSQL (testcontainers): the provider
 * call runs outside any DB transaction, the SENDING -> SENT/FAILED transition is idempotent, and a
 * retry never re-sends a delivery whose attempt already exists (at-most-once). The provider is mocked
 * so no external call leaves the box. Self-cleaning on the shared container.
 */
class NotificationDeliveryAtomicityIT extends BaseIntegrationTest {

    private static final long KG = 1L;
    private static final long EVENT = 1L;
    private static final long USER = 121L; // seed guardian user in kg1
    private static final String SUB = "pushover-atomicity-key";

    @Autowired private NotificationService notificationService;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private NotificationDeliveryStore deliveryStore;
    @Autowired private JdbcTemplate jdbc;

    @MockBean private PushoverService pushoverService; // no-op success unless a test stubs a failure
    @MockBean private SmsPort smsPort;                 // unused here; mocked so no real Solapi call

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM notification_delivery_attempts WHERE notification_id IN "
                + "(SELECT notification_id FROM notifications WHERE dedupe_key LIKE 'atom-%')");
        jdbc.update("DELETE FROM notifications WHERE dedupe_key LIKE 'atom-%'");
        jdbc.update("DELETE FROM push_subscriptions WHERE address = ?", SUB);
        jdbc.update("INSERT INTO push_subscriptions (user_id, provider, address, status, created_at) "
                + "VALUES (?, 'PUSHOVER'::push_provider_enum, ?, 'ACTIVE'::status_enum, NOW())", USER, SUB);
    }

    @AfterEach
    void tearDown() {
        jdbc.update("DELETE FROM notifications WHERE dedupe_key LIKE 'atom-%'");
        jdbc.update("DELETE FROM push_subscriptions WHERE address = ?", SUB);
    }

    @Test
    void providerSucceeds_thenRetry_doesNotResend() {
        long id = insertPush("atom-success-" + System.nanoTime());

        notificationService.dispatch(notificationRepository.findById(id).orElseThrow());
        assertThat(statusOf(id)).isEqualTo("SENT");
        assertThat(attemptOutcome(id)).isEqualTo("SUCCEEDED");

        // Retry the same delivery: the existing attempt must short-circuit -> no second provider call.
        notificationService.dispatch(notificationRepository.findById(id).orElseThrow());

        verify(pushoverService, times(1)).sendToUser(any(), any(), any());
        assertThat(statusOf(id)).isEqualTo("SENT");
    }

    @Test
    void providerSucceededButTerminalWriteLost_retryReconcilesToFailed_withoutResending() {
        long id = insertPush("atom-stuck-" + System.nanoTime());

        // Simulate the crash window: phase A committed (IN_FLIGHT + SENDING) but phase B never ran, so
        // the message may already be delivered while the record is still SENDING.
        deliveryStore.beginAttempt(notificationRepository.findById(id).orElseThrow(), "PUSHOVER");
        assertThat(statusOf(id)).isEqualTo("SENDING");
        assertThat(attemptOutcome(id)).isEqualTo("IN_FLIGHT");

        // A retry MUST NOT re-send (at-most-once); it reconciles the stuck row to a terminal FAILED.
        notificationService.dispatch(notificationRepository.findById(id).orElseThrow());

        verify(pushoverService, never()).sendToUser(any(), any(), any());
        assertThat(statusOf(id)).isEqualTo("FAILED");
        assertThat(attemptOutcome(id)).isEqualTo("FAILED");
    }

    @Test
    void providerFails_recordsFailedTerminalState() {
        long id = insertPush("atom-fail-" + System.nanoTime());
        doThrow(new IllegalStateException("Pushover delivery error"))
                .when(pushoverService).sendToUser(any(), any(), any());

        notificationService.dispatch(notificationRepository.findById(id).orElseThrow());

        verify(pushoverService, times(1)).sendToUser(any(), any(), any());
        assertThat(statusOf(id)).isEqualTo("FAILED");
        assertThat(failReasonOf(id)).contains("Pushover delivery failed");
        assertThat(attemptOutcome(id)).isEqualTo("FAILED");
    }

    // ── helpers ──────────────────────────────────────────────────────────────────

    private long insertPush(String dedupeKey) {
        jdbc.update("INSERT INTO notifications "
                + "(kindergarten_id, event_id, recipient_user_id, channel, title, body, status, dedupe_key, retry_count, created_at) "
                + "VALUES (?, ?, ?, 'PUSH'::notification_channel_enum, 't', 'b', 'QUEUED'::notification_status_enum, ?, 0, NOW())",
                KG, EVENT, USER, dedupeKey);
        return jdbc.queryForObject(
                "SELECT notification_id FROM notifications WHERE dedupe_key = ?", Long.class, dedupeKey);
    }

    private String statusOf(long id) {
        return jdbc.queryForObject(
                "SELECT status FROM notifications WHERE notification_id = ?", String.class, id);
    }

    private String failReasonOf(long id) {
        return jdbc.queryForObject(
                "SELECT fail_reason FROM notifications WHERE notification_id = ?", String.class, id);
    }

    private String attemptOutcome(long id) {
        return jdbc.queryForObject(
                "SELECT outcome FROM notification_delivery_attempts WHERE notification_id = ?", String.class, id);
    }
}
