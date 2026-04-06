package com.ai_kids_care.v1.service.sms;

public enum SmsFailureCategory {
    AUTH_FAILED(false),
    INVALID_RECIPIENT(false),
    INVALID_SENDER(false),
    INSUFFICIENT_BALANCE(false),
    RATE_LIMITED(true),
    PROVIDER_TEMPORARY(true),
    PROVIDER_REJECTED(false),
    NETWORK_ERROR(true),
    NOT_CONFIGURED(false),
    UNKNOWN(false);

    private final boolean retryable;

    SmsFailureCategory(boolean retryable) {
        this.retryable = retryable;
    }

    public boolean isRetryable() {
        return retryable;
    }
}
