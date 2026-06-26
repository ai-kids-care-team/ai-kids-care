package com.ai_kids_care.v1.notifications;

import com.ai_kids_care.BaseIntegrationTest;
import com.ai_kids_care.v1.service.DeferredNotificationScanner;
import com.ai_kids_care.v1.service.PushoverService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Step ③b: the scanner dispatches DEFERRED notifications whose deferred_until has passed and leaves
 * not-yet-due ones untouched. PushoverService is mocked so a dispatched row reaches SENT. The test
 * profile disables the @Scheduled auto-scan (huge interval), so scan() is invoked directly.
 */
class DeferredNotificationScannerTest extends BaseIntegrationTest {

    private static final long KG = 1L;
    private static final long EVENT = 1L;
    private static final long GUARDIAN_USER = 121L;
    private static final String SUB = "pushover-scan-g1-key";

    @Autowired private DeferredNotificationScanner scanner;
    @Autowired private JdbcTemplate jdbc;
    @MockBean private PushoverService pushoverService;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM notifications WHERE event_id = ?", EVENT);
        jdbc.update("DELETE FROM push_subscriptions WHERE user_id = ? AND address = ?", GUARDIAN_USER, SUB);
        jdbc.update("INSERT INTO push_subscriptions (user_id, provider, address, status, created_at) "
                + "VALUES (?, 'PUSHOVER'::push_provider_enum, ?, 'ACTIVE'::status_enum, NOW())", GUARDIAN_USER, SUB);
    }

    @AfterEach
    void tearDown() {
        jdbc.update("DELETE FROM push_subscriptions WHERE user_id = ? AND address = ?", GUARDIAN_USER, SUB);
        jdbc.update("DELETE FROM notifications WHERE event_id = ?", EVENT);
    }

    private void insertDeferred(String dedupe, String deferredUntilSql) {
        jdbc.update("INSERT INTO notifications "
                        + "(kindergarten_id, event_id, recipient_user_id, channel, title, body, status, dedupe_key, "
                        + "retry_count, deferred_until, created_at) "
                        + "VALUES (?, ?, ?, 'PUSH'::notification_channel_enum, '안전 알림', '본문', "
                        + "'DEFERRED'::notification_status_enum, ?, 0, " + deferredUntilSql + ", NOW())",
                KG, EVENT, GUARDIAN_USER, dedupe);
    }

    private String statusOf(String dedupe) {
        return jdbc.queryForObject("SELECT status FROM notifications WHERE dedupe_key = ?", String.class, dedupe);
    }

    @Test
    void dueDeferred_isDispatched() {
        insertDeferred("evt-1-u-121-guardian-due", "NOW() - INTERVAL '1 minute'");
        scanner.scan();
        assertThat(statusOf("evt-1-u-121-guardian-due")).isEqualTo("SENT");
    }

    @Test
    void notYetDueDeferred_isLeftUntouched() {
        insertDeferred("evt-1-u-121-guardian-future", "NOW() + INTERVAL '1 hour'");
        scanner.scan();
        assertThat(statusOf("evt-1-u-121-guardian-future")).isEqualTo("DEFERRED");
    }
}
