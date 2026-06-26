package com.ai_kids_care.v1.notifications;

import com.ai_kids_care.BaseIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Schema governance for the push delivery addressing model (replaces the FCM/APNS-shaped
 * device_tokens). Enforces the DB invariant behind "Push delivery addressing model":
 * a recipient MUST NOT have two identical (user_id, provider, address) push subscriptions —
 * the UNIQUE index {@code uq_push_subscriptions_user_provider_address} rejects the duplicate.
 *
 * Self-contained fixture (own user + cleanup) so it is safe on the shared Testcontainer.
 */
class PushSubscriptionConstraintTest extends BaseIntegrationTest {

    private static final String LOGIN = "pushsub-uniq-user";
    private static final String PHONE = "010-9999-0002";
    private static final String ADDRESS = "pushover-userkey-abc123";

    @Autowired
    JdbcTemplate jdbc;

    private Long userId;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM push_subscriptions WHERE address = ?", ADDRESS);
        jdbc.update("DELETE FROM users WHERE login_id = ? OR phone = ?", LOGIN, PHONE);
        jdbc.update("""
                INSERT INTO users (login_id, email, phone, password_hash, status, created_at, updated_at)
                VALUES (?, ?, ?, ?, 'ACTIVE', NOW(), NOW())
                """, LOGIN, "pushsub-uniq@test.local", PHONE,
                "$2a$10$notarealhashnotarealhashnotarealhashnotarealhash00");
        userId = jdbc.queryForObject("SELECT user_id FROM users WHERE login_id = ?", Long.class, LOGIN);
    }

    @Test
    void duplicateUserProviderAddressIsRejectedByUniqueConstraint() {
        insertSubscription(ADDRESS);

        assertThatThrownBy(() -> insertSubscription(ADDRESS))
                .as("a second push subscription with the same (user_id, provider, address) must be rejected")
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasStackTraceContaining("uq_push_subscriptions_user_provider_address");
    }

    private void insertSubscription(String address) {
        jdbc.update("""
                INSERT INTO push_subscriptions (user_id, provider, address, status, created_at)
                VALUES (?, 'PUSHOVER'::push_provider_enum, ?, 'ACTIVE'::status_enum, NOW())
                """, userId, address);
    }
}
