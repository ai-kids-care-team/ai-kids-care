package com.ai_kids_care.v1.security.validation;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link PasswordConstraintValidator}.
 *
 * Tests use a plain Jakarta validator with the default properties; specialised
 * configurations are exercised by constructing the validator directly.
 *
 * Coverage matrix:
 *  - null / empty / blank
 *  - too short (< min-length)
 *  - missing letter (require-letter=true)
 *  - missing digit  (require-digit=true)
 *  - all-same characters when rejectAllSame=true
 *  - compliant passwords: boundary (exactly min-length), above min-length
 *  - disable rules: require-letter=false, require-digit=false
 */
class PasswordConstraintValidatorTest {

    // ── helpers ───────────────────────────────────────────────────────────────

    /** Simple carrier so Jakarta validates the annotated field via the Spring-free path. */
    private record Carrier(@ValidPassword String password) {}

    /**
     * Build a validator backed by a Spring-initialized factory so that constructor
     * injection of {@link PasswordComplexityProperties} works.
     * Since these are plain unit tests (no Spring context), we wire the validator
     * directly through a helper that bypasses the ConstraintValidatorFactory.
     */
    private PasswordConstraintValidator validatorWith(PasswordComplexityProperties props) {
        return new PasswordConstraintValidator(props);
    }

    private PasswordComplexityProperties defaultProps() {
        // Default: min=10, requireLetter=true, requireDigit=true, rejectAllSame=false
        return new PasswordComplexityProperties();
    }

    /** Invoke isValid with a no-op context so we can check the boolean. */
    private boolean isValid(PasswordConstraintValidator v, String password) {
        return v.isValid(password, new NoOpConstraintValidatorContext());
    }

    // ── null / empty ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("null password → invalid")
    void null_isInvalid() {
        assertThat(isValid(validatorWith(defaultProps()), null)).isFalse();
    }

    @Test
    @DisplayName("empty string → invalid")
    void empty_isInvalid() {
        assertThat(isValid(validatorWith(defaultProps()), "")).isFalse();
    }

    // ── min-length ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("min-length rule")
    class MinLength {

        @Test
        @DisplayName("password shorter than min-length → invalid")
        void tooShort_isInvalid() {
            // 9 chars, has letter + digit
            assertThat(isValid(validatorWith(defaultProps()), "abcde1234")).isFalse();
        }

        @Test
        @DisplayName("password exactly min-length with letter+digit → valid")
        void exactMinLength_valid() {
            // exactly 10 chars: 8 letters + 2 digits
            assertThat(isValid(validatorWith(defaultProps()), "abcdefgh12")).isTrue();
        }

        @Test
        @DisplayName("password above min-length → valid")
        void aboveMinLength_valid() {
            assertThat(isValid(validatorWith(defaultProps()), "StrongPass2026!")).isTrue();
        }

        @Test
        @DisplayName("custom min-length=8 accepts 8-char password")
        void customMinLength8_accepts8chars() {
            PasswordComplexityProperties p = new PasswordComplexityProperties();
            p.setMinLength(8);
            // "abcdef12" = 8 chars, letter + digit
            assertThat(isValid(validatorWith(p), "abcdef12")).isTrue();
        }

        @Test
        @DisplayName("custom min-length=8 rejects 7-char password")
        void customMinLength8_rejects7chars() {
            PasswordComplexityProperties p = new PasswordComplexityProperties();
            p.setMinLength(8);
            assertThat(isValid(validatorWith(p), "abcde12")).isFalse();
        }
    }

    // ── require-letter ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("require-letter rule")
    class RequireLetter {

        @Test
        @DisplayName("digits-only password (≥ min-length) → invalid when requireLetter=true")
        void digitsOnly_isInvalid() {
            // 10 digits, no letter
            assertThat(isValid(validatorWith(defaultProps()), "1234567890")).isFalse();
        }

