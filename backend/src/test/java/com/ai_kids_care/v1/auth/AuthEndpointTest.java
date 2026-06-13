package com.ai_kids_care.v1.auth;

import com.ai_kids_care.BaseIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Characterization tests for the four implemented auth endpoints.
 *
 * These tests record the explicit HTTP contracts of the implemented auth endpoints.
 * Authentication failures must be handled by the auth API itself and must not rely
 * on a protected /error dispatch to manufacture a 401 response.
 *
 * Seed data (from db/initdb/21_users_seed.sql) provides user login_id='admin'.
 * A dedicated test user is upserted in @BeforeEach so tests remain idempotent
 * across container-reuse runs.
 */
@AutoConfigureMockMvc
class AuthEndpointTest extends BaseIntegrationTest {

    private static final String TEST_LOGIN_ID = "baseline-test-auth-user";
    private static final String TEST_PASSWORD  = "Test@Baseline2024";
    private static final String AUTHENTICATION_FAILURE_MESSAGE = "Authentication failed";

    @Autowired private TestRestTemplate rest;
    @Autowired private JdbcTemplate     jdbc;
    @Autowired private PasswordEncoder  passwordEncoder;
    @Autowired private MockMvc           mockMvc;
    @Autowired private ObjectMapper      objectMapper;

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
        jdbc.update("""
                DELETE FROM user_role_assignments
                WHERE user_id = (SELECT user_id FROM users WHERE login_id = ?)
                """, TEST_LOGIN_ID);
        jdbc.update("""
                INSERT INTO user_role_assignments
                    (user_id, role, scope_type, scope_id, status, granted_at)
                SELECT user_id, 'SUPERADMIN', 'PLATFORM', NULL, 'ACTIVE', NOW()
                FROM users
                WHERE login_id = ?
                """, TEST_LOGIN_ID);
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
    void login_kindergartenScopedRole_returnsServerDerivedKindergartenId() {
        jdbc.update("""
                UPDATE user_role_assignments
                SET role = 'TEACHER', scope_type = 'KINDERGARTEN', scope_id = 1
                WHERE user_id = (SELECT user_id FROM users WHERE login_id = ?)
                """, TEST_LOGIN_ID);

        var resp = rest.postForEntity(
                "/api/v1/auth/login",
                Map.of("identifier", TEST_LOGIN_ID, "password", TEST_PASSWORD),
                Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody())
                .containsEntry("role", "TEACHER")
                .containsEntry("kindergartenId", 1);
    }

    @Test
    void login_wrongPassword_returns401() throws Exception {
        assertAuthenticationFailure(
                "/api/v1/auth/login",
                Map.of("identifier", TEST_LOGIN_ID, "password", "definitely-wrong"));
    }

    @Test
    void login_activeUserWithoutActiveRole_returns401() throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        String loginId = "test-no-role-" + suffix;
        jdbc.update("""
                INSERT INTO users (login_id, email, phone, password_hash, status, created_at, updated_at)
                VALUES (?, ?, ?, ?, 'ACTIVE', NOW(), NOW())
                """,
                loginId,
                loginId + "@test-baseline.internal",
                "010" + suffix.substring(0, 8),
                passwordEncoder.encode(TEST_PASSWORD));

