package com.ai_kids_care.v1.notifications;

import com.ai_kids_care.BaseIntegrationTest;
import com.ai_kids_care.v1.service.GuardianNotificationService;
import com.ai_kids_care.v1.service.PushoverService;
import com.ai_kids_care.v1.type.EventStatusEnum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Step ③a: GuardianNotificationService recipient resolution + trigger matrix, exercised directly
 * (synchronous notifyOnReview; the AFTER_COMMIT @Async listener is the production trigger).
 *
 * Uses the seed demo chain (kg1): event 1 in room 1 (교실) → class 1 → child 1 → guardian (user 121);
 * event 2 in room 3 (놀이터, public space — no active class_room_assignment). PushoverService is
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

    @Autowired private GuardianNotificationService service;
    @Autowired private JdbcTemplate jdbc;
    @MockBean private PushoverService pushoverService; // sendToUser is a no-op → dispatch reaches SENT

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
                "SELECT status FROM notifications WHERE event_id = ? AND recipient_user_id = ?",
                String.class, eventId, GUARDIAN_USER);
    }

    @Test
    void escalatedClassroom_notifiesClassChildrenGuardians() {
        service.notifyOnReview(EVENT_CLASSROOM, KG, EventStatusEnum.ESCALATED,
                ROOM_CLASSROOM, detectedClassroom, null, null);

        assertThat(guardianNotificationCount(EVENT_CLASSROOM)).isEqualTo(1);
        assertThat(guardianNotificationStatus(EVENT_CLASSROOM)).isEqualTo("SENT");
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

        assertThat(guardianNotificationCount(EVENT_PUBLIC)).isEqualTo(1);
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

        assertThat(guardianNotificationCount(EVENT_CLASSROOM)).isEqualTo(1);
    }

    @Test
    void guardianWithoutActiveSubscription_recordedFailed() {
        jdbc.update("DELETE FROM push_subscriptions WHERE user_id = ? AND address = ?", GUARDIAN_USER, SUB_ADDRESS);

        service.notifyOnReview(EVENT_CLASSROOM, KG, EventStatusEnum.ESCALATED,
                ROOM_CLASSROOM, detectedClassroom, null, null);

        assertThat(guardianNotificationCount(EVENT_CLASSROOM)).isEqualTo(1);
        assertThat(guardianNotificationStatus(EVENT_CLASSROOM)).isEqualTo("FAILED");
    }
}
