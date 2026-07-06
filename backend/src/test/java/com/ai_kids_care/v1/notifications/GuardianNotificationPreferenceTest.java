package com.ai_kids_care.v1.notifications;

import com.ai_kids_care.BaseIntegrationTest;
import com.ai_kids_care.v1.service.GuardianNotificationService;
import com.ai_kids_care.v1.service.PushoverService;
import com.ai_kids_care.v1.service.SmsPort;
import com.ai_kids_care.v1.type.EventStatusEnum;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * UX-08: per-recipient defer/skip decisions in {@link GuardianNotificationService#notifyOnReview}
 * driven by the guardian's own canonical {@code notification_rules} row (via
 * {@code NotificationPreferenceService.findCanonical}). RESOLVED only — ESCALATED must pierce both
 * a disabled total switch and a user-level quiet window (chidren-safety red line, dev-lead spec).
 * Uses the seed demo chain (kg1 / event 1 / room 1 / guardian user 121), self-cleaning canonical
 * rows + kindergarten quiet config on teardown.
 */
class GuardianNotificationPreferenceTest extends BaseIntegrationTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter HHMM = DateTimeFormatter.ofPattern("HH:mm");
    private static final long KG = 1L;
    private static final long EVENT = 1L;
    private static final long ROOM = 1L;
    private static final long GUARDIAN_USER = 121L;
    private static final String SUB = "pushover-pref-g1-key";

    @Autowired private GuardianNotificationService service;
    @Autowired private JdbcTemplate jdbc;
    @MockBean private PushoverService pushoverService;
    @MockBean private SmsPort smsPort;

    private OffsetDateTime detected;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM notifications WHERE event_id = ?", EVENT);
        jdbc.update("DELETE FROM push_subscriptions WHERE user_id = ? AND address = ?", GUARDIAN_USER, SUB);
        jdbc.update("INSERT INTO push_subscriptions (user_id, provider, address, status, created_at) "
                + "VALUES (?, 'PUSHOVER'::push_provider_enum, ?, 'ACTIVE'::status_enum, NOW())", GUARDIAN_USER, SUB);
        jdbc.update("UPDATE kindergartens SET notification_quiet_hours_json = NULL WHERE kindergarten_id = ?", KG);
        deleteCanonicalRow();
        detected = jdbc.queryForObject(
                "SELECT detected_at FROM detection_events WHERE event_id = ?", OffsetDateTime.class, EVENT);
    }

    @AfterEach
    void tearDown() {
        jdbc.update("UPDATE kindergartens SET notification_quiet_hours_json = NULL WHERE kindergarten_id = ?", KG);
        jdbc.update("DELETE FROM push_subscriptions WHERE user_id = ? AND address = ?", GUARDIAN_USER, SUB);
        deleteCanonicalRow();
    }

    private void deleteCanonicalRow() {
        jdbc.update("DELETE FROM notification_rules WHERE kindergarten_id = ? AND user_id = ? "
                + "AND target_type = 'KINDERGARTEN'::notification_target_type", KG, GUARDIAN_USER);
    }

    private void insertCanonicalRow(boolean enabled, String quietHoursJson) {
        jdbc.update("""
                INSERT INTO notification_rules
                    (kindergarten_id, user_id, target_type, target_id, event_type, min_severity,
                     quiet_hours_json, enabled, created_at)
                VALUES (?, ?, 'KINDERGARTEN'::notification_target_type, ?, NULL, 0, ?::jsonb, ?, NOW())
                """, KG, GUARDIAN_USER, KG, quietHoursJson, enabled);
    }

    private String windowJson(boolean covering) {
        LocalTime now = OffsetDateTime.now().atZoneSameInstant(SEOUL).toLocalTime();
        LocalTime start = covering ? now.minusHours(2) : now.plusHours(3);
        LocalTime end = covering ? now.plusHours(2) : now.plusHours(4);
        return "{\"start\":\"" + start.format(HHMM) + "\",\"end\":\"" + end.format(HHMM) + "\"}";
    }

    private int pushCount() {
        return jdbc.queryForObject(
                "SELECT count(*) FROM notifications WHERE event_id = ? AND recipient_user_id = ? "
                        + "AND channel = 'PUSH'::notification_channel_enum",
                Integer.class, EVENT, GUARDIAN_USER);
    }

    private String pushStatus() {
        return jdbc.queryForObject(
                "SELECT status FROM notifications WHERE event_id = ? AND recipient_user_id = ? "
                        + "AND channel = 'PUSH'::notification_channel_enum",
                String.class, EVENT, GUARDIAN_USER);
    }

    @Test
    void resolved_disabledPreference_skipsGuardianEntirely() {
        insertCanonicalRow(false, null);
        service.notifyOnReview(EVENT, KG, EventStatusEnum.RESOLVED, ROOM, detected, null, true);
        assertThat(pushCount()).isZero();
    }

    @Test
    void escalated_disabledPreference_stillDeliversImmediately() {
        insertCanonicalRow(false, null);
        service.notifyOnReview(EVENT, KG, EventStatusEnum.ESCALATED, ROOM, detected, null, null);
        assertThat(pushCount()).isEqualTo(1);
        assertThat(pushStatus()).isEqualTo("SENT");
    }

    @Test
    void resolved_userQuietWindowCovering_deferredEvenWhenKindergartenWindowDoesNot() {
        jdbc.update("UPDATE kindergartens SET notification_quiet_hours_json = ?::jsonb WHERE kindergarten_id = ?",
                windowJson(false), KG);
        insertCanonicalRow(true, windowJson(true));

        service.notifyOnReview(EVENT, KG, EventStatusEnum.RESOLVED, ROOM, detected, null, true);

        assertThat(pushStatus()).isEqualTo("DEFERRED");
    }

    @Test
    void resolved_userQuietWindowNotCovering_immediateEvenWhenKindergartenWindowDoes() {
        jdbc.update("UPDATE kindergartens SET notification_quiet_hours_json = ?::jsonb WHERE kindergarten_id = ?",
                windowJson(true), KG);
        insertCanonicalRow(true, windowJson(false));

        service.notifyOnReview(EVENT, KG, EventStatusEnum.RESOLVED, ROOM, detected, null, true);

        assertThat(pushStatus()).isEqualTo("SENT");
    }

    @Test
    void escalated_userQuietWindowCovering_stillPierces() {
        insertCanonicalRow(true, windowJson(true));
        service.notifyOnReview(EVENT, KG, EventStatusEnum.ESCALATED, ROOM, detected, null, null);
        assertThat(pushStatus()).isEqualTo("SENT");
    }

    @Test
    void resolved_noCanonicalRow_fallsBackToKindergartenLevelWindow() {
        // deleteCanonicalRow() already ran in setUp — no per-user row exists.
        jdbc.update("UPDATE kindergartens SET notification_quiet_hours_json = ?::jsonb WHERE kindergarten_id = ?",
                windowJson(true), KG);

        service.notifyOnReview(EVENT, KG, EventStatusEnum.RESOLVED, ROOM, detected, null, true);

        assertThat(pushStatus()).isEqualTo("DEFERRED");
    }

    @Test
    void resolved_enabledWithNoOwnWindow_fallsBackToKindergartenLevelWindow() {
        // enabled=true but quiet_hours_json is null → falls back to the kindergarten-level window.
        jdbc.update("UPDATE kindergartens SET notification_quiet_hours_json = ?::jsonb WHERE kindergarten_id = ?",
                windowJson(true), KG);
        insertCanonicalRow(true, null);

        service.notifyOnReview(EVENT, KG, EventStatusEnum.RESOLVED, ROOM, detected, null, true);

        assertThat(pushStatus()).isEqualTo("DEFERRED");
    }
}
