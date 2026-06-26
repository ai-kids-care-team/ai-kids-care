package com.ai_kids_care.v1.security;

public enum AuthorizationAction {
    PLATFORM_METADATA_READ,
    PLATFORM_METADATA_WRITE,
    // AN-READ：平台级公告无 tenant scope 过滤；任一已认证用户均可读 ACTIVE 公告（粗粒度门）。
    PLATFORM_ANNOUNCEMENT_READ,
    // AN-READ：公告写操作专属 PLATFORM_IT_ADMIN 门。
    PLATFORM_ANNOUNCEMENT_WRITE,
    TENANT_S2_READ,
    TENANT_S2_WRITE,
    TENANT_SURVEILLANCE_READ,
    // SPEC-0001 §3 / §349 / §351：儿童读取粗粒度门——GUARDIAN（关系-scoped）和 TEACHER（assignment-scoped）均适用；
    // 细粒度作用域分别由 ChildRepository 的 guardian 关系查询和 teacher 班级分配查询在 SQL 内强制。
    CHILD_READ,
    // SPEC-0002 Slice A: 园级审批 / 成员状态变更（粗粒度 role 门；细粒度在 KindergartenAdminPolicy 内完成）
    KINDERGARTEN_ADMIN_APPROVAL_READ,
    KINDERGARTEN_ADMIN_APPROVAL_WRITE,
    KINDERGARTEN_ADMIN_MEMBER_WRITE,
    // SPEC-0002 Slice B: 平台级审批 / 平台账户状态变更（粗粒度 role 门；细粒度在 PlatformPolicy 内完成）
    PLATFORM_SUPERADMIN_APPROVAL_READ,
    PLATFORM_SUPERADMIN_APPROVAL_WRITE,
    PLATFORM_USER_WRITE,
    // SPEC-0001 / ADR-0018 A3d：通知读取粗粒度门——GUARDIAN / TEACHER / KINDERGARTEN_ADMIN + 有效 tenant identity；
    // 细粒度「受体仅读自己 / Admin 读其园」由 NotificationRepository SQL 强制（recipient-scoped vs. kindergarten-scoped）。
    NOTIFICATION_READ,
    // ADR-0026 Phase 1：摄像头流写粗粒度门——仅 KINDERGARTEN_ADMIN + 有效 tenant identity；
    // 细粒度 tenant 隔离由 Service 层 requireActiveKindergartenId() + repository 强制。
    TENANT_SURVEILLANCE_WRITE,
    // SPEC-0003：感谢信读取粗粒度门——GUARDIAN + TEACHER + KINDERGARTEN_ADMIN + 有效 tenant identity；
    // 细粒度可见性（GUARDIAN 自己+公开 / TEACHER 公开+发给本人 / ADMIN 全部）由 Repository SQL 强制。
    APPRECIATION_LETTER_READ,
    // SPEC-0003：感谢信写粗粒度门——仅 GUARDIAN + 有效 tenant identity；
    // 细粒度所有权（仅作者可改/删）由 Repository SQL 强制。
    APPRECIATION_LETTER_WRITE,
    // push_subscriptions 自助管理粗粒度门——任一已认证用户均可管理自己的推送订阅
    // （push_subscriptions 无 kindergarten_id，纯 user-scoped）；细粒度「只能管自己的」
    // 由 PushSubscriptionService 用 EffectiveAuthorizationContext.userId() 在查询内强制。
    PUSH_SUBSCRIPTION_MANAGE,
    // 检测事件复核工作流粗门——本园 KINDERGARTEN_ADMIN/TEACHER + 有效 tenant identity；
    // 细粒度租户隔离由 EventReviewService 用 EffectiveAuthorizationContext + repository 强制。
    EVENT_REVIEW_WRITE,
    EVENT_REVIEW_READ,
    // ⑥ 检测事件实时看板读取粗门——本园 KINDERGARTEN_ADMIN/TEACHER + 有效 tenant identity
    // （与 staff 告警受众一致）；细粒度租户隔离由 DetectionEventService 用
    // EffectiveAuthorizationContext.requireActiveKindergartenId() + repository 强制。
    DETECTION_EVENT_READ
}
