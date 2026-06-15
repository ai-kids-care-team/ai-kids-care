package com.ai_kids_care.v1.security;

import com.ai_kids_care.v1.type.UserRoleAssignmentScopeType;
import com.ai_kids_care.v1.type.UserRoleEnum;
import org.springframework.stereotype.Component;

@Component("authorizationPolicy")
public class AuthorizationPolicy {

    public boolean isAllowed(AuthorizationAction action) {
        return EffectiveAuthorizationContextHolder.get()
                .map(context -> isAllowed(context, action))
                .orElse(false);
    }

    private boolean isAllowed(
            EffectiveAuthorizationContext context,
            AuthorizationAction action
    ) {
        UserRoleEnum role = context.role();
        boolean tenantIdentity =
                context.scopeType() == UserRoleAssignmentScopeType.KINDERGARTEN
                        && context.activeKindergartenId() != null;

        return switch (action) {
            case PLATFORM_METADATA_READ ->
                    role == UserRoleEnum.PLATFORM_IT_ADMIN
                            || role == UserRoleEnum.SUPERADMIN;
            case PLATFORM_METADATA_WRITE ->
                    role == UserRoleEnum.PLATFORM_IT_ADMIN;
            case TENANT_ANNOUNCEMENT_READ ->
                    tenantIdentity && (role == UserRoleEnum.GUARDIAN
                            || role == UserRoleEnum.TEACHER
                            || role == UserRoleEnum.KINDERGARTEN_ADMIN);
            case TENANT_ANNOUNCEMENT_WRITE, TENANT_S2_WRITE ->
                    tenantIdentity && role == UserRoleEnum.KINDERGARTEN_ADMIN;
            case TENANT_S2_READ ->
                    tenantIdentity && (role == UserRoleEnum.TEACHER
                            || role == UserRoleEnum.KINDERGARTEN_ADMIN);
            case TENANT_SURVEILLANCE_READ ->
                    tenantIdentity && role == UserRoleEnum.KINDERGARTEN_ADMIN;
        };
    }
}
