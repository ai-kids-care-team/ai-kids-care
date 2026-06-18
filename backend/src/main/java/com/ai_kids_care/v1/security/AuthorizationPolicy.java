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
            // AN-READ：平台级公告广读——任一已认证用户可见 ACTIVE 公告。
            // isAllowed 在无认证 context 时通过 orElse(false) 返回 false，
            // 因此 context 存在时返回 true 等价于"任一已认证用户"。
            case PLATFORM_ANNOUNCEMENT_READ -> true;
            // AN-READ：写操作仅限 PLATFORM_IT_ADMIN（镜像 PLATFORM_USER_WRITE）。
            case PLATFORM_ANNOUNCEMENT_WRITE ->
                    context.scopeType() == UserRoleAssignmentScopeType.PLATFORM
                            && role == UserRoleEnum.PLATFORM_IT_ADMIN;
            case TENANT_S2_WRITE ->
                    tenantIdentity && role == UserRoleEnum.KINDERGARTEN_ADMIN;
            case TENANT_S2_READ ->
                    tenantIdentity && (role == UserRoleEnum.TEACHER
                            || role == UserRoleEnum.KINDERGARTEN_ADMIN);
            case TENANT_SURVEILLANCE_READ ->
                    tenantIdentity && role == UserRoleEnum.KINDERGARTEN_ADMIN;
            // SPEC-0001 §3 / §349 / §351：粗粒度门——GUARDIAN 或 TEACHER + 有效 tenant identity；
            // 细粒度「GUARDIAN 仅 ACTIVE 关系儿童 / TEACHER 仅 ACTIVE assignment 班级内儿童」由 ChildRepository SQL 内强制。
            // KINDERGARTEN_ADMIN 不在此门内（不得访问 GuardianChildVO 端点）。
            case CHILD_READ ->
                    tenantIdentity && (role == UserRoleEnum.GUARDIAN || role == UserRoleEnum.TEACHER);
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
            // SPEC-0001 / ADR-0018 A3d：粗粒度门——有效 tenant identity + GUARDIAN / TEACHER / KINDERGARTEN_ADMIN；
            // 细粒度「受体仅读自己 / Admin 读其园」由 NotificationRepository SQL（recipient vs. kindergarten-scoped）强制。
            case NOTIFICATION_READ ->
                    tenantIdentity && (role == UserRoleEnum.GUARDIAN
                            || role == UserRoleEnum.TEACHER
                            || role == UserRoleEnum.KINDERGARTEN_ADMIN);
            // ADR-0026 Phase 1：写粗粒度门——仅 KINDERGARTEN_ADMIN + 有效 tenant identity。
            // 细粒度 tenant 隔离由 Service 层 requireActiveKindergartenId() + repository 强制。
            case TENANT_SURVEILLANCE_WRITE ->
                    tenantIdentity && role == UserRoleEnum.KINDERGARTEN_ADMIN;
        };
    }
}
