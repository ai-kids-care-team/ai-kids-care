package com.ai_kids_care.v1.service.sms;

public record SmsSendResult(
        boolean success,
        String providerCode,
        String providerMessage,
        String providerMessageId,
        SmsFailureCategory failureCategory
) {
    public static SmsSendResult success(String providerCode, String providerMessage, String providerMessageId) {
        return new SmsSendResult(true, providerCode, providerMessage, providerMessageId, null);
    }

    public static SmsSendResult failure(
            String providerCode,
            String providerMessage,
            String providerMessageId,
            SmsFailureCategory failureCategory
    ) {
        return new SmsSendResult(false, providerCode, providerMessage, providerMessageId, failureCategory);
    }

    public boolean retryable() {
        return failureCategory != null && failureCategory.isRetryable();
    }

    public String internalCode() {
        return failureCategory == null ? "NONE" : failureCategory.name();
    }
}
