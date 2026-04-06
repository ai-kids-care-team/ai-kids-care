package com.ai_kids_care.v1.service.sms;

import com.ai_kids_care.v1.config.SolapiProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class SolapiSmsSender implements SmsSender {
    private static final String SUCCESS_ACCEPTED = "2000";
    private static final String SUCCESS_DELIVERED = "3000";
    private static final String SUCCESS_CONFIRMED = "4000";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final SolapiProperties properties;
    private final SolapiErrorMapper errorMapper;
    private final SecureRandom secureRandom = new SecureRandom();

    public SolapiSmsSender(
            RestTemplateBuilder restTemplateBuilder,
            ObjectMapper objectMapper,
            SolapiProperties properties,
            SolapiErrorMapper errorMapper
    ) {
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.errorMapper = errorMapper;
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofMillis(Math.max(1000, properties.getConnectTimeoutMs())))
                .setReadTimeout(Duration.ofMillis(Math.max(1000, properties.getReadTimeoutMs())))
                .build();
    }

    @Override
    public SmsSendResult sendSms(String to, String text, String title) {
        if (!properties.isEnabled()) {
            return SmsSendResult.failure(
                    "LOCAL_DISABLED",
                    "solapi.enabled is false",
                    null,
                    SmsFailureCategory.NOT_CONFIGURED
            );
        }
        if (isBlank(properties.getApiKey()) || isBlank(properties.getApiSecret()) || isBlank(properties.getSenderNumber())) {
            return SmsSendResult.failure(
                    "LOCAL_MISCONFIGURED",
                    "solapi api-key/api-secret/sender-number is missing",
                    null,
                    SmsFailureCategory.NOT_CONFIGURED
            );
        }

        String toNumber = normalizePhone(to);
        String fromNumber = normalizePhone(properties.getSenderNumber());
        if (toNumber.isBlank()) {
            return SmsSendResult.failure(
                    "LOCAL_INVALID_RECIPIENT",
                    "recipient phone is blank",
                    null,
                    SmsFailureCategory.INVALID_RECIPIENT
            );
        }
        if (fromNumber.isBlank()) {
            return SmsSendResult.failure(
                    "LOCAL_INVALID_SENDER",
                    "sender phone is blank",
                    null,
                    SmsFailureCategory.INVALID_SENDER
            );
        }
        if (isBlank(text)) {
            return SmsSendResult.failure(
                    "LOCAL_EMPTY_TEXT",
                    "sms body is empty",
                    null,
                    SmsFailureCategory.PROVIDER_REJECTED
            );
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", buildAuthorizationHeader(properties.getApiKey(), properties.getApiSecret()));
        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(buildRequestBody(toNumber, fromNumber, text, title), headers);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(buildSendEndpoint(), requestEntity, String.class);
            return parseApiResult(response.getStatusCode().value(), response.getBody());
        } catch (HttpStatusCodeException ex) {
            return parseApiError(ex.getStatusCode().value(), ex.getResponseBodyAsString(), ex.getMessage());
        } catch (ResourceAccessException ex) {
            return SmsSendResult.failure(
                    "NETWORK_ERROR",
                    nullSafe(ex.getMessage()),
                    null,
                    SmsFailureCategory.NETWORK_ERROR
            );
        } catch (Exception ex) {
            return SmsSendResult.failure(
                    "UNKNOWN_ERROR",
                    nullSafe(ex.getMessage()),
                    null,
                    SmsFailureCategory.UNKNOWN
            );
        }
    }

    private String buildSendEndpoint() {
        String baseUrl = properties.getBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "https://api.solapi.com";
        }
        if (baseUrl.endsWith("/")) {
            return baseUrl + "messages/v4/send-many/detail";
        }
        return baseUrl + "/messages/v4/send-many/detail";
    }

    private Map<String, Object> buildRequestBody(String to, String from, String text, String title) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("to", to);
        message.put("from", from);
        message.put("text", text);
        message.put("type", "SMS");
        if (!isBlank(title)) {
            message.put("subject", title);
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("messages", List.of(message));
        payload.put("allowDuplicates", false);
        return payload;
    }

    private SmsSendResult parseApiResult(int httpStatus, String body) {
        try {
            JsonNode root = objectMapper.readTree(nullSafe(body));
            String statusCode = firstNonBlank(
                    readText(root, "statusCode"),
                    readText(root.path("messageList").path(0), "statusCode")
            );
            String statusMessage = firstNonBlank(
                    readText(root, "statusMessage"),
                    readText(root.path("messageList").path(0), "statusMessage"),
                    readText(root, "errorMessage"),
                    "SOLAPI response"
            );
            String messageId = firstNonBlank(
                    readText(root, "messageId"),
                    readText(root.path("messageList").path(0), "messageId")
            );

            if (isSuccessCode(statusCode)) {
                return SmsSendResult.success(statusCode, statusMessage, messageId);
            }

            SmsFailureCategory category = errorMapper.map(statusCode, httpStatus);
            return SmsSendResult.failure(statusCode, statusMessage, messageId, category);
        } catch (Exception ex) {
            SmsFailureCategory category = errorMapper.map(null, httpStatus);
            return SmsSendResult.failure(
                    "PARSE_ERROR",
                    "failed to parse solapi response: " + nullSafe(ex.getMessage()),
                    null,
                    category
            );
        }
    }

    private SmsSendResult parseApiError(int httpStatus, String body, String fallbackMessage) {
        try {
            JsonNode root = objectMapper.readTree(nullSafe(body));
            String code = firstNonBlank(
                    readText(root, "errorCode"),
                    readText(root, "statusCode"),
                    readText(root.path("messageList").path(0), "statusCode")
            );
            String message = firstNonBlank(readText(root, "errorMessage"), readText(root, "statusMessage"), fallbackMessage);
            SmsFailureCategory category = errorMapper.map(code, httpStatus);
            return SmsSendResult.failure(code, message, null, category);
        } catch (Exception ex) {
            SmsFailureCategory category = errorMapper.map(null, httpStatus);
            return SmsSendResult.failure(
                    "HTTP_" + httpStatus,
                    firstNonBlank(fallbackMessage, nullSafe(ex.getMessage())),
                    null,
                    category
            );
        }
    }

    private String buildAuthorizationHeader(String apiKey, String apiSecret) {
        String date = Instant.now().toString();
        String salt = randomHex(24);
        String signature = hmacSha256Hex(apiSecret, date + salt);
        return "HMAC-SHA256 apiKey=" + apiKey + ", date=" + date + ", salt=" + salt + ", signature=" + signature;
    }

    private String hmacSha256Hex(String secret, String message) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKeySpec);
            byte[] bytes = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                builder.append(String.format(Locale.ROOT, "%02x", b));
            }
            return builder.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("failed to generate solapi signature", ex);
        }
    }

    private String randomHex(int byteLength) {
        byte[] bytes = new byte[Math.max(12, byteLength)];
        secureRandom.nextBytes(bytes);
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            builder.append(String.format(Locale.ROOT, "%02x", b));
        }
        return builder.toString();
    }

    private boolean isSuccessCode(String code) {
        return SUCCESS_ACCEPTED.equals(code)
                || SUCCESS_DELIVERED.equals(code)
                || SUCCESS_CONFIRMED.equals(code);
    }

    private String readText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private String normalizePhone(String phone) {
        if (phone == null) {
            return "";
        }
        return phone.replaceAll("[^0-9]", "");
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String nullSafe(String value) {
        return value == null ? "" : value;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (!isBlank(value)) {
                return value;
            }
        }
        return "";
    }
}
