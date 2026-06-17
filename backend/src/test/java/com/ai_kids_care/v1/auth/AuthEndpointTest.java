package com.ai_kids_care.v1.auth;

import com.ai_kids_care.BaseIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
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
                DELETE FROM user_kindergarten_memberships
                WHERE user_id = (SELECT user_id FROM users WHERE login_id = ?)
                """, TEST_LOGIN_ID);
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
    void login_validCredentials_returnsSessionProfileWithoutBearerTokens() throws Exception {
        mockMvc.perform(withRealCsrf(post("/api/v1/auth/login"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("identifier", TEST_LOGIN_ID, "password", TEST_PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("AI_KIDS_CARE_SESSION"))
                .andExpect(jsonPath("$.loginId").value(TEST_LOGIN_ID))
                .andExpect(jsonPath("$.effectiveRole").value("SUPERADMIN"))
                .andExpect(jsonPath("$.scopeType").value("PLATFORM"))
                .andExpect(jsonPath("$.scopeId").doesNotExist())
                .andExpect(jsonPath("$.accessToken").doesNotExist())
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andExpect(jsonPath("$.token").doesNotExist());
    }

    @Test
    void session_afterLogin_returnsServerDerivedProfile() throws Exception {
        Cookie sessionCookie = loginSessionCookie();

        mockMvc.perform(get("/api/v1/auth/session").cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.loginId").value(TEST_LOGIN_ID))
                .andExpect(jsonPath("$.effectiveRole").value("SUPERADMIN"))
                .andExpect(jsonPath("$.scopeType").value("PLATFORM"));
    }

    @Test
    void session_withoutLogin_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/auth/session"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void login_kindergartenScopedRoleWithoutMembership_returns401() throws Exception {
        jdbc.update("""
                UPDATE user_role_assignments
                SET role = 'TEACHER', scope_type = 'KINDERGARTEN', scope_id = 1
                WHERE user_id = (SELECT user_id FROM users WHERE login_id = ?)
                """, TEST_LOGIN_ID);

        assertAuthenticationFailure(
                "/api/v1/auth/login",
                Map.of("identifier", TEST_LOGIN_ID, "password", TEST_PASSWORD));
    }

    @Test
    void login_kindergartenScopedRoleWithMatchingMembership_returnsServerDerivedScope() throws Exception {
        jdbc.update("""
                UPDATE user_role_assignments
                SET role = 'TEACHER', scope_type = 'KINDERGARTEN', scope_id = 1
                WHERE user_id = (SELECT user_id FROM users WHERE login_id = ?)
                """, TEST_LOGIN_ID);
        insertActiveMembership(1L);

        mockMvc.perform(withRealCsrf(post("/api/v1/auth/login"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("identifier", TEST_LOGIN_ID, "password", TEST_PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.effectiveRole").value("TEACHER"))
                .andExpect(jsonPath("$.scopeType").value("KINDERGARTEN"))
                .andExpect(jsonPath("$.scopeId").value(1));
    }

    @Test
    void login_platformRoleWithActiveMembership_returns401() throws Exception {
        insertActiveMembership(1L);

        assertAuthenticationFailure(
                "/api/v1/auth/login",
                Map.of("identifier", TEST_LOGIN_ID, "password", TEST_PASSWORD));
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

    // 注：原 login_activeUserWithMultipleActiveRoles_returns401 已移除——ADR-0021 的
    // uq_ura_one_active_per_user 部分唯一约束使「同一用户多条 ACTIVE role」在 DB 层不可能创建；
    // 该 DB 拒绝由 V2SchemaConstraintIntegrationTest 覆盖，resolveIdentity 的 count 校验作为纵深防御保留。

    // ── Session lifecycle ────────────────────────────────────────────────────

    @Test
    void csrfEndpoint_returnsReadableCookieAndHeaderContract() throws Exception {
        mockMvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("XSRF-TOKEN"))
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.headerName").value("X-XSRF-TOKEN"));
    }

    @Test
    void refresh_isClosed() throws Exception {
        // /auth/refresh no longer exists and is not whitelisted: default-deny returns
        // 401 for an anonymous caller (with valid CSRF), not an anonymous 404.
        mockMvc.perform(withRealCsrf(post("/api/v1/auth/refresh"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"obsolete\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logout_invalidatesCurrentSession() throws Exception {
        Cookie sessionCookie = loginSessionCookie();

        mockMvc.perform(withRealCsrf(post("/api/v1/auth/logout"))
                        .cookie(sessionCookie))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/auth/session").cookie(sessionCookie))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedRequest_afterRoleRevocationReturns401AndInvalidatesSession() throws Exception {
        Cookie sessionCookie = loginSessionCookie();
        jdbc.update("""
                UPDATE user_role_assignments
                SET status = 'DISABLED', revoked_at = NOW()
                WHERE user_id = (SELECT user_id FROM users WHERE login_id = ?)
                """, TEST_LOGIN_ID);

        mockMvc.perform(get("/api/v1/ai_models").cookie(sessionCookie))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/auth/session").cookie(sessionCookie))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedRequest_afterUserDisabledReturns401() throws Exception {
        Cookie sessionCookie = loginSessionCookie();
        jdbc.update("UPDATE users SET status = 'DISABLED' WHERE login_id = ?", TEST_LOGIN_ID);

        mockMvc.perform(get("/api/v1/ai_models").cookie(sessionCookie))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/auth/session").cookie(sessionCookie))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedRequest_afterMembershipEndedReturns401() throws Exception {
        configureTeacherRole(1L);
        Cookie sessionCookie = loginSessionCookie();
        jdbc.update("""
                UPDATE user_kindergarten_memberships SET status = 'DISABLED'
                WHERE user_id = (SELECT user_id FROM users WHERE login_id = ?)
                """, TEST_LOGIN_ID);

        mockMvc.perform(get("/api/v1/auth/session").cookie(sessionCookie))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutAll_revokesEverySessionForTheUser() throws Exception {
        Cookie first = loginSessionCookie();
        Cookie second = loginSessionCookie();
        mockMvc.perform(get("/api/v1/auth/session").cookie(first))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/auth/session").cookie(second))
                .andExpect(status().isOk());

        mockMvc.perform(withRealCsrf(post("/api/v1/auth/logout-all")).cookie(first))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/auth/session").cookie(first))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/auth/session").cookie(second))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void publishedVos_includeTheirPrimaryId() throws Exception {
        // Platform metadata: AiModelVO.modelId (default test user is SUPERADMIN).
        Cookie platformSession = loginSessionCookie();
        long modelId = jdbc.queryForObject(
                "SELECT model_id FROM ai_models ORDER BY model_id LIMIT 1", Long.class);
        mockMvc.perform(get("/api/v1/ai_models/{id}", modelId).cookie(platformSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modelId").value((int) modelId));

        // Tenant resources: ClassVO.classId / RoomVO.roomId / DetectionSessionVO.sessionId.
        configureKindergartenAdminRole(1L);
        Cookie adminSession = loginSessionCookie();
        long classId = jdbc.queryForObject(
                "SELECT class_id FROM classes WHERE kindergarten_id = 1 ORDER BY class_id LIMIT 1", Long.class);
        long roomId = jdbc.queryForObject(
                "SELECT room_id FROM rooms WHERE kindergarten_id = 1 ORDER BY room_id LIMIT 1", Long.class);
        long sessionId = jdbc.queryForObject(
                "SELECT session_id FROM detection_sessions WHERE kindergarten_id = 1 ORDER BY session_id LIMIT 1", Long.class);
        mockMvc.perform(get("/api/v1/classes/{id}", classId).cookie(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.classId").value((int) classId));
        mockMvc.perform(get("/api/v1/rooms/{id}", roomId).cookie(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roomId").value((int) roomId));
        mockMvc.perform(get("/api/v1/detection_sessions/{id}", sessionId).cookie(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value((int) sessionId));
    }

    @Test
    void platformRole_selectsValidatedTenantContextWithoutChangingRole() throws Exception {
        Cookie sessionCookie = loginSessionCookie();

        mockMvc.perform(withRealCsrf(post("/api/v1/auth/session/tenant-context"))
                        .cookie(sessionCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kindergartenId\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.selectedKindergartenId").value(1));

        mockMvc.perform(get("/api/v1/auth/session").cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.effectiveRole").value("SUPERADMIN"))
                .andExpect(jsonPath("$.scopeType").value("PLATFORM"));
    }

    @Test
    void kindergartenRole_cannotSelectPlatformTenantContext() throws Exception {
        configureTeacherRole(1L);
        Cookie sessionCookie = loginSessionCookie();

        mockMvc.perform(withRealCsrf(post("/api/v1/auth/session/tenant-context"))
                        .cookie(sessionCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kindergartenId\":1}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void platformTenantSelection_doesNotGrantCameraRead() throws Exception {
        Cookie sessionCookie = loginSessionCookie();
        mockMvc.perform(withRealCsrf(post("/api/v1/auth/session/tenant-context"))
                        .cookie(sessionCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kindergartenId\":1}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/camera_streams?kindergartenId=1")
                        .cookie(sessionCookie))
                .andExpect(status().isForbidden());
    }

    @Test
    void kindergartenRole_cannotReadPlatformAiMetadata() throws Exception {
        configureTeacherRole(1L);
        Cookie sessionCookie = loginSessionCookie();

        mockMvc.perform(get("/api/v1/ai_models").cookie(sessionCookie))
                .andExpect(status().isForbidden());
    }

    @Test
    void tenantClassQueries_hideForeignTenantResources() throws Exception {
        long foreignKindergartenId = insertActiveKindergarten();
        long foreignClassId = insertActiveClass(foreignKindergartenId);
        configureTeacherRole(1L);
        Cookie sessionCookie = loginSessionCookie();

        MvcResult list = mockMvc.perform(get("/api/v1/classes").cookie(sessionCookie))
                .andExpect(status().isOk())
                .andReturn();
        boolean containsForeignTenant = StreamSupport.stream(
                        objectMapper.readTree(list.getResponse().getContentAsString())
                                .path("content")
                                .spliterator(),
                        false)
                .anyMatch(item -> item.path("kindergartenId").asLong() == foreignKindergartenId);
        assertThat(containsForeignTenant).isFalse();

        mockMvc.perform(get("/api/v1/classes/{id}", foreignClassId)
                        .cookie(sessionCookie))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Resource not found"));
    }

    @Test
    void tenantWrite_rejectsClientKindergartenOverride() throws Exception {
        long foreignKindergartenId = insertActiveKindergarten();
        configureKindergartenAdminRole(1L);
        Cookie sessionCookie = loginSessionCookie();

        Map<String, Object> body = new HashMap<>();
        body.put("kindergartenId", foreignKindergartenId);
        body.put("name", "Foreign tenant class");
        body.put("grade", "AGE_5");
        body.put("academicYear", 2026);
        body.put("startDate", "2026-03-01");
        body.put("endDate", "2027-02-28");
        body.put("status", "ACTIVE");

        mockMvc.perform(withRealCsrf(post("/api/v1/classes"))
                        .cookie(sessionCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Resource not found"));
    }

    @Test
    void cameraList_rejectsClientKindergartenOverride() throws Exception {
        long foreignKindergartenId = insertActiveKindergarten();
        configureKindergartenAdminRole(1L);
        Cookie sessionCookie = loginSessionCookie();

        mockMvc.perform(get("/api/v1/cctv_cameras")
                        .param("kindergartenId", String.valueOf(foreignKindergartenId))
                        .cookie(sessionCookie))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Resource not found"));
    }

    @Test
    void teacher_cannotReadSurveillanceResources() throws Exception {
        configureTeacherRole(1L);
        Cookie sessionCookie = loginSessionCookie();

        // CCTV cameras, stream config and detection sessions are not in the TEACHER
        // actor matrix; only KINDERGARTEN_ADMIN may read them, so a teacher is denied.
        // (cctv_cameras/camera_streams require a kindergartenId param; supply it so the
        // request reaches method authorization instead of failing parameter binding.)
        mockMvc.perform(get("/api/v1/cctv_cameras").param("kindergartenId", "1").cookie(sessionCookie))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/camera_streams").param("kindergartenId", "1").cookie(sessionCookie))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/detection_sessions").cookie(sessionCookie))
                .andExpect(status().isForbidden());
    }

    @Test
    void guardianChildVerification_returnsOnlyGenericMatchResultAndLegacyLookupIsClosed() {
        ResponseEntity<Map> verified = postWithCsrf(
                "/api/v1/auth/guardian-child-verifications",
                Map.of("childRrnFirst6", "200921", "childRrnBack7", "4037926"));
        ResponseEntity<Map> notVerified = postWithCsrf(
                "/api/v1/auth/guardian-child-verifications",
                Map.of("childRrnFirst6", "200921", "childRrnBack7", "0000000"));
        ResponseEntity<Map> legacyLookup = rest.getForEntity(
                "/api/v1/children/rrn?rrn_First6=200921&rrn_Last7=4037926",
                Map.class);

        assertThat(verified.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(verified.getBody()).containsOnlyKeys("verified").containsEntry("verified", true);
        assertThat(notVerified.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(notVerified.getBody()).containsOnlyKeys("verified").containsEntry("verified", false);
        // Legacy /children/rrn lookup is closed and not whitelisted: anonymous default-deny 401.
        assertThat(legacyLookup.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void guardianChildVerificationAndRegistration_rejectMalformedRrnBeforePersistence() {
        ResponseEntity<Map> verification = postWithCsrf(
                "/api/v1/auth/guardian-child-verifications",
                Map.of("childRrnFirst6", "abcdef", "childRrnBack7", "zzzzzzz"));

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
        ResponseEntity<Map> registration = postWithCsrf("/api/v1/auth/register", body);

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

        ResponseEntity<Map> resp = postWithCsrf("/api/v1/auth/register", body);

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

        ResponseEntity<Map> resp = postWithCsrf("/api/v1/auth/register", body);

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

        ResponseEntity<Map> resp = postWithCsrf("/api/v1/auth/register", body);

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
        addApplicantIdentity(body, suffix);
        body.put("kindergartenId", 1);
        body.put("emergencyContactName", "Emergency Contact");
        body.put("emergencyContactPhone", "01033334444");
        body.put("level", level);
        body.put("staffNo", "TEST-" + suffix);

        ResponseEntity<Map> resp = postWithCsrf("/api/v1/auth/register", body);

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
        // Note: level mismatch is rejected before RRN hash is computed, so no uniqueness concern.
        String loginId = "test-invalid-" + suffix;
        Map<String, Object> body = commonRegistrationBody(suffix, loginId, role);
        addApplicantIdentity(body);
        body.put("kindergartenId", 1);
        body.put("emergencyContactName", "Emergency Contact");
        body.put("emergencyContactPhone", "01033334444");
        body.put("level", level);
        body.put("staffNo", "INVALID-" + suffix);

        ResponseEntity<Map> resp = postWithCsrf("/api/v1/auth/register", body);

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

    /**
     * Suffix-based variant: derives a unique 7-digit back7 from the suffix so that
     * multiple registrations in the same test do not collide on the rrn_hash UNIQUE index.
     */
    private void addApplicantIdentity(Map<String, Object> body, String suffix) {
        body.put("rrnFirst6", "990101");
        // Use last 7 numeric digits of the suffix hash to form a unique back7.
        // suffix is alphanumeric (UUID-derived). Use Math.abs(hashCode()) % 10_000_000.
        String back7 = String.format("%07d", Math.abs(suffix.hashCode()) % 10_000_000);
        body.put("rrnBack7", back7);
        body.put("gender", "MALE");
    }

    private Cookie loginSessionCookie() throws Exception {
        MvcResult login = mockMvc.perform(withRealCsrf(post("/api/v1/auth/login"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("identifier", TEST_LOGIN_ID, "password", TEST_PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("AI_KIDS_CARE_SESSION"))
                .andReturn();
        return login.getResponse().getCookie("AI_KIDS_CARE_SESSION");
    }

    private void insertActiveMembership(long kindergartenId) {
        jdbc.update("""
                INSERT INTO user_kindergarten_memberships
                    (user_id, kindergarten_id, status, joined_at, created_at, updated_at)
                SELECT user_id, ?, 'ACTIVE', NOW(), NOW(), NOW()
                FROM users
                WHERE login_id = ?
                """, kindergartenId, TEST_LOGIN_ID);
    }

    private void configureTeacherRole(long kindergartenId) {
        configureKindergartenRole("TEACHER", kindergartenId);
    }

    private void configureKindergartenAdminRole(long kindergartenId) {
        configureKindergartenRole("KINDERGARTEN_ADMIN", kindergartenId);
    }

    private void configureKindergartenRole(String role, long kindergartenId) {
        jdbc.update("""
                UPDATE user_role_assignments
                SET role = ?::user_role_enum, scope_type = 'KINDERGARTEN', scope_id = ?
                WHERE user_id = (SELECT user_id FROM users WHERE login_id = ?)
                """, role, kindergartenId, TEST_LOGIN_ID);
        insertActiveMembership(kindergartenId);
    }

    private long insertActiveKindergarten() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        return jdbc.queryForObject("""
                INSERT INTO kindergartens
                    (name, address, region_code, code, business_registration_no,
                     contact_name, contact_phone, contact_email, status, created_at, updated_at)
                VALUES (?, ?, 'TEST', ?, ?, 'Contact', '01000000000', ?, 'ACTIVE', NOW(), NOW())
                RETURNING kindergarten_id
                """,
                Long.class,
                "Foreign Kindergarten " + suffix,
                "Foreign address",
                "FOREIGN-" + suffix,
                "BRN-" + suffix,
                "foreign-" + suffix + "@test.internal");
    }

    private long insertActiveClass(long kindergartenId) {
        return jdbc.queryForObject("""
                INSERT INTO classes
                    (kindergarten_id, name, grade, academic_year, start_date, end_date,
                     status, created_at, updated_at)
                VALUES (?, 'Foreign Class', 'AGE_5', 2026, '2026-03-01', '2027-02-28',
                        'ACTIVE', NOW(), NOW())
                RETURNING class_id
                """,
                Long.class,
                kindergartenId);
    }

    private void assertAuthenticationFailure(String path, Map<String, ?> requestBody) throws Exception {
        mockMvc.perform(withRealCsrf(post(path))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestBody)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value(AUTHENTICATION_FAILURE_MESSAGE))
                .andExpect(jsonPath("$.timestamp").doesNotExist())
                .andExpect(jsonPath("$.path").doesNotExist());
    }

    private MockHttpServletRequestBuilder withRealCsrf(
            MockHttpServletRequestBuilder request
    ) throws Exception {
        MvcResult csrf = mockMvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("XSRF-TOKEN"))
                .andReturn();
        String token = objectMapper.readTree(csrf.getResponse().getContentAsString())
                .get("token")
                .asText();
        return request
                .cookie(csrf.getResponse().getCookie("XSRF-TOKEN"))
                .header("X-XSRF-TOKEN", token);
    }

    private ResponseEntity<Map> postWithCsrf(String path, Map<String, ?> requestBody) {
        ResponseEntity<Map> csrf = rest.getForEntity("/api/v1/auth/csrf", Map.class);
        assertThat(csrf.getStatusCode()).isEqualTo(HttpStatus.OK);
        String token = (String) csrf.getBody().get("token");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(HttpHeaders.COOKIE, "XSRF-TOKEN=" + token);
        headers.set("X-XSRF-TOKEN", token);
        return rest.exchange(
                path,
                HttpMethod.POST,
                new HttpEntity<>(requestBody, headers),
                Map.class
        );
    }

    private String statusFor(String table, String userIdColumn, long userId) {
        return jdbc.queryForObject(
                "SELECT status::text FROM " + table + " WHERE " + userIdColumn + " = ?",
                String.class,
                userId
        );
    }
}
