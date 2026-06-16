package com.ai_kids_care.v1.security.audit;

import com.ai_kids_care.v1.security.EffectiveAuthorizationContext;
import com.ai_kids_care.v1.security.EffectiveAuthorizationContextHolder;
import com.ai_kids_care.v1.type.UserRoleAssignmentScopeType;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;

/**
 * 已认证用户被拒绝（403）时审计并返回统一 JSON。
 *
 * 由 Spring Security {@code ExceptionTranslationFilter} 在已认证主体被拒时调用，覆盖：
 * - {@code @PreAuthorize} 粗粒度角色 / scope 门拒绝（SPEC-0001 §367「角色拒绝」）。
 * - CSRF token 缺失 / 无效（已认证请求）。
 *
 * 此时 {@link EffectiveAuthorizationContextFilter} 尚未清理 holder，故 actor 上下文可用。
 * 匿名请求的认证缺失走 {@code authenticationEntryPoint}（401），不经本 handler。
 *
 * 审批端点内的细粒度 403（自审 / level）与跨租户隐藏 404 由 service 层 try/catch 单独审计。
 */
@Component
@RequiredArgsConstructor
public class SecurityAuditAccessDeniedHandler implements AccessDeniedHandler {

    private final SecurityAuditWriter auditWriter;
    private final ObjectMapper objectMapper;

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException
    ) throws IOException {
        audit(request);

        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), Map.of("error", "Access denied"));
    }

    private void audit(HttpServletRequest request) {
        EffectiveAuthorizationContext context =
                EffectiveAuthorizationContextHolder.get().orElse(null);

        UserRoleAssignmentScopeType scopeType = context != null
                ? context.scopeType()
                : UserRoleAssignmentScopeType.PLATFORM;  // actor 未知 → 平台级事件，kindergarten_id NULL
        Long kindergartenId = (context != null
                && scopeType == UserRoleAssignmentScopeType.KINDERGARTEN)
                ? context.activeKindergartenId()
                : null;

        auditWriter.record(AuditEvent.builder()
                .action(AuditAction.AUTHORIZATION_DENIED)
                .result(AuditResult.DENIED)
                .actorUserId(context != null ? context.userId() : null)
                .scopeType(scopeType)
                .kindergartenId(kindergartenId)
                .effectiveRole(context != null ? context.role().name() : null)
                .resourceType(request.getMethod() + " " + request.getRequestURI())
                .build());
    }
}
