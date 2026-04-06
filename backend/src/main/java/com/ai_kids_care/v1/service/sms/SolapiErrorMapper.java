package com.ai_kids_care.v1.service.sms;

import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class SolapiErrorMapper {

    private static final Set<String> AUTH_CODES = Set.of(
            "InvalidAPIKey", "SignatureDoesNotMatch", "RequestTimeTooSkewed", "DuplicatedSignature", "1020"
    );

    private static final Set<String> INVALID_RECIPIENT_CODES = Set.of(
            "3010", "3011", "3012", "3013", "3021", "3022", "3023"
    );

    private static final Set<String> INVALID_SENDER_CODES = Set.of(
            "1062", "2062", "3025"
    );

    private static final Set<String> BALANCE_CODES = Set.of(
            "1030", "2230"
    );

    private static final Set<String> RATE_LIMIT_CODES = Set.of(
            "TooManyRequests", "429"
    );

    private static final Set<String> RETRYABLE_PROVIDER_CODES = Set.of(
            "1024", "2024", "3024", "3053", "3040", "3041", "3042", "3043", "3044", "3045", "3046", "3048"
    );

    public SmsFailureCategory map(String providerCode, int httpStatus) {
        if (providerCode == null || providerCode.isBlank()) {
            return mapByHttpStatus(httpStatus);
        }

        if (AUTH_CODES.contains(providerCode)) {
            return SmsFailureCategory.AUTH_FAILED;
        }
        if (INVALID_RECIPIENT_CODES.contains(providerCode)) {
            return SmsFailureCategory.INVALID_RECIPIENT;
        }
        if (INVALID_SENDER_CODES.contains(providerCode)) {
            return SmsFailureCategory.INVALID_SENDER;
        }
        if (BALANCE_CODES.contains(providerCode)) {
            return SmsFailureCategory.INSUFFICIENT_BALANCE;
        }
        if (RATE_LIMIT_CODES.contains(providerCode) || httpStatus == 429) {
            return SmsFailureCategory.RATE_LIMITED;
        }
        if (RETRYABLE_PROVIDER_CODES.contains(providerCode)) {
            return SmsFailureCategory.PROVIDER_TEMPORARY;
        }
        if (providerCode.startsWith("2")) {
            return SmsFailureCategory.PROVIDER_TEMPORARY;
        }
        if (providerCode.startsWith("3")) {
            return SmsFailureCategory.PROVIDER_REJECTED;
        }

        return mapByHttpStatus(httpStatus);
    }

    private SmsFailureCategory mapByHttpStatus(int httpStatus) {
        if (httpStatus == 429) {
            return SmsFailureCategory.RATE_LIMITED;
        }
        if (httpStatus >= 500) {
            return SmsFailureCategory.PROVIDER_TEMPORARY;
        }
        if (httpStatus >= 400) {
            return SmsFailureCategory.PROVIDER_REJECTED;
        }
        return SmsFailureCategory.UNKNOWN;
    }
}
