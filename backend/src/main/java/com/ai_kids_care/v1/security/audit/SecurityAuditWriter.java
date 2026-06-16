package com.ai_kids_care.v1.security.audit;

import com.ai_kids_care.v1.entity.AuditLog;
import com.ai_kids_care.v1.repository.AuditLogRepository;
import com.ai_kids_care.v1.repository.KindergartenRepository;
import com.ai_kids_care.v1.repository.UserRepository;
import com.ai_kids_care.v1.type.UserRoleAssignmentScopeType;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Append-only 安全审计写入器（SPEC-0001 审计要求 §270-298）。
 *
 * 设计取舍：
 * - **直写 {@code audit_logs}**，不复用被关闭的通用 {@code AuditLogService} CRUD 栈（ADR-0021 边界）。
 *   本类是系统唯一的审计写入方；不提供更新 / 删除（append-only 由应用层保证；
 *   DB 级 REVOKE UPDATE/DELETE 属高风险权限，由维护者另行决定——ADR-0021 §4）。
 * - **独立事务（{@code REQUIRES_NEW}）**：每条审计记录独立提交，使授权拒绝 / 登录失败
 *   等发生在业务事务回滚路径上的事件仍能持久化。
 * - **Best-effort**：审计写入失败只记日志、绝不向上抛——审计问题不得阻断或回滚其伴随的
 *   业务 / 安全操作（生产稳定性优先）。残留风险：业务事务在审计提交后于 commit 阶段失败，
 *   会留下孤儿 SUCCESS 记录（极罕见，无 deferred 约束）。
 * - **掩码**：只落结构化字段（actor id / scope / action / result / resource type+id /
 *   ip / user-agent / correlation id）。绝不写 loginId / email / phone / RRN / token /
 *   session id / 请求 body（SPEC §282/§296）。
 * - correlation id 取自 MDC（{@link CorrelationIdFilter}）；ip / user-agent 取自当前请求。
 */
@Service
public class SecurityAuditWriter {

    private static final Logger log = LoggerFactory.getLogger(SecurityAuditWriter.class);

    private static final int MAX_USER_AGENT = 512;
    private static final int MAX_RESOURCE_TYPE = 256;

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;
    private final KindergartenRepository kindergartenRepository;
    private final TransactionTemplate requiresNewTransaction;

    public SecurityAuditWriter(
            AuditLogRepository auditLogRepository,
            UserRepository userRepository,
            KindergartenRepository kindergartenRepository,
            PlatformTransactionManager transactionManager
    ) {
        this.auditLogRepository = auditLogRepository;
        this.userRepository = userRepository;
        this.kindergartenRepository = kindergartenRepository;
        this.requiresNewTransaction = new TransactionTemplate(transactionManager);
        this.requiresNewTransaction.setPropagationBehavior(
                TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * 记录一条安全审计事件。失败只记日志、不抛出（best-effort，见类注释）。
     */
    public void record(AuditEvent event) {
        try {
            requiresNewTransaction.executeWithoutResult(status -> persist(event));
        } catch (RuntimeException ex) {
            log.error("Security audit write failed: action={} result={}",
                    event.action(), event.result(), ex);
        }
    }

    private void persist(AuditEvent event) {
        UserRoleAssignmentScopeType scopeType = event.scopeType();
        Long kindergartenId = event.kindergartenId();

        // 防御性：满足 chk_audit_scope_kindergarten，避免插入失败丢审计。
        // PLATFORM => kindergarten_id 必须 NULL（不伪造 kindergarten id——SPEC §284）。
        if (scopeType == UserRoleAssignmentScopeType.PLATFORM) {
            kindergartenId = null;
        } else if (scopeType == UserRoleAssignmentScopeType.KINDERGARTEN && kindergartenId == null) {
            // 园级事件却无 tenant id 会违反 CHECK；降级为 PLATFORM scope 以保留事件（actor 仍记录）。
            scopeType = UserRoleAssignmentScopeType.PLATFORM;
        }

        AuditLog entry = new AuditLog();
        entry.setScopeType(scopeType);
        entry.setAction(event.action().name());
        entry.setResult(event.result().name());
        entry.setEffectiveRole(event.effectiveRole());
        entry.setResourceType(truncate(event.resourceType(), MAX_RESOURCE_TYPE));
        entry.setResourceId(event.resourceId());
        entry.setCorrelationId(MDC.get(CorrelationIdFilter.MDC_KEY));
        if (event.actorUserId() != null) {
            entry.setUser(userRepository.getReferenceById(event.actorUserId()));
        }
        if (kindergartenId != null) {
            entry.setKindergarten(kindergartenRepository.getReferenceById(kindergartenId));
        }
        applyRequestMetadata(entry);

        auditLogRepository.save(entry);
    }

    private void applyRequestMetadata(AuditLog entry) {
        HttpServletRequest request = currentRequest();
        if (request == null) {
            return;
        }
        // remoteAddr：反代后为代理 IP；可信 X-Forwarded-For 解析待边缘 TLS/反代定案（ADR-0017）后再加。
        entry.setIp(request.getRemoteAddr());
        entry.setUserAgent(truncate(request.getHeader("User-Agent"), MAX_USER_AGENT));
    }

    private HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            return attrs.getRequest();
        }
        return null;
    }

    private static String truncate(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, max);
    }
}