        @Test
        @DisplayName("digits-only password accepted when requireLetter=false")
        void digitsOnly_acceptedWhenRuleDisabled() {
            PasswordComplexityProperties p = new PasswordComplexityProperties();
            p.setRequireLetter(false);
            assertThat(isValid(validatorWith(p), "1234567890")).isTrue();
        }
    }

    // ── require-digit ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("require-digit rule")
    class RequireDigit {

        @Test
        @DisplayName("letters-only password (≥ min-length) → invalid when requireDigit=true")
        void lettersOnly_isInvalid() {
            // 10 letters, no digit
            assertThat(isValid(validatorWith(defaultProps()), "abcdefghij")).isFalse();
        }

        @Test
        @DisplayName("letters-only password accepted when requireDigit=false")
        void lettersOnly_acceptedWhenRuleDisabled() {
            PasswordComplexityProperties p = new PasswordComplexityProperties();
            p.setRequireDigit(false);
            assertThat(isValid(validatorWith(p), "abcdefghij")).isTrue();
        }
    }

    // ── reject-all-same ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("reject-all-same rule")
    class RejectAllSame {

        @Test
        @DisplayName("all-same characters NOT rejected by default (rejectAllSame=false)")
        void allSame_notRejectedByDefault() {
            // default rejectAllSame=false; "aaaaaaaaaa1" still needs letter+digit
            assertThat(isValid(validatorWith(defaultProps()), "aaaaaaaaaa1")).isTrue();
        }

        @Test
        @DisplayName("all-same characters rejected when rejectAllSame=true")
        void allSame_rejectedWhenEnabled() {
            PasswordComplexityProperties p = new PasswordComplexityProperties();
            p.setRejectAllSame(true);
            p.setRequireLetter(false);
            p.setRequireDigit(false);
            // "aaaaaaaaaa" = 10 same chars
            assertThat(isValid(validatorWith(p), "aaaaaaaaaa")).isFalse();
        }

        @Test
        @DisplayName("mixed characters not rejected when rejectAllSame=true")
        void mixed_acceptedWhenEnabled() {
            PasswordComplexityProperties p = new PasswordComplexityProperties();
            p.setRejectAllSame(true);
            // "abcdefgh12" has mixed chars + letter + digit + ≥10
            assertThat(isValid(validatorWith(p), "abcdefgh12")).isTrue();
        }
    }

    // ── known seed/demo passwords (login path exemption verification) ─────────
    //
    // These passwords exist in the DB for demo/seed accounts. The validator is NOT
    // applied to login, so these never cause a live user to be locked out.
    // This test documents that admin123 fails the complexity check — confirming it
    // would be rejected at *signup* but not at login (different code path).

    @Test
    @DisplayName("admin123 (demo seed password) fails complexity — confirms 8-char shortfall")
    void admin123_failsComplexity_expectedForSeedAccounts() {
        // "admin123" = 8 chars — below default min-length 10.
        // This is intentional: seed accounts pre-exist and are never re-registered.
        // The validator is only on signup/change-password, not login.
        assertThat(isValid(validatorWith(defaultProps()), "admin123")).isFalse();
    }

    // ── compliant passwords ───────────────────────────────────────────────────

    @Test
    @DisplayName("Test@Baseline2024 (existing test password) passes complexity")
    void existingTestPassword_passes() {
        assertThat(isValid(validatorWith(defaultProps()), "Test@Baseline2024")).isTrue();
    }

    @Test
    @DisplayName("Test@Admin2026 passes complexity")
    void testAdminPassword_passes() {
        assertThat(isValid(validatorWith(defaultProps()), "Test@Admin2026")).isTrue();
    }

    // ── Jakarta bean validation integration ──────────────────────────────────
    //
    // These tests run through the full Jakarta Validator pipeline using an
    // autowired-less factory (no Spring context needed — the constructor injection
    // is driven by SpringConstraintValidatorFactory in production, but we verify
    // the annotation wiring here via a manual factory override).

    @Nested
    @DisplayName("Jakarta bean validation via Validator (direct factory)")
    class JakartaValidation {

