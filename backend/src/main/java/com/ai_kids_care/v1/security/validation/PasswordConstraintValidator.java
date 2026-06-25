package com.ai_kids_care.v1.security.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Validates a password string against the rules declared in
 * {@link PasswordComplexityProperties}.
 *
 * <p>Null and empty passwords are always rejected here; use {@code @NotBlank} on the
 * surrounding field for a friendlier empty-value message if desired.
 *
 * <p>The validator is <b>only</b> wired to "set-password" DTOs (signup, change,
 * reset). It must never be applied to the login DTO — existing accounts whose
 * stored password predates the current policy must remain loginnable.
 */
public class PasswordConstraintValidator
        implements ConstraintValidator<ValidPassword, String> {

    private final PasswordComplexityProperties props;

    /**
     * Spring injects the properties bean when this validator is instantiated via
     * the Spring-managed validator factory. In non-Spring unit tests the constructor
     * is called directly with an explicit {@link PasswordComplexityProperties}.
     */
    public PasswordConstraintValidator(PasswordComplexityProperties props) {
        this.props = props;
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isEmpty()) {
            customMessage(context, "비밀번호를 입력해주세요.");
            return false;
        }

        if (value.length() < props.getMinLength()) {
            customMessage(context,
                    "비밀번호는 최소 " + props.getMinLength() + "자 이상이어야 합니다.");
            return false;
        }

        if (props.isRequireLetter() && !containsLetter(value)) {
            customMessage(context, "비밀번호에 영문자가 포함되어야 합니다.");
            return false;
        }

        if (props.isRequireDigit() && !containsDigit(value)) {
            customMessage(context, "비밀번호에 숫자가 포함되어야 합니다.");
            return false;
        }

        if (props.isRejectAllSame() && allSameChar(value)) {
            customMessage(context, "비밀번호의 모든 문자가 동일할 수 없습니다.");
            return false;
        }

        return true;
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private static boolean containsLetter(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (Character.isLetter(s.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsDigit(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (Character.isDigit(s.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    private static boolean allSameChar(String s) {
        if (s.length() <= 1) {
            return true;
        }
        char first = s.charAt(0);
        for (int i = 1; i < s.length(); i++) {
            if (s.charAt(i) != first) {
                return false;
            }
        }
        return true;
    }

    private static void customMessage(ConstraintValidatorContext ctx, String msg) {
        ctx.disableDefaultConstraintViolation();
        ctx.buildConstraintViolationWithTemplate(msg).addConstraintViolation();
    }
}
