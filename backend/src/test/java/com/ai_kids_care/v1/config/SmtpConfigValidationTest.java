package com.ai_kids_care.v1.config;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SmtpConfig fail-fast: a blank {@code spring.mail.host/port/username/password} must be rejected
 * (the @NotBlank that fails context startup), consistent with Pushover/Solapi's posture (design D2)
 * — no silent attempt to send with a blank SMTP credential.
 */
class SmtpConfigValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private static SmtpConfig fullyPopulated() {
        SmtpConfig config = new SmtpConfig();
        config.setHost("smtp.example.com");
        config.setPort("587");
        config.setUsername("noreply@example.com");
        config.setPassword("app-password-not-secret");
        return config;
    }

    @Test
    void blankHostIsRejected() {
        SmtpConfig config = fullyPopulated();
        config.setHost("   ");
        assertThat(validator.validate(config))
                .as("blank spring.mail.host must violate @NotBlank")
                .anyMatch(v -> v.getPropertyPath().toString().equals("host"));
    }

    @Test
    void blankPortIsRejected() {
        SmtpConfig config = fullyPopulated();
        config.setPort("");
        assertThat(validator.validate(config))
                .as("blank spring.mail.port must violate @NotBlank")
                .anyMatch(v -> v.getPropertyPath().toString().equals("port"));
    }

    @Test
    void blankUsernameIsRejected() {
        SmtpConfig config = fullyPopulated();
        config.setUsername(null);
        assertThat(validator.validate(config))
                .as("blank spring.mail.username must violate @NotBlank")
                .anyMatch(v -> v.getPropertyPath().toString().equals("username"));
    }

    @Test
    void blankPasswordIsRejected() {
        SmtpConfig config = fullyPopulated();
        config.setPassword("   ");
        assertThat(validator.validate(config))
                .as("blank spring.mail.password must violate @NotBlank")
                .anyMatch(v -> v.getPropertyPath().toString().equals("password"));
    }

    @Test
    void fullyPopulatedConfigPasses() {
        assertThat(validator.validate(fullyPopulated())).isEmpty();
    }
}
