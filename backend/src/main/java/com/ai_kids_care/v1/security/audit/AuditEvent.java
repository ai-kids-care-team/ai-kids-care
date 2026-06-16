package com.ai_kids_care.v1.security.audit;

import com.ai_kids_care.v1.type.UserRoleAssignmentScopeType;
import lombok.Builder;

/**
 * 一条安全审计事件的不可变载荷（调用点填写业务字段）。
 *
 * 不在此承载的字段由 {@link SecurityAuditWriter} 自服务补全：
 * - {@code correlation_id} ← MDC（{@link CorrelationIdFilter}）。
 * - {@code ip} / {@code user_agent} ← 当前 servlet 请求。
 * - {@code created_at} ← DB / 实体默认。
 *
 * 绝不承载 S0/S1 原始值（密码 / RRN / token / session id / 请求 body）——SPEC-0001 §282/§296。
 *
 * @param action        审计动作
 * @param result        结果
 * @param actorUserId   发起者 user id；匿名 / actor 未知（如登录失败）为 {@code null}
 * @param scopeType     事件 scope（NOT NULL；{@code PLATFORM} 时 writer 强制 kindergartenId=null）
 * @param kindergartenId 园级事件的 tenant id；平台级事件为 {@code null}（不伪造——SPEC §284）
 * @param effectiveRole 发起者有效角色名；未知为 {@code null}
 * @param resourceType  目标资源类型（如 {@code "USER"} / {@code "KINDERGARTEN"} / HTTP method+path）
 * @param resourceId    目标资源 id（无 FK 约束的内部 id）；无则 {@code null}
 */
@Builder
public record AuditEvent(
        AuditAction action,
        AuditResult result,
        Long actorUserId,
        UserRoleAssignmentScopeType scopeType,
        Long kindergartenId,
        String effectiveRole,
        String resourceType,
        Long resourceId
) {
}
