package com.ai_kids_care.v1.auth;

import com.ai_kids_care.BaseIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * INC-001 guardrail, rebuilt as an auth-authorization capability test (was previously a
 * source-scanning harness check).
 *
 * Enforces the DB-level invariant behind "Single Account Single Role" / account identity:
 * a user account's phone number is globally unique (UNIQUE index {@code uq_user_account_phone}
 * on {@code users.phone}). Two accounts MUST NOT share a phone number — the database rejects
 * the second insert before the row is written.
 *
 * This is the regression that the deleted harness phone-uniqueness check existed to prevent:
 * shared-container fixtures reusing a phone literal would collide on this constraint. Here we
 * assert the constraint itself directly, with self-contained fixture data.
 */
class PhoneUniquenessConstraintTest extends BaseIntegrationTest {

    private static final String SHARED_PHONE = "010-9999-0001";
    private static final String LOGIN_A = "inc001-phone-a";
    private static final String LOGIN_B = "inc001-phone-b";

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void cleanUp() {
        // Self-contained, idempotent across shared-container reruns.
        jdbc.update("DELETE FROM users WHERE login_id IN (?, ?) OR phone = ?",
                LOGIN_A, LOGIN_B, SHARED_PHONE);
    }

    @Test
    void secondAccountWithSamePhoneIsRejectedByUniqueConstraint() {
        insertUser(LOGIN_A, "inc001-phone-a@test.local", SHARED_PHONE);

        assertThatThrownBy(() ->
                insertUser(LOGIN_B, "inc001-phone-b@test.local", SHARED_PHONE))
                .as("a second user account with an already-used phone must violate uq_user_account_phone")
                .isInstanceOf(DataIntegrityViolationException.class)
                // Pin it to the phone constraint specifically, so the test can't pass on some
                // other future constraint (NOT NULL, a different unique index, etc.).
                .hasStackTraceContaining("uq_user_account_phone");
    }

    private void insertUser(String loginId, String email, String phone) {
        jdbc.update("""
                INSERT INTO users (login_id, email, phone, password_hash, status, created_at, updated_at)
                VALUES (?, ?, ?, ?, 'ACTIVE', NOW(), NOW())
                """, loginId, email, phone, "$2a$10$notarealhashnotarealhashnotarealhashnotarealhash00");
    }
}
