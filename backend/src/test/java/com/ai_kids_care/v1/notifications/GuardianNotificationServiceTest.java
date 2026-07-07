package com.ai_kids_care.v1.notifications;

import com.ai_kids_care.BaseIntegrationTest;
import com.ai_kids_care.v1.service.GuardianNotificationService;
import com.ai_kids_care.v1.service.PushPort;
import com.ai_kids_care.v1.service.SmsPort;
import com.ai_kids_care.v1.type.EventStatusEnum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

/**
 * Step ③a: GuardianNotificationService recipient resolution + trigger matrix, exercised directly
 * (synchronous notifyOnReview; the AFTER_COMMIT @Async listener is the production trigger).
 *
 * Uses the seed demo chain (kg1): event 1 in room 1 (교실) → class 1 → child 1 → guardian (user 121);
 * event 2 in room 3 (놀이터, public space — no active class_room_assignment). PushPort is
 * mocked so dispatch reaches SENT without a real Pushover call. Self-cleaning on the shared container.
 */
class GuardianNotificationServiceTest extends BaseIntegrationTest {

    private static final long KG = 1L;
    private static final long EVENT_CLASSROOM = 1L;
    private static final long ROOM_CLASSROOM = 1L;
    private static final long EVENT_PUBLIC = 2L;
    private static final long ROOM_PUBLIC = 3L;
    private static final long GUARDIAN_USER = 121L;
    private static final long CHILD = 1L;
    private static final String SUB_ADDRESS = "pushover-guardian-kg1-key";
    private static final String GUARDIAN_PHONE = "010-1000-0121"; // seed users.phone for user 121

    @Autowired private GuardianNotificationService service;
    @Autowired private JdbcTemplate jdbc;
    @MockBean private PushPort pushPort; // sendToUser is a no-op → dispatch reaches SENT
    @MockBean private SmsPort smsPort; // mocked → no real Solapi send; dispatchSms reaches SENT on the no-op

