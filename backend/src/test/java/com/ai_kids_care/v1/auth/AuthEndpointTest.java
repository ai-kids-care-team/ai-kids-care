package com.ai_kids_care.v1.auth;

import com.ai_kids_care.BaseIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Characterization tests for the four implemented auth endpoints.
 *
 * These tests record the system's CURRENT behaviour (including quirks such as
 * wrong-password -> 401 via the configured security entry point). If a future change alters that behaviour intentionally,
 * update the assertion and note the reason.  Do not silently accept a regression.
 *
 * Seed data (from db/initdb/21_users_seed.sql) provides user login_id='admin'.
 * A dedicated test user is upserted in @BeforeEach so tests remain idempotent
 * across container-reuse runs.
 */
class AuthEndpointTest extends BaseIntegrationTest {

    private static final String TEST_LOGIN_ID = "baseline-test-auth-user";
    private static final String TEST_PASSWORD  = "Test@Baseline2024";

    @Autowired private TestRestTemplate rest;
    @Autowired private JdbcTemplate     jdbc;
    @Autowired private PasswordEncoder  passwordEncoder;

    @BeforeEach
    void upsertTestUser() {
        // ON CONFLICT handles both fresh-container and container-reuse scenarios.
        String hash = passwordEncoder.encode(TEST_PASSWORD);
        jdbc.update("""
                INSERT INTO users (login_id, email, phone, password_hash, status, created_at, updated_at)
                VALUES (?, ?, ?, ?, 'ACTIVE', NOW(), NOW())
                ON CONFLICT (login_id) DO UPDATE
                    SET password_hash = EXCLUDED.password_hash,
                        status        = 'ACTIVE'
                """,
                TEST_LOGIN_ID,
                "baseline-auth@test-baseline.internal",
                "010-0000-9997",
                hash);
    }

    // ── POST /api/v1/auth/login ──────────────────────────────────────────────

    @Test
    void login_validCredentials_returns200WithTokenFields() {
        var resp = rest.postForEntity(
                "/api/v1/auth/login",
                Map.of("identifier", TEST_LOGIN_ID, "password", TEST_PASSWORD),
                Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody())
                .containsKeys("accessToken", "refreshToken", "tokenType", "expiresIn");
        assertThat((String) resp.getBody().get("accessToken")).isNotBlank();
        assertThat((String) resp.getBody().get("refreshToken")).isNotBlank();
    }

    @Test
    void login_wrongPassword_returns401() {
        // Current behaviour: the error dispatch is handled by the configured security
        // entry point, which returns a generic 401.
        var resp = rest.postForEntity(
                "/api/v1/auth/login",
                Map.of("identifier", TEST_LOGIN_ID, "password", "definitely-wrong"),
                Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ── POST /api/v1/auth/refresh ────────────────────────────────────────────

    @Test
    void refresh_validRefreshToken_returns200WithNewTokens() {
        // Obtain a refresh token via login first.
        var loginResp = rest.postForEntity(
                "/api/v1/auth/login",
                Map.of("identifier", TEST_LOGIN_ID, "password", TEST_PASSWORD),
                Map.class);
        assertThat(loginResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        String refreshToken = (String) loginResp.getBody().get("refreshToken");

        var resp = rest.postForEntity(
                "/api/v1/auth/refresh",
                Map.of("refreshToken", refreshToken),
                Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).containsKey("accessToken");
        assertThat((String) resp.getBody().get("accessToken")).isNotBlank();
    }

    // ── POST /api/v1/auth/register ───────────────────────────────────────────

    @Test
    void register_superadminRole_returns201WithUserId() {
        // SUPERADMIN path (registerSuperadmin) does not require a kindergartenId.
        // @Valid is absent on the controller, so optional fields are not validated.
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        var body = Map.of(
                "userRole",   "SUPERADMIN",
                "loginId",    "test-reg-" + suffix,
                "email",      "test-reg-" + suffix + "@test-baseline.internal",
                "phone",      "0102222" + suffix.substring(0, 4),
                "password",   TEST_PASSWORD,
                "name",       "Test Superadmin",
                "rrnFirst6",  "990101",
                "rrnBack7",   "1234567",
                "gender",     "MALE",
                "department", "Test Dept"
        );

        ResponseEntity<Map> resp = rest.postForEntity("/api/v1/auth/register", body, Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(resp.getBody()).containsKey("userId");
        assertThat(resp.getBody().get("userId")).isNotNull();
    }

    // ── GET /api/v1/auth/register/availability ───────────────────────────────

    @Test
    void availability_existingLoginId_returnsUnavailable() {
        // 'admin' is inserted by db/initdb/21_users_seed.sql (user_id=1).
        ResponseEntity<Map> resp = rest.getForEntity(
                "/api/v1/auth/register/availability?field=login_id&value=admin",
                Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().get("available")).isEqualTo(false);
    }

    @Test
    void availability_newLoginId_returnsAvailable() {
        String newId = "nonexistent-" + UUID.randomUUID().toString().substring(0, 8);
        ResponseEntity<Map> resp = rest.getForEntity(
                "/api/v1/auth/register/availability?field=login_id&value=" + newId,
                Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().get("available")).isEqualTo(true);
    }
}
