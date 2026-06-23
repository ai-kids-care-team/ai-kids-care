package com.ai_kids_care.v1.notifications;

import com.ai_kids_care.BaseIntegrationTest;
import com.ai_kids_care.v1.service.GuardianNotificationService;
import com.ai_kids_care.v1.service.PushoverService;
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
 * Step ③b: a RESOLVED guardian notification falling within the kindergarten's quiet hours is
 * deferred (DEFERRED + deferred_until); ESCALATED pierces quiet hours (immediate); outside the
 * window or with no config, RESOLVED is immediate. Uses the seed demo chain (event 1 / room 1 /
 * guardian user 121) and a dynamic quiet window around "now" so the test is time-independent.
 * PushoverService is mocked so an immediate dispatch reaches SENT. Self-cleaning (restores
 * kindergarten 1's quiet config to NULL on teardown).
 */
class GuardianNotificationQuietHoursTest extends BaseIntegrationTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter HHMM = DateTimeFormatter.ofPattern("HH:mm");
    private static final long KG = 1L;
    private static final long EVENT = 1L;
    private static final long ROOM = 1L;
    private static final long GUARDIAN_USER = 121L;
    private static final String SUB = "pushover-qh-g1-key";

    @Autowired private GuardianNotificationService service;
    @Autowired private JdbcTemplate jdbc;
    @MockBean private PushoverService pushoverService;

    private OffsetDateTime detected;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM notifications WHERE event_id = ?", EVENT);
        jdbc.update("DELETE FROM push_subscriptions WHERE user_id = ? AND address = ?", GUARDIAN_USER, SUB);
        jdbc.update("INSERT INTO push_subscriptions (user_id, provider, address, status, created_at) "
                + "VALUES (?, 'PUSHOVER'::push_provider_enum, ?, 'ACTIVE'::status_enum, NOW())", GUARDIAN_USER, SUB);
        jdbc.update("UPDATE kindergartens SET notification_quiet_hours_json = NULL WHERE kindergarten_id = ?", KG);
        detected = jdbc.queryForObject(
                "SELECT detected_at FROM detection_events WHERE event_id = ?", OffsetDateTime.class, EVENT);
    }

    @AfterEach
    void tearDown() {
        jdbc.update("UPDATE kindergartens SET notification_quiet_hours_json = NULL WHERE kindergarten_id = ?", KG);
        jdbc.update("DELETE FROM push_subscriptions WHERE user_id = ? AND address = ?", GUARDIAN_USER, SUB);
    }

    private void setQuiet(boolean covering) {
        LocalTime now = OffsetDateTime.now().atZoneSameInstant(SEOUL).toLocalTime();
        LocalTime start = covering ? now.minusHours(2) : now.plusHours(3);
        LocalTime end = covering ? now.plusHours(2) : now.plusHours(4);
        String json = "{\"start\":\"" + start.format(HHMM) + "\",\"end\":\"" + end.format(HHMM) + "\"}";
        jdbc.update("UPDATE kindergartens SET notification_quiet_hours_json = ? WHERE kindergarten_id = ?", json, KG);
    }

    private String status() {
        return jdbc.queryForObject(
                "SELECT status FROM notifications WHERE event_id = ? AND recipient_user_id = ?",
                String.class, EVENT, GUARDIAN_USER);
    }

    private OffsetDateTime deferredUntil() {
        return jdbc.queryForObject(
                "SELECT deferred_until FROM notifications WHERE event_id = ? AND recipient_user_id = ?",
                OffsetDateTime.class, EVENT, GUARDIAN_USER);
    }

    @Test
    void resolvedWithinQuietHours_isDeferred() {
        setQuiet(true);
        service.notifyOnReview(EVENT, KG, EventStatusEnum.RESOLVED, ROOM, detected, null, true);
        assertThat(status()).isEqualTo("DEFERRED");
        assertThat(deferredUntil()).isNotNull();
        assertThat(deferredUntil()).isAfter(OffsetDateTime.now());
    }

    @Test
    void escalatedWithinQuietHours_isImmediate() {
        setQuiet(true);
        service.notifyOnReview(EVENT, KG, EventStatusEnum.ESCALATED, ROOM, detected, null, null);
        assertThat(status()).isEqualTo("SENT");
        assertThat(deferredUntil()).isNull();
    }

    @Test
    void resolvedOutsideQuietHours_isImmediate() {
        setQuiet(false);
        service.notifyOnReview(EVENT, KG, EventStatusEnum.RESOLVED, ROOM, detected, null, true);
        assertThat(status()).isEqualTo("SENT");
    }

    @Test
    void resolvedWithNoQuietConfig_isImmediate() {
        // quiet_hours_json left NULL by setUp
        service.notifyOnReview(EVENT, KG, EventStatusEnum.RESOLVED, ROOM, detected, null, true);
        assertThat(status()).isEqualTo("SENT");
    }
}
