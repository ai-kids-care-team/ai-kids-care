package com.ai_kids_care.v1.security;

import com.ai_kids_care.BaseIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SPEC-0001 §362 / §390：错误响应中的敏感数据 absence（负向测试套件「error」腿）。
 *
 * <p>§390 验证「response、错误和审计中不存在 S0」。其中：
 * <ul>
 *   <li>response 腿 → {@code SensitiveResponseContractTest} / {@code PublishedOpenApiContractTest}；</li>
 *   <li>审计腿 → {@code SecurityAuditIntegrationTest.auditRecords_doNotContainS0}；</li>
 *   <li>error 腿 → 本类。</li>
 * </ul>
 *
 * <p>本类驱动具代表性的错误响应（400 校验失败 / 400 报文不可解析 / 403 CSRF 缺失 / 401 未认证），
 * 断言响应体永不回显提交的明文口令或 RRN，也不暴露 S0 字段名（passwordHash / rrnEncrypted /
 * camera ciphertext / pushToken / 内部 storageUri 等）或服务端内部信息（stacktrace / exception）。
 *
 * <p>当前实现下错误体均为固定的 {@code {"error": "..."}} 或空体，故本类是**回归护栏**：一旦未来开启
 * {@code server.error.include-binding-errors / include-message / include-stacktrace}，或某 handler
 * 改为回显 {@code getRejectedValue()} / 原始请求体，本测试即失败。隐藏 404 的 {@code {"error":
 * "Resource not found"}} 契约由 GuardianChild / AdminApproval 集成测试覆盖，此处不重复。
 */
@AutoConfigureMockMvc
class ErrorResponseSensitiveDataIntegrationTest extends BaseIntegrationTest {

    /** 植入请求的「金丝雀」明文，绝不应出现在任何错误响应体中。 */
    private static final String PASSWORD_CANARY = "CANARY-PLAINTEXT-PASSWORD-2026";
    private static final String RRN_CANARY = "CANARY-RRN-BACK7";
    private static final String CHILD_RRN_CANARY = "CANARY-CHILD-RRN-BACK7";

    /** S0 / 内部存储字段名（camelCase + snake_case），错误体中不得出现。 */
    private static final String[] SENSITIVE_TOKENS = {
            "passwordHash", "password_hash",
            "rrnEncrypted", "rrn_encrypted",
            "rrnHash", "rrn_hash",
            "ciphertext", "keyVersion", "key_version",
            "streamUser", "stream_user",
            "sourceUrl", "source_url",
            "storageUri", "storage_uri",
            "pushToken", "push_token"
    };

    /** 服务端内部信息的 JSON key 形式，禁止泄露（stacktrace / 异常类名）。 */
    private static final String[] INTERNAL_TOKENS = {
            "\"trace\"", "\"exception\"", "\"stackTrace\""
    };

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    // ── 400 校验失败：不回显提交的口令 / RRN ─────────────────────────────────────

    @Test
    void registerValidationError_doesNotEchoSubmittedSecrets() throws Exception {
        // 故意构造校验失败的注册请求：email / RRN 后段格式非法触发 400，并把明文口令与
        // RRN 金丝雀塞进对应字段，验证 rejectedValue 与整体序列化均不被回显。
        Map<String, Object> body = new HashMap<>();
        body.put("email", "not-an-email-" + RRN_CANARY);   // @Email 失败（rejectedValue 含金丝雀）
        body.put("rrnBack7", RRN_CANARY);                  // @Pattern(\\d{7}) 失败
        body.put("childRrnBack7", CHILD_RRN_CANARY);       // @Pattern(\\d{7}) 失败
        body.put("password", PASSWORD_CANARY);             // @NotBlank 通过，但属敏感明文
        // 省略 userRole / loginId / name / phone → 触发多个 @NotNull / @NotBlank 失败

        MvcResult result = mockMvc.perform(withRealCsrf(post("/api/v1/auth/register"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andReturn();

        assertNoSensitiveLeakage(result);
    }

    // ── 400 报文不可解析：错误体不回显原始字节、不暴露内部信息 ───────────────────

    @Test
    void malformedRequestBody_errorResponseExposesNoSecretsOrInternals() throws Exception {
        // 截断的非法 JSON（缺右括号）→ HttpMessageNotReadableException → 400。
        // 校验：解析错误既不回显原始报文（含口令金丝雀），也不带 stacktrace / exception。
        String malformed = "{\"identifier\":\"err-test\",\"password\":\"" + PASSWORD_CANARY + "\"";

        MvcResult result = mockMvc.perform(withRealCsrf(post("/api/v1/auth/login"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(malformed))
                .andExpect(status().isBadRequest())
                .andReturn();

        assertNoSensitiveLeakage(result);
    }

    // ── 403 CSRF 缺失：不回显提交的口令 ─────────────────────────────────────────

    @Test
    void csrfRejectedLoginError_doesNotEchoSubmittedPassword() throws Exception {
        // 不带 CSRF token 的写请求 → 403（SecurityAuditAccessDeniedHandler，固定 {"error":"Access denied"}）。
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "identifier", "err-test-user",
                                "password", PASSWORD_CANARY))))
                .andExpect(status().isForbidden())
                .andReturn();

        assertNoSensitiveLeakage(result);
    }

    // ── 401 未认证：错误体不暴露 S0 / 服务端内部 ────────────────────────────────

    @Test
    void unauthenticatedError_exposesNoSensitiveData() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/classes"))
                .andExpect(status().isUnauthorized())
                .andReturn();

        assertNoSensitiveLeakage(result);
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private void assertNoSensitiveLeakage(MvcResult result) throws Exception {
        String body = result.getResponse().getContentAsString();
        String haystack = body == null ? "" : body;

        assertThat(haystack)
                .as("error response must not echo submitted plaintext secrets")
                .doesNotContain(PASSWORD_CANARY)
                .doesNotContain(RRN_CANARY)
                .doesNotContain(CHILD_RRN_CANARY);

        for (String token : SENSITIVE_TOKENS) {
            assertThat(haystack)
                    .as("error response must not expose S0/internal field name: %s", token)
                    .doesNotContain(token);
        }
        for (String token : INTERNAL_TOKENS) {
            assertThat(haystack)
                    .as("error response must not expose server internals: %s", token)
                    .doesNotContain(token);
        }
    }

    private MockHttpServletRequestBuilder withRealCsrf(MockHttpServletRequestBuilder request) throws Exception {
        MvcResult csrf = mockMvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isOk())
                .andReturn();
        String token = objectMapper.readTree(csrf.getResponse().getContentAsString())
                .get("token").asText();
        return request
                .cookie(csrf.getResponse().getCookie("XSRF-TOKEN"))
                .header("X-XSRF-TOKEN", token);
    }
}
