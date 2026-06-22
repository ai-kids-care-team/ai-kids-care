package com.ai_kids_care.v1.config;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PushoverConfig fail-fast: a blank Pushover API token must be rejected (the @NotBlank that
 * fails context startup), so the previous "hard-coded empty credential, runtime throw" hazard
 * cannot recur silently.
 */
class PushoverConfigValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void blankApiTokenIsRejected() {
        PushoverConfig config = new PushoverConfig();
        config.setApiToken("   ");
        assertThat(validator.validate(config))
                .as("blank pushover.api-token must violate @NotBlank")
                .anyMatch(v -> v.getPropertyPath().toString().equals("apiToken"));
    }

    @Test
    void presentApiTokenPasses() {
        PushoverConfig config = new PushoverConfig();
        config.setApiToken("app-token-not-secret");
        assertThat(validator.validate(config)).isEmpty();
    }
}
