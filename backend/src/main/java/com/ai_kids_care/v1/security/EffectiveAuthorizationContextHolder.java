package com.ai_kids_care.v1.security;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

public final class EffectiveAuthorizationContextHolder {

    private static final ThreadLocal<EffectiveAuthorizationContext> CONTEXT =
            new ThreadLocal<>();

    private EffectiveAuthorizationContextHolder() {
    }

    public static void set(EffectiveAuthorizationContext context) {
        CONTEXT.set(context);
    }

    public static Optional<EffectiveAuthorizationContext> get() {
        return Optional.ofNullable(CONTEXT.get());
    }

    public static EffectiveAuthorizationContext require() {
        return get().orElseThrow(() ->
                new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication failed"));
    }

    public static Long requireActiveKindergartenId() {
        EffectiveAuthorizationContext context = require();
        if (context.activeKindergartenId() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
        }
        return context.activeKindergartenId();
    }

    public static void clear() {
        CONTEXT.remove();
    }
}
