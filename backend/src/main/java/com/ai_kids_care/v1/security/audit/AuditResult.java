package com.ai_kids_care.v1.security.audit;

/**
 * 安全审计结果（落 {@code audit_logs.result}，ADR-0021 §3）。
 */
public enum AuditResult {
    /** 操作成功完成。 */
    SUCCESS,
    /** 因授权 / 租户 / 资源关系被拒（403 或隐藏 404）。 */
    DENIED,
    /** 凭据校验失败等非授权类失败（如登录失败）。 */
    FAILURE
}
