package com.ai_kids_care.v1.security;

public enum AuthorizationAction {
    PLATFORM_METADATA_READ,
    PLATFORM_METADATA_WRITE,
    TENANT_ANNOUNCEMENT_READ,
    TENANT_ANNOUNCEMENT_WRITE,
    TENANT_S2_READ,
    TENANT_S2_WRITE,
    TENANT_SURVEILLANCE_READ,
    // SPEC-0001 §3 / §349：Guardian 读取与自己有 ACTIVE 关系的儿童（细粒度关系在 ChildRepository SQL 内强制）
    GUARDIAN_CHILD_READ,
    // SPEC-0002 Slice A: 园级审批 / 成员状态变更（粗粒度 role 门；细粒度在 KindergartenAdminPolicy 内完成）
    KINDERGARTEN_ADMIN_APPROVAL_READ,
    KINDERGARTEN_ADMIN_APPROVAL_WRITE,
    KINDERGARTEN_ADMIN_MEMBER_WRITE,
    // SPEC-0002 Slice B: 平台级审批 / 平台账户状态变更（粗粒度 role 门；细粒度在 PlatformPolicy 内完成）
    PLATFORM_SUPERADMIN_APPROVAL_READ,
    PLATFORM_SUPERADMIN_APPROVAL_WRITE,
    PLATFORM_USER_WRITE
}