        private Validator validator;

        @BeforeEach
        void buildValidator() {
            PasswordComplexityProperties props = defaultProps();
            validator = Validation.byDefaultProvider()
                    .configure()
                    .constraintValidatorFactory(
                            new DirectConstraintValidatorFactory(props))
                    .buildValidatorFactory()
                    .getValidator();
        }

        @Test
        @DisplayName("valid password → no violations")
        void valid_noViolations() {
            Set<ConstraintViolation<Carrier>> violations =
                    validator.validate(new Carrier("abcdefgh12"));
            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("too-short password → one violation")
        void tooShort_oneViolation() {
            Set<ConstraintViolation<Carrier>> violations =
                    validator.validate(new Carrier("ab12"));
            assertThat(violations).hasSize(1);
        }

        @Test
        @DisplayName("letters-only password → one violation (missing digit)")
        void lettersOnly_oneViolation() {
            Set<ConstraintViolation<Carrier>> violations =
                    validator.validate(new Carrier("abcdefghij"));
            assertThat(violations).hasSize(1);
        }

        @Test
        @DisplayName("digits-only password → one violation (missing letter)")
        void digitsOnly_oneViolation() {
            Set<ConstraintViolation<Carrier>> violations =
                    validator.validate(new Carrier("1234567890"));
            assertThat(violations).hasSize(1);
        }
    }

    // ── test infrastructure ───────────────────────────────────────────────────

    /**
     * Minimal {@link jakarta.validation.ConstraintValidatorFactory} that directly
     * instantiates {@link PasswordConstraintValidator} with the supplied props,
     * and falls back to default no-arg construction for all other validators.
     */
    private static class DirectConstraintValidatorFactory
            implements jakarta.validation.ConstraintValidatorFactory {

        private final PasswordComplexityProperties props;

        DirectConstraintValidatorFactory(PasswordComplexityProperties props) {
            this.props = props;
        }

        @Override
        public <T extends jakarta.validation.ConstraintValidator<?, ?>> T getInstance(Class<T> key) {
            if (key == PasswordConstraintValidator.class) {
                @SuppressWarnings("unchecked")
                T v = (T) new PasswordConstraintValidator(props);
                return v;
            }
            try {
                return key.getDeclaredConstructor().newInstance();
            } catch (Exception e) {
                throw new RuntimeException("Cannot instantiate " + key, e);
            }
        }

        @Override
        public void releaseInstance(jakarta.validation.ConstraintValidator<?, ?> instance) {
            // no-op
        }
    }

    /**
     * No-op {@link jakarta.validation.ConstraintValidatorContext} for direct
     * validator invocation without a full Jakarta context.
     */
    private static class NoOpConstraintValidatorContext
            implements jakarta.validation.ConstraintValidatorContext {

        @Override
        public void disableDefaultConstraintViolation() {}

        @Override
        public String getDefaultConstraintMessageTemplate() { return ""; }

        @Override
        public jakarta.validation.ClockProvider getClockProvider() { return null; }

        @Override
        public ConstraintViolationBuilder buildConstraintViolationWithTemplate(String msg) {
            return new ConstraintViolationBuilder() {
                @Override
                public NodeBuilderDefinedContext addNode(String name) { return null; }
                @Override
                public NodeBuilderCustomizableContext addPropertyNode(String name) { return null; }
                @Override
                public LeafNodeBuilderCustomizableContext addBeanNode() { return null; }
                @Override
                public ContainerElementNodeBuilderCustomizableContext addContainerElementNode(
                        String name, Class<?> containerType, Integer typeArgumentIndex) { return null; }
                @Override
                public NodeBuilderDefinedContext addParameterNode(int index) { return null; }
                @Override
                public ConstraintValidatorContext addConstraintViolation() {
                    return NoOpConstraintValidatorContext.this;
                }
            };
        }

        @Override
        public <T> T unwrap(Class<T> type) { throw new UnsupportedOperationException(); }
    }
}
