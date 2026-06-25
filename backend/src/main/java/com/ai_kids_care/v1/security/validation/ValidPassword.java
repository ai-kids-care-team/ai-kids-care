package com.ai_kids_care.v1.security.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Enforces password complexity rules on "set-password" paths (signup, change-password,
 * reset-password). Rules are driven by {@link PasswordComplexityProperties}
 * ({@code app.security.password.*}) so they can be tightened without a code change.
 *
 * <p><b>NOT</b> applied to login — existing accounts must always be loginnable
 * regardless of whether their stored password meets the current policy.
 */
@Documented
@Constraint(validatedBy = PasswordConstraintValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidPassword {

    String message() default "비밀번호 복잡도 조건을 충족하지 않습니다.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
