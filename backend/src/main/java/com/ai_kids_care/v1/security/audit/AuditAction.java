package com.ai_kids_care.v1.security.audit;

/**
 * 安全审计动作（SPEC-0001 审计要求 §270-284）。
 *
 * 应用层枚举约束 {@code audit_logs.action}（DB 维持 varchar，避免 DB enum 频繁迁移——ADR-0021 §3）。
 * 名称即落库的 {@code action} 字符串。
 */
public enum AuditAction {

    // ── 会话生命周期 ──────────────────────────────────────────────────────────
    LOGIN_SUCCESS,
    LOGIN_FAILURE,
    LOGOUT,
    /** /auth/logout-all：用户主动吊销自身全部 session。 */
    SESSION_REVOKE_ALL,

    // ── 平台 tenant context ──────────────────────────────────────────────────
    /** 平台角色选择/切换 active tenant（平台级事件，不伪造 kindergarten id——SPEC §284）。 */
    TENANT_CONTEXT_SELECT,

    // ── 角色 / 成员状态变更（SPEC-0002 审批端点） ─────────────────────────────
    KINDERGARTEN_APPROVE,
    KINDERGARTEN_REJECT,
    KINDERGARTEN_DISABLE_MEMBER,
    PLATFORM_SUPERADMIN_APPROVE,
    PLATFORM_SUPERADMIN_REJECT,
    PLATFORM_USER_DISABLE,

    // ── 授权拒绝 ──────────────────────────────────────────────────────────────
    /** 角色 / 跨租户 / CSRF 拒绝（@PreAuthorize 403 经 AccessDeniedHandler，及审批内细粒度 403/隐藏 404）。 */
    AUTHORIZATION_DENIED,

    // ── 前瞻：S1 / evidence 读取 ──────────────────────────────────────────────
    /**
     * S1 敏感数据 / detection evidence 读取（SPEC §277）。
     * 当前 DetectionEvent / EventEvidenceFile 控制器为空壳、无已发布读取端点，
     * 故无活动调用点；保留枚举供后续重开相应读资源时接入。
     */
    S1_EVIDENCE_READ
}
