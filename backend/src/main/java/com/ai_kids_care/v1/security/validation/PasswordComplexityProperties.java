package com.ai_kids_care.v1.security.validation;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Config-driven password complexity rules.
 *
 * <pre>
 * app:
 *   security:
 *     password:
 *       min-length: 10
 *       require-letter: true
 *       require-digit: true
 *       reject-all-same: false
 * </pre>
 *
 * All rules default to a sensible secure baseline. Set {@code reject-all-same: true}
 * to additionally reject passwords whose characters are all identical (e.g. "aaaaaaaaaa").
 */
@Component
@ConfigurationProperties(prefix = "app.security.password")
public class PasswordComplexityProperties {

    /** Minimum number of characters. Default 10. */
    private int minLength = 10;

    /** Require at least one ASCII letter (a-z or A-Z). Default true. */
    private boolean requireLetter = true;

    /** Require at least one ASCII digit (0-9). Default true. */
    private boolean requireDigit = true;

    /**
     * Reject passwords where every character is the same (e.g. "aaaaaaaaaa").
     * Default false (conservative — does not break any existing migration path).
     */
    private boolean rejectAllSame = false;

    public int getMinLength() {
        return minLength;
    }

    public void setMinLength(int minLength) {
        this.minLength = minLength;
    }

    public boolean isRequireLetter() {
        return requireLetter;
    }

    public void setRequireLetter(boolean requireLetter) {
        this.requireLetter = requireLetter;
    }

    public boolean isRequireDigit() {
        return requireDigit;
    }

    public void setRequireDigit(boolean requireDigit) {
        this.requireDigit = requireDigit;
    }

    public boolean isRejectAllSame() {
        return rejectAllSame;
    }

    public void setRejectAllSame(boolean rejectAllSame) {
        this.rejectAllSame = rejectAllSame;
    }
}
