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
            // ⑥ 检测事件实时看板——本园 KINDERGARTEN_ADMIN/TEACHER（与 staff 告警受众一致）。
            case DETECTION_EVENT_READ ->
                    tenantIdentity && (role == UserRoleEnum.TEACHER
                            || role == UserRoleEnum.KINDERGARTEN_ADMIN);
            // Neo4j 关系图只读查询——本园 KINDERGARTEN_ADMIN/TEACHER（与 detection 看板受众一致）。
            // GUARDIAN 被排除（完整关系图会外溢共同监护人/教师，属隐私）；平台角色无 tenant identity 天然被拒。
            // 细粒度租户隔离由 GraphRepository 的 Cypher kindergarten_id 谓词强制（load-then-filter 禁止）。
            case GRAPH_READ ->
                    tenantIdentity && (role == UserRoleEnum.TEACHER
                            || role == UserRoleEnum.KINDERGARTEN_ADMIN);
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
            // SPEC-0003：感谢信读取——有效 tenant identity + GUARDIAN / TEACHER / KINDERGARTEN_ADMIN；
            // SUPERADMIN / PLATFORM_IT_ADMIN 无 tenant identity，由此被拒（不需要显式排除）。
            case APPRECIATION_LETTER_READ ->
                    tenantIdentity && (role == UserRoleEnum.GUARDIAN
                            || role == UserRoleEnum.TEACHER
                            || role == UserRoleEnum.KINDERGARTEN_ADMIN);
            // SPEC-0003：感谢信写——仅 GUARDIAN + 有效 tenant identity。
            case APPRECIATION_LETTER_WRITE ->
                    tenantIdentity && role == UserRoleEnum.GUARDIAN;
            // push_subscriptions 自助管理——任一已认证用户（context 存在即 true，
            // 等价「任一已认证用户」，镜像 PLATFORM_ANNOUNCEMENT_READ）。
            // 细粒度「只能管自己的」由 PushSubscriptionService 的 user-scoped 查询强制。
            case PUSH_SUBSCRIPTION_MANAGE -> true;
            // 检测事件复核——本园 KINDERGARTEN_ADMIN/TEACHER + 有效 tenant identity；
            // 细粒度租户隔离由 EventReviewService(findByIdAndKindergarten_Id / kindergarten 谓词)强制。
            case EVENT_REVIEW_WRITE, EVENT_REVIEW_READ ->
                    tenantIdentity && (role == UserRoleEnum.KINDERGARTEN_ADMIN
                            || role == UserRoleEnum.TEACHER);
            // UX-08：通知偏好自助管理——任一已认证用户（context 存在即 true，镜像 PUSH_SUBSCRIPTION_MANAGE）。
            // 细粒度「只能管自己在本园的 canonical 行」由 NotificationPreferenceService 的
            // (kindergarten_id, user_id) 双谓词查询强制。
            case NOTIFICATION_PREFERENCE_MANAGE -> true;
        };
    }
}