    private OffsetDateTime detectedClassroom;
    private OffsetDateTime detectedPublic;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM notifications WHERE event_id IN (?, ?)", EVENT_CLASSROOM, EVENT_PUBLIC);
        jdbc.update("DELETE FROM push_subscriptions WHERE user_id = ? AND address = ?", GUARDIAN_USER, SUB_ADDRESS);
        jdbc.update("""
                INSERT INTO push_subscriptions (user_id, provider, address, status, created_at)
                VALUES (?, 'PUSHOVER'::push_provider_enum, ?, 'ACTIVE'::status_enum, NOW())
                """, GUARDIAN_USER, SUB_ADDRESS);
        detectedClassroom = jdbc.queryForObject(
                "SELECT detected_at FROM detection_events WHERE event_id = ?", OffsetDateTime.class, EVENT_CLASSROOM);
        detectedPublic = jdbc.queryForObject(
                "SELECT detected_at FROM detection_events WHERE event_id = ?", OffsetDateTime.class, EVENT_PUBLIC);
    }

    private int guardianNotificationCount(long eventId) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM notifications WHERE event_id = ? AND recipient_user_id = ?",
                Integer.class, eventId, GUARDIAN_USER);
    }

    private String guardianNotificationStatus(long eventId) {
        return jdbc.queryForObject(
                "SELECT status FROM notifications WHERE event_id = ? AND recipient_user_id = ? AND channel = 'PUSH'::notification_channel_enum",
                String.class, eventId, GUARDIAN_USER);
    }

    private int channelCount(long eventId, String channel) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM notifications WHERE event_id = ? AND recipient_user_id = ? "
                        + "AND channel = ?::notification_channel_enum",
                Integer.class, eventId, GUARDIAN_USER, channel);
    }

    private String channelStatus(long eventId, String channel) {
        return jdbc.queryForObject(
                "SELECT status FROM notifications WHERE event_id = ? AND recipient_user_id = ? "
                        + "AND channel = ?::notification_channel_enum",
                String.class, eventId, GUARDIAN_USER, channel);
    }

    @Test
    void escalatedClassroom_notifiesClassChildrenGuardians() {
        service.notifyOnReview(EVENT_CLASSROOM, KG, EventStatusEnum.ESCALATED,
                ROOM_CLASSROOM, detectedClassroom, null, null);

        // PUSH row created and SENT (unchanged behavior).
        assertThat(channelCount(EVENT_CLASSROOM, "PUSH")).isEqualTo(1);
        assertThat(guardianNotificationStatus(EVENT_CLASSROOM)).isEqualTo("SENT");
    }

    @Test
    void escalatedClassroom_guardianWithPhone_alsoGetsSms() {
        // Guardian user 121 has a seed users.phone → ESCALATED creates both a PUSH and an SMS row.
        service.notifyOnReview(EVENT_CLASSROOM, KG, EventStatusEnum.ESCALATED,
                ROOM_CLASSROOM, detectedClassroom, null, null);

        assertThat(guardianNotificationCount(EVENT_CLASSROOM)).isEqualTo(2); // PUSH + SMS
        assertThat(channelCount(EVENT_CLASSROOM, "PUSH")).isEqualTo(1);
        assertThat(channelCount(EVENT_CLASSROOM, "SMS")).isEqualTo(1);
        assertThat(channelStatus(EVENT_CLASSROOM, "SMS")).isEqualTo("SENT");
        // SMS dispatched via the mocked port to the guardian's users.phone — never real Solapi.
        verify(smsPort).send(eq(GUARDIAN_PHONE), any());
    }

    @Test
    void escalatedClassroom_guardianWithoutPhone_getsPushOnly() {
        // Blank out the guardian's phone → ESCALATED creates a PUSH row only (no SMS, no port call).
        String original = jdbc.queryForObject(
                "SELECT phone FROM users WHERE user_id = ?", String.class, GUARDIAN_USER);
        jdbc.update("UPDATE users SET phone = NULL WHERE user_id = ?", GUARDIAN_USER);
        try {
            service.notifyOnReview(EVENT_CLASSROOM, KG, EventStatusEnum.ESCALATED,
                    ROOM_CLASSROOM, detectedClassroom, null, null);

            assertThat(channelCount(EVENT_CLASSROOM, "PUSH")).isEqualTo(1);
            assertThat(channelCount(EVENT_CLASSROOM, "SMS")).isZero();
        } finally {
            jdbc.update("UPDATE users SET phone = ? WHERE user_id = ?", original, GUARDIAN_USER);
        }
    }

    @Test
    void escalatedClassroom_smsSendFailure_recordsSmsFailedButPushUnaffected() {
        doThrow(new IllegalStateException("Solapi 발송 실패")).when(smsPort).send(any(), any());

        service.notifyOnReview(EVENT_CLASSROOM, KG, EventStatusEnum.ESCALATED,
                ROOM_CLASSROOM, detectedClassroom, null, null);

        // SMS row recorded FAILED; the guardian's PUSH row is unaffected (still SENT).
        assertThat(channelStatus(EVENT_CLASSROOM, "SMS")).isEqualTo("FAILED");
        assertThat(channelStatus(EVENT_CLASSROOM, "PUSH")).isEqualTo("SENT");
    }

    @Test
    void resolvedWithNotifyGuardians_notifies() {
        service.notifyOnReview(EVENT_CLASSROOM, KG, EventStatusEnum.RESOLVED,
                ROOM_CLASSROOM, detectedClassroom, null, true);

        assertThat(guardianNotificationCount(EVENT_CLASSROOM)).isEqualTo(1);
    }

    @Test
    void resolvedWithoutNotifyGuardians_doesNotNotify() {
        service.notifyOnReview(EVENT_CLASSROOM, KG, EventStatusEnum.RESOLVED,
                ROOM_CLASSROOM, detectedClassroom, null, null);

        assertThat(guardianNotificationCount(EVENT_CLASSROOM)).isZero();
    }

    @Test
    void dismissed_doesNotNotify() {
        service.notifyOnReview(EVENT_CLASSROOM, KG, EventStatusEnum.DISMISSED,
                ROOM_CLASSROOM, detectedClassroom, null, null);

        assertThat(guardianNotificationCount(EVENT_CLASSROOM)).isZero();
    }

    @Test
    void publicSpaceWithAffectedChildIds_notifiesThoseGuardians() {
        service.notifyOnReview(EVENT_PUBLIC, KG, EventStatusEnum.ESCALATED,
                ROOM_PUBLIC, detectedPublic, List.of(CHILD), null);

        // ESCALATED + guardian has a phone → PUSH + SMS.
        assertThat(guardianNotificationCount(EVENT_PUBLIC)).isEqualTo(2);
        assertThat(channelCount(EVENT_PUBLIC, "PUSH")).isEqualTo(1);
        assertThat(channelCount(EVENT_PUBLIC, "SMS")).isEqualTo(1);
    }

    @Test
    void publicSpaceWithoutAffectedChildIds_doesNotNotify() {
        // room 3 (놀이터) has no active class_room_assignment → automatic resolution is empty
        service.notifyOnReview(EVENT_PUBLIC, KG, EventStatusEnum.ESCALATED,
                ROOM_PUBLIC, detectedPublic, null, null);

        assertThat(guardianNotificationCount(EVENT_PUBLIC)).isZero();
    }

    @Test
    void duplicateConfirm_doesNotDuplicateNotification() {
        service.notifyOnReview(EVENT_CLASSROOM, KG, EventStatusEnum.ESCALATED,
                ROOM_CLASSROOM, detectedClassroom, null, null);
        service.notifyOnReview(EVENT_CLASSROOM, KG, EventStatusEnum.ESCALATED,
                ROOM_CLASSROOM, detectedClassroom, null, null);

        // The -guardian (PUSH) and -guardian-sms (SMS) keys are each blocked by
        // UNIQUE(kindergarten_id, dedupe_key) on the second confirm → still exactly one row per channel.
        assertThat(channelCount(EVENT_CLASSROOM, "PUSH")).isEqualTo(1);
        assertThat(channelCount(EVENT_CLASSROOM, "SMS")).isEqualTo(1);
        assertThat(guardianNotificationCount(EVENT_CLASSROOM)).isEqualTo(2);
    }

    @Test
    void guardianWithoutActiveSubscription_recordedFailed() {
        // Clear ALL of this guardian's push subscriptions — other step-③b test classes
        // (QuietHours/Scanner) create user-121 subscriptions on the shared container, so an
        // address-scoped delete would leave a stray ACTIVE subscription and dispatch would SEND.
        jdbc.update("DELETE FROM push_subscriptions WHERE user_id = ?", GUARDIAN_USER);

        service.notifyOnReview(EVENT_CLASSROOM, KG, EventStatusEnum.ESCALATED,
                ROOM_CLASSROOM, detectedClassroom, null, null);

        // PUSH FAILED (no active subscription); the SMS channel is independent and reaches SENT
        // (mocked port), proving per-channel best-effort.
        assertThat(channelCount(EVENT_CLASSROOM, "PUSH")).isEqualTo(1);
        assertThat(guardianNotificationStatus(EVENT_CLASSROOM)).isEqualTo("FAILED");
        assertThat(channelStatus(EVENT_CLASSROOM, "SMS")).isEqualTo("SENT");
    }
}
