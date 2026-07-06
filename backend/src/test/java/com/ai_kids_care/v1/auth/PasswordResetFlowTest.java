package com.ai_kids_care.v1.auth;

import com.ai_kids_care.BaseIntegrationTest;
import com.ai_kids_care.v1.service.SmsPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * UX-07 wire-password-management, backend lane tasks 2+3: the enumeration-safe three-step SMS
 * password reset flow ({@code request} → {@code verify} → {@code confirm}).
 *
 * <p>The core deliverable per design.md's "防枚举验收要点" is that {@code request} responds
 * byte-identically for a non-existent account, an existing account without a phone, and an
 * existing account with a phone — differing only in whether {@link SmsPort#send} is invoked —
 * and that {@code verify} returns a uniform {@code 400} for every failure mode (wrong code,
 * dummy challenge, expired/unknown challenge, attempts exceeded).
 */
@AutoConfigureMockMvc
class PasswordResetFlowTest extends BaseIntegrationTest {

    private static final String TEST_PASSWORD = "Test@Baseline2024";
    private static final String NEW_PASSWORD = "BrandNewSecret2024!";
    private static final Pattern SMS_CODE_PATTERN = Pattern.compile("(\\d{6})");

    @Autowired private JdbcTemplate jdbc;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private SmsPort smsPort;

    private String phoneLoginId;
    private String phoneValue;
    private String noPhoneLoginId;
    private String nonExistentLoginId;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        phoneLoginId = "pwreset-phone-" + suffix;
        phoneValue = "010" + suffix.substring(0, 8);
        noPhoneLoginId = "pwreset-nophone-" + suffix;
        nonExistentLoginId = "pwreset-ghost-" + suffix;

        insertActiveUser(phoneLoginId, phoneValue);
        insertActiveUser(noPhoneLoginId, null);
    }

    private void insertActiveUser(String loginId, String phone) {
        String hash = passwordEncoder.encode(TEST_PASSWORD);
        jdbc.update("""
                INSERT INTO users (login_id, email, phone, password_hash, status, created_at, updated_at)
                VALUES (?, ?, ?, ?, 'ACTIVE', NOW(), NOW())
                """,
                loginId, loginId + "@test-baseline.internal", phone, hash);
        jdbc.update("""
                INSERT INTO user_role_assignments (user_id, role, scope_type, scope_id, status, granted_at)
                SELECT user_id, 'SUPERADMIN', 'PLATFORM', NULL, 'ACTIVE', NOW()
                FROM users WHERE login_id = ?
                """, loginId);
        jdbc.update("""
                INSERT INTO superadmins (user_id, name, department, status, created_at, updated_at)
                SELECT user_id, '테스트관리자', 'Test Department', 'ACTIVE', NOW(), NOW()
                FROM users WHERE login_id = ?
                """, loginId);
    }

    // ── request: enumeration safety ──────────────────────────────────────────

    @Test
    void request_nonExistentAccount_returns200WithOpaqueChallengeAndNoSms() throws Exception {
        MvcResult result = request(nonExistentLoginId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.challengeId").isNotEmpty())
                .andExpect(jsonPath("$.expiresAt").isNotEmpty())
                .andReturn();
        assertResponseHasExactlyTwoFields(result);
        verify(smsPort, never()).send(anyString(), anyString());
    }

    @Test
    void request_existingAccountWithoutPhone_returns200SameShapeAndNoSms() throws Exception {
        MvcResult result = request(noPhoneLoginId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.challengeId").isNotEmpty())
                .andExpect(jsonPath("$.expiresAt").isNotEmpty())
                .andReturn();
        assertResponseHasExactlyTwoFields(result);
        verify(smsPort, never()).send(anyString(), anyString());
    }

    @Test
    void request_existingAccountWithPhone_returns200SameShapeAndSendsSms() throws Exception {
        MvcResult result = request(phoneLoginId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.challengeId").isNotEmpty())
                .andExpect(jsonPath("$.expiresAt").isNotEmpty())
                .andReturn();
        assertResponseHasExactlyTwoFields(result);
        verify(smsPort, times(1)).send(eq(phoneValue), anyString());
    }

    @Test
    void request_threeAccountVariants_produceStructurallyIdenticalResponses() throws Exception {
        JsonNode ghost = bodyOf(request(nonExistentLoginId).andExpect(status().isOk()).andReturn());
        JsonNode noPhone = bodyOf(request(noPhoneLoginId).andExpect(status().isOk()).andReturn());
        JsonNode withPhone = bodyOf(request(phoneLoginId).andExpect(status().isOk()).andReturn());

        // Same field set on all three (byte-identical structure); values legitimately differ
        // (each challengeId is a fresh random UUID).
        assertThat(fieldNames(ghost)).containsExactlyInAnyOrder("challengeId", "expiresAt");
        assertThat(fieldNames(noPhone)).containsExactlyInAnyOrder("challengeId", "expiresAt");
        assertThat(fieldNames(withPhone)).containsExactlyInAnyOrder("challengeId", "expiresAt");
    }

    // ── verify: uniform failure shape ────────────────────────────────────────

    @Test
    void verify_dummyChallengeForNonExistentAccount_returns400UniformShape() throws Exception {
        String challengeId = requestChallengeId(nonExistentLoginId);

        verifyCode(challengeId, "000000")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("인증에 실패했습니다."));
    }

    @Test
    void verify_wrongCodeForRealAccount_returns400SameShapeAsDummyChallenge() throws Exception {
        String challengeId = requestChallengeId(phoneLoginId);

        verifyCode(challengeId, "000000")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("인증에 실패했습니다."));
    }

    @Test
    void verify_unknownChallengeId_returns400() throws Exception {
        verifyCode(UUID.randomUUID().toString(), "123456")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("인증에 실패했습니다."));
    }

    @Test
    void verify_correctCode_returns200WithResetToken() throws Exception {
        String challengeId = requestChallengeId(phoneLoginId);
        String code = captureSentCode();

        verifyCode(challengeId, code)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resetToken").isNotEmpty())
                .andExpect(jsonPath("$.expiresAt").isNotEmpty());
    }

    @Test
    void verify_attemptsExceedCap_locksChallengeEvenForSubsequentCorrectCode() throws Exception {
        String challengeId = requestChallengeId(phoneLoginId);
        String code = captureSentCode();

        // 5 wrong attempts locks the challenge (application.yml default max-verify-attempts: 5).
        for (int i = 0; i < 5; i++) {
            verifyCode(challengeId, "000000").andExpect(status().isBadRequest());
        }

        // The 6th attempt, even with the correct code, still fails — the challenge is gone.
        verifyCode(challengeId, code)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("인증에 실패했습니다."));
    }

    // ── confirm: single-use token + session revocation ───────────────────────

    @Test
    void confirm_validTokenAndCompliantPassword_updatesPasswordAndRevokesSessions() throws Exception {
        // Establish a session under the OLD password before resetting it.
        Cookie priorSession = loginSessionCookie(phoneLoginId, TEST_PASSWORD);

        String challengeId = requestChallengeId(phoneLoginId);
        String code = captureSentCode();
        String resetToken = extractResetToken(verifyCode(challengeId, code)
                .andExpect(status().isOk())
                .andReturn());

        confirmReset(resetToken, NEW_PASSWORD)
                .andExpect(status().isOk());

        String storedHash = jdbc.queryForObject(
                "SELECT password_hash FROM users WHERE login_id = ?", String.class, phoneLoginId);
        assertThat(passwordEncoder.matches(NEW_PASSWORD, storedHash)).isTrue();

        // Session that existed before the reset is revoked.
        mockMvc.perform(get("/api/v1/auth/session").cookie(priorSession))
                .andExpect(status().isUnauthorized());

        // New password logs in.
        mockMvc.perform(withRealCsrf(post("/api/v1/auth/login"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("identifier", phoneLoginId, "password", NEW_PASSWORD))))
                .andExpect(status().isOk());
    }

    @Test
    void confirm_resetTokenReplay_secondUseReturns400() throws Exception {
        String challengeId = requestChallengeId(phoneLoginId);
        String code = captureSentCode();
        String resetToken = extractResetToken(verifyCode(challengeId, code)
                .andExpect(status().isOk())
                .andReturn());

        confirmReset(resetToken, NEW_PASSWORD).andExpect(status().isOk());

        // Replay with the same (already-consumed) token.
        confirmReset(resetToken, "AnotherSecret2024!")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").isNotEmpty());
    }

    @Test
    void confirm_invalidToken_returns400AndDoesNotChangePassword() throws Exception {
        confirmReset("not-a-real-token", NEW_PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").isNotEmpty());

        String storedHash = jdbc.queryForObject(
                "SELECT password_hash FROM users WHERE login_id = ?", String.class, phoneLoginId);
        assertThat(passwordEncoder.matches(TEST_PASSWORD, storedHash)).isTrue();
    }

    @Test
    void confirm_nonCompliantNewPassword_returns400() throws Exception {
        String challengeId = requestChallengeId(phoneLoginId);
        String code = captureSentCode();
        String resetToken = extractResetToken(verifyCode(challengeId, code)
                .andExpect(status().isOk())
                .andReturn());

        confirmReset(resetToken, "short1")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").isNotEmpty());
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private org.springframework.test.web.servlet.ResultActions request(String loginId) throws Exception {
        return mockMvc.perform(withRealCsrf(post("/api/v1/auth/password-reset/request"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("loginId", loginId))));
    }

    private org.springframework.test.web.servlet.ResultActions verifyCode(String challengeId, String code) throws Exception {
        return mockMvc.perform(withRealCsrf(post("/api/v1/auth/password-reset/verify"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        Map.of("challengeId", challengeId, "code", code))));
    }

    private org.springframework.test.web.servlet.ResultActions confirmReset(String resetToken, String newPassword) throws Exception {
        return mockMvc.perform(withRealCsrf(post("/api/v1/auth/password-reset/confirm"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        Map.of("resetToken", resetToken, "newPassword", newPassword))));
    }

    private String requestChallengeId(String loginId) throws Exception {
        MvcResult result = request(loginId).andExpect(status().isOk()).andReturn();
        return bodyOf(result).get("challengeId").asText();
    }

    /** Captures the most recently sent SMS text and extracts its 6-digit verification code. */
    private String captureSentCode() {
        org.mockito.ArgumentCaptor<String> textCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(smsPort, times(1)).send(eq(phoneValue), textCaptor.capture());
        org.mockito.Mockito.clearInvocations(smsPort);
        Matcher matcher = SMS_CODE_PATTERN.matcher(textCaptor.getValue());
        assertThat(matcher.find()).as("SMS text should contain a 6-digit code").isTrue();
        return matcher.group(1);
    }

    private String extractResetToken(MvcResult result) throws Exception {
        return bodyOf(result).get("resetToken").asText();
    }

    private JsonNode bodyOf(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private Iterable<String> fieldNames(JsonNode node) {
        return () -> node.fieldNames();
    }

    private void assertResponseHasExactlyTwoFields(MvcResult result) throws Exception {
        JsonNode body = bodyOf(result);
        assertThat(fieldNames(body)).containsExactlyInAnyOrder("challengeId", "expiresAt");
    }

    private Cookie loginSessionCookie(String loginId, String password) throws Exception {
        MvcResult login = mockMvc.perform(withRealCsrf(post("/api/v1/auth/login"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("identifier", loginId, "password", password))))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("AI_KIDS_CARE_SESSION"))
                .andReturn();
        return login.getResponse().getCookie("AI_KIDS_CARE_SESSION");
    }

    private MockHttpServletRequestBuilder withRealCsrf(
            MockHttpServletRequestBuilder request) throws Exception {
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
}
