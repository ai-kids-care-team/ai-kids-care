package com.ai_kids_care.v1.security;

public enum AuthorizationAction {
    PLATFORM_METADATA_READ,
    PLATFORM_METADATA_WRITE,
    TENANT_ANNOUNCEMENT_READ,
    TENANT_ANNOUNCEMENT_WRITE,
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
    NOTIFICATION_READ
}