        assertAuthenticationFailure(
                "/api/v1/auth/login",
                Map.of("identifier", loginId, "password", TEST_PASSWORD));
    }

    @Test
    void login_activeUserWithMultipleActiveRoles_returns401() throws Exception {
        insertSecondActiveRole();

        assertAuthenticationFailure(
                "/api/v1/auth/login",
                Map.of("identifier", TEST_LOGIN_ID, "password", TEST_PASSWORD));
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

    @Test
    void refresh_userWithoutActiveRole_returns401() throws Exception {
        String refreshToken = loginRefreshToken();
        jdbc.update("""
                UPDATE user_role_assignments
                SET status = 'PENDING'
                WHERE user_id = (SELECT user_id FROM users WHERE login_id = ?)
                """, TEST_LOGIN_ID);

        assertAuthenticationFailure(
                "/api/v1/auth/refresh",
                Map.of("refreshToken", refreshToken));
    }

    @Test
    void refresh_userWithMultipleActiveRoles_returns401() throws Exception {
        String refreshToken = loginRefreshToken();
        insertSecondActiveRole();

        assertAuthenticationFailure(
                "/api/v1/auth/refresh",
                Map.of("refreshToken", refreshToken));
    }

    @Test
    void refresh_pendingUser_returns401() throws Exception {
        String refreshToken = loginRefreshToken();
        jdbc.update("UPDATE users SET status = 'PENDING' WHERE login_id = ?", TEST_LOGIN_ID);

        assertAuthenticationFailure(
                "/api/v1/auth/refresh",
                Map.of("refreshToken", refreshToken));
    }

    @Test
    void refresh_invalidToken_returnsExplicit401() throws Exception {
        assertAuthenticationFailure(
                "/api/v1/auth/refresh",
                Map.of("refreshToken", "not-a-valid-jwt"));
    }

    @Test
    void guardianChildVerification_returnsOnlyGenericMatchResultAndLegacyLookupIsClosed() {
        ResponseEntity<Map> verified = rest.postForEntity(
                "/api/v1/auth/guardian-child-verifications",
                Map.of("childRrnFirst6", "200921", "childRrnBack7", "4037926"),
                Map.class);
        ResponseEntity<Map> notVerified = rest.postForEntity(
                "/api/v1/auth/guardian-child-verifications",
                Map.of("childRrnFirst6", "200921", "childRrnBack7", "0000000"),
                Map.class);
        ResponseEntity<Map> legacyLookup = rest.getForEntity(
                "/api/v1/children/rrn?rrn_First6=200921&rrn_Last7=4037926",
                Map.class);

        assertThat(verified.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(verified.getBody()).containsOnlyKeys("verified").containsEntry("verified", true);
        assertThat(notVerified.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(notVerified.getBody()).containsOnlyKeys("verified").containsEntry("verified", false);
        assertThat(legacyLookup.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void guardianChildVerificationAndRegistration_rejectMalformedRrnBeforePersistence() {
        ResponseEntity<Map> verification = rest.postForEntity(
                "/api/v1/auth/guardian-child-verifications",
                Map.of("childRrnFirst6", "abcdef", "childRrnBack7", "zzzzzzz"),
                Map.class);

        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        String loginId = "test-invalid-rrn-" + suffix;
        Map<String, Object> body = commonRegistrationBody(suffix, loginId, "TEACHER");
        body.put("rrnFirst6", "abcdef");
        body.put("rrnBack7", "zzzzzzz");
        body.put("gender", "MALE");
        body.put("kindergartenId", 1);
        body.put("emergencyContactName", "Emergency Contact");
        body.put("emergencyContactPhone", "01033334444");
        body.put("level", "TEACHER");
        body.put("staffNo", "INVALID-RRN-" + suffix);
        ResponseEntity<Map> registration = rest.postForEntity("/api/v1/auth/register", body, Map.class);

        assertThat(verification.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(registration.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM users WHERE login_id = ?",
                Integer.class,
                loginId
        )).isZero();
    }

    // ── POST /api/v1/auth/register ───────────────────────────────────────────

    @Test
    void register_superadminRole_createsPendingApplicationAndCannotLogin() throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        String loginId = "test-reg-" + suffix;
        Map<String, Object> body = commonRegistrationBody(suffix, loginId, "SUPERADMIN");
        body.put("department", "Test Dept");
        body.put("status", "ACTIVE");
        body.put("scopeType", "KINDERGARTEN");
        body.put("scopeId", 999999);

        ResponseEntity<Map> resp = rest.postForEntity("/api/v1/auth/register", body, Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(resp.getBody()).containsEntry("status", "PENDING");
        Number userIdValue = (Number) resp.getBody().get("userId");
        assertThat(userIdValue).isNotNull();
        long userId = userIdValue.longValue();

        assertThat(statusFor("users", "user_id", userId)).isEqualTo("PENDING");
        assertThat(statusFor("user_role_assignments", "user_id", userId)).isEqualTo("PENDING");
        assertThat(statusFor("superadmins", "user_id", userId)).isEqualTo("PENDING");

        assertAuthenticationFailure(
                "/api/v1/auth/login",
                Map.of("identifier", loginId, "password", TEST_PASSWORD));
    }

    @Test
    void register_platformItAdminRole_isRejectedBeforePersistence() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        String loginId = "test-platform-" + suffix;
        Map<String, Object> body = commonRegistrationBody(suffix, loginId, "PLATFORM_IT_ADMIN");
        body.put("department", "Platform Operations");
        body.put("status", "ACTIVE");

        ResponseEntity<Map> resp = rest.postForEntity("/api/v1/auth/register", body, Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM users WHERE login_id = ?",
                Integer.class,
                loginId
        )).isZero();
    }

    @Test
    void register_kindergartenRoles_createPendingProfileRoleAndMembership() {
        assertKindergartenRoleCreatesPendingApplication("TEACHER", "TEACHER");
        assertKindergartenRoleCreatesPendingApplication("KINDERGARTEN_ADMIN", "DIRECTOR");
        assertKindergartenRoleCreatesPendingApplication("KINDERGARTEN_ADMIN", "VICE_DIRECTOR");
    }

    @Test
    void register_kindergartenRoleLevelMismatch_isRejectedBeforePersistence() {
        assertKindergartenRoleLevelMismatchRejected("KINDERGARTEN_ADMIN", "TEACHER");
        assertKindergartenRoleLevelMismatchRejected("TEACHER", "DIRECTOR");
        assertKindergartenRoleLevelMismatchRejected("TEACHER", "VICE_DIRECTOR");
    }

    @Test
    void register_guardianRole_createsPendingProfileRoleAndMembership() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        String loginId = "test-guardian-" + suffix;
        Map<String, Object> body = commonRegistrationBody(suffix, loginId, "GUARDIAN");
        addApplicantIdentity(body);
        body.put("kindergartenId", 999999);
        body.put("address", "Test address");
        body.put("childRrnFirst6", "200921");
        body.put("childRrnBack7", "4037926");
        body.put("relationship", "FATHER");
        body.put("primaryGuardian", false);

        ResponseEntity<Map> resp = rest.postForEntity("/api/v1/auth/register", body, Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(resp.getBody()).containsEntry("status", "PENDING");
        long userId = ((Number) resp.getBody().get("userId")).longValue();

        assertThat(statusFor("users", "user_id", userId)).isEqualTo("PENDING");
        assertThat(statusFor("user_role_assignments", "user_id", userId)).isEqualTo("PENDING");
        assertThat(statusFor("guardians", "user_id", userId)).isEqualTo("PENDING");
        assertThat(statusFor("user_kindergarten_memberships", "user_id", userId)).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject(
                "SELECT scope_id FROM user_role_assignments WHERE user_id = ?",
                Long.class,
                userId
        )).isEqualTo(1L);
        assertThat(jdbc.queryForObject(
                "SELECT kindergarten_id FROM user_kindergarten_memberships WHERE user_id = ?",
                Long.class,
                userId
        )).isEqualTo(1L);
        assertThat(jdbc.queryForObject("""
                SELECT count(*)
                FROM child_guardian_relationships cgr
                JOIN guardians g ON g.guardian_id = cgr.guardian_id
                WHERE g.user_id = ?
                """, Integer.class, userId)).isEqualTo(1);
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

    private void assertKindergartenRoleCreatesPendingApplication(String role, String level) {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        String loginId = "test-" + role.toLowerCase() + "-" + suffix;
        Map<String, Object> body = commonRegistrationBody(suffix, loginId, role);
        addApplicantIdentity(body);
        body.put("kindergartenId", 1);
        body.put("emergencyContactName", "Emergency Contact");
        body.put("emergencyContactPhone", "01033334444");
        body.put("level", level);
        body.put("staffNo", "TEST-" + suffix);

        ResponseEntity<Map> resp = rest.postForEntity("/api/v1/auth/register", body, Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(resp.getBody()).containsEntry("status", "PENDING");
        long userId = ((Number) resp.getBody().get("userId")).longValue();

        assertThat(statusFor("users", "user_id", userId)).isEqualTo("PENDING");
        assertThat(statusFor("user_role_assignments", "user_id", userId)).isEqualTo("PENDING");
        assertThat(statusFor("teachers", "user_id", userId)).isEqualTo("PENDING");
        assertThat(statusFor("user_kindergarten_memberships", "user_id", userId)).isEqualTo("PENDING");
    }

    private void assertKindergartenRoleLevelMismatchRejected(String role, String level) {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        String loginId = "test-invalid-" + suffix;
        Map<String, Object> body = commonRegistrationBody(suffix, loginId, role);
        addApplicantIdentity(body);
        body.put("kindergartenId", 1);
        body.put("emergencyContactName", "Emergency Contact");
        body.put("emergencyContactPhone", "01033334444");
        body.put("level", level);
        body.put("staffNo", "INVALID-" + suffix);

        ResponseEntity<Map> resp = rest.postForEntity("/api/v1/auth/register", body, Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM users WHERE login_id = ?",
                Integer.class,
                loginId
        )).isZero();
    }

    private Map<String, Object> commonRegistrationBody(String suffix, String loginId, String role) {
        Map<String, Object> body = new HashMap<>();
        body.put("userRole", role);
        body.put("loginId", loginId);
        body.put("email", loginId + "@test-baseline.internal");
        body.put("phone", "010" + suffix.substring(0, 8));
        body.put("password", TEST_PASSWORD);
        body.put("name", "Test Applicant");
        return body;
    }

    private void addApplicantIdentity(Map<String, Object> body) {
        body.put("rrnFirst6", "990101");
        body.put("rrnBack7", "1234567");
        body.put("gender", "MALE");
    }

    private String loginRefreshToken() {
        ResponseEntity<Map> loginResp = rest.postForEntity(
                "/api/v1/auth/login",
                Map.of("identifier", TEST_LOGIN_ID, "password", TEST_PASSWORD),
                Map.class);
        assertThat(loginResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        return (String) loginResp.getBody().get("refreshToken");
    }

    private void insertSecondActiveRole() {
        jdbc.update("""
                INSERT INTO user_role_assignments
                    (user_id, role, scope_type, scope_id, status, granted_at)
                SELECT user_id, 'PLATFORM_IT_ADMIN', 'PLATFORM', NULL, 'ACTIVE', NOW()
                FROM users
                WHERE login_id = ?
                """, TEST_LOGIN_ID);
    }

    private void assertAuthenticationFailure(String path, Map<String, ?> requestBody) throws Exception {
        mockMvc.perform(post(path)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestBody)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value(AUTHENTICATION_FAILURE_MESSAGE))
                .andExpect(jsonPath("$.timestamp").doesNotExist())
                .andExpect(jsonPath("$.path").doesNotExist());
    }

    private String statusFor(String table, String userIdColumn, long userId) {
        return jdbc.queryForObject(
                "SELECT status::text FROM " + table + " WHERE " + userIdColumn + " = ?",
                String.class,
                userId
        );
    }
}
