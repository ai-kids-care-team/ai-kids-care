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
            // SPEC-0002 Slice A: 粗粒度门——仅确认 KINDERGARTEN_ADMIN + 有效 tenant identity。
            // 细粒度（teachers.level / 同园 / 禁自审 / 目标状态）由 KindergartenAdminPolicy 在事务内完成（ADR-0019 §2）。
            case KINDERGARTEN_ADMIN_APPROVAL_READ,
                 KINDERGARTEN_ADMIN_APPROVAL_WRITE,
                 KINDERGARTEN_ADMIN_MEMBER_WRITE ->
                    tenantIdentity && role == UserRoleEnum.KINDERGARTEN_ADMIN;
            // SPEC-0002 Slice B: 平台级粗粒度门——仅确认 PLATFORM_IT_ADMIN + PLATFORM scope（scopeId 必须为 null）。
            // 细粒度（禁自审 / 目标 role 为 SUPERADMIN / 目标状态）由 PlatformPolicy 在事务内完成（ADR-0019 §2）。
            // 注意：PLATFORM scope 不使用 tenantIdentity（该布尔值为 KINDERGARTEN scope 专用）。
            case PLATFORM_SUPERADMIN_APPROVAL_READ,
                 PLATFORM_SUPERADMIN_APPROVAL_WRITE,
                 PLATFORM_USER_WRITE ->
                    context.scopeType() == UserRoleAssignmentScopeType.PLATFORM
                            && role == UserRoleEnum.PLATFORM_IT_ADMIN;
        };
    }
}
