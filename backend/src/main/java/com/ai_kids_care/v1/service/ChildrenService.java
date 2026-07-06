package com.ai_kids_care.v1.service;

import com.ai_kids_care.v1.config.RrnHashConfig;
import com.ai_kids_care.v1.entity.Child;
import com.ai_kids_care.v1.repository.ChildRepository;
import com.ai_kids_care.v1.security.EffectiveAuthorizationContext;
import com.ai_kids_care.v1.security.EffectiveAuthorizationContextHolder;
import com.ai_kids_care.v1.security.RrnHashUtil;
import com.ai_kids_care.v1.security.audit.AuditAction;
import com.ai_kids_care.v1.security.audit.AuditEvent;
import com.ai_kids_care.v1.security.audit.AuditResult;
import com.ai_kids_care.v1.security.audit.SecurityAuditWriter;
import com.ai_kids_care.v1.type.UserRoleAssignmentScopeType;
import com.ai_kids_care.v1.type.UserRoleEnum;
import com.ai_kids_care.v1.vo.GuardianChildVO;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ChildrenService {

    private final ChildRepository repository;
    private final RrnHashConfig rrnHashConfig;
    private final SecurityAuditWriter auditWriter;

    // ── SPEC-0001 §3 / §349 / §351：儿童读取（Guardian 关系-scoped + Teacher assignment-scoped）──

    /**
     * GET /api/v1/children —— 列出当前用户可见儿童（最小字段，无 S0/S1）。
     * GUARDIAN → 关系-scoped（ChildRepository guardian 查询）；
     * TEACHER  → assignment-scoped（ChildRepository teacher 嵌套 EXISTS 查询）；
     * 其他角色不应通过粗粒度门，兜底返回空列表。
     */
    @Transactional(readOnly = true)
    @PreAuthorize("@authorizationPolicy.isAllowed(T(com.ai_kids_care.v1.security.AuthorizationAction).CHILD_READ)")
    public List<GuardianChildVO> listRelatedChildren() {
        EffectiveAuthorizationContext context = EffectiveAuthorizationContextHolder.require();
        Long kindergartenId = EffectiveAuthorizationContextHolder.requireActiveKindergartenId();
        LocalDate today = LocalDate.now();
        return switch (context.role()) {
            case GUARDIAN -> repository.findRelatedChildrenForGuardian(
                            kindergartenId, context.userId(), today)
                    .stream()
                    .map(this::toGuardianChildVO)
                    .toList();
            case TEACHER -> repository.findActivelyAssignedChildrenForTeacher(
                            kindergartenId, context.userId(), today)
                    .stream()
                    .map(this::toGuardianChildVO)
                    .toList();
            default -> List.of();
        };
    }

    /**
     * GET /api/v1/children/{id} —— 读取单个儿童。
     * GUARDIAN → 关系-scoped；TEACHER → assignment-scoped。
     * 不可见（跨租户 / 无关系或分配 / 已结束）→ 审计拒绝 + 隐藏 404（SPEC §3.4 / §349 / §351）。
     */
    @Transactional(readOnly = true)
    @PreAuthorize("@authorizationPolicy.isAllowed(T(com.ai_kids_care.v1.security.AuthorizationAction).CHILD_READ)")
    public GuardianChildVO getRelatedChild(Long childId) {
        EffectiveAuthorizationContext context = EffectiveAuthorizationContextHolder.require();
        Long kindergartenId = EffectiveAuthorizationContextHolder.requireActiveKindergartenId();
        LocalDate today = LocalDate.now();
        Optional<Child> found = switch (context.role()) {
            case GUARDIAN -> repository.findRelatedChildForGuardian(
                    childId, kindergartenId, context.userId(), today);
            case TEACHER -> repository.findActivelyAssignedChildForTeacher(
                    childId, kindergartenId, context.userId(), today);
            default -> Optional.empty();
        };
        if (found.isEmpty()) {
            auditWriter.record(AuditEvent.builder()
                    .action(AuditAction.AUTHORIZATION_DENIED)
                    .result(AuditResult.DENIED)
                    .actorUserId(context.userId())
                    .scopeType(UserRoleAssignmentScopeType.KINDERGARTEN)
                    .kindergartenId(kindergartenId)
                    .effectiveRole(context.role().name())
                    .resourceType("CHILD")
                    .resourceId(childId)
                    .build());
            throw new EntityNotFoundException("Child not found");
        }
        return toGuardianChildVO(found.get());
    }

    private GuardianChildVO toGuardianChildVO(Child child) {
        return new GuardianChildVO(child.getId(), child.getName(), child.getStatus().name());
    }

    /**
     * HMAC 단일 조회 경로 — ADR-0024 D4 (Phase 3).
     *
     * <p>V5/V6 적용 완료 후: rrn_hash NOT NULL, rrn_encrypted 열 삭제.
     * BCrypt 회퇴 경로 및 게으른 역충전은 제거되었다.
     * 읽기 전용 트랜잭션으로 동작한다.
     */
    @Transactional(readOnly = true)
    Optional<Child> getChildEntityByRRN(String rrn_First6, String rrn_Last7) {
        String hash = RrnHashUtil.hash(rrnHashConfig.getPepper(), rrn_First6, rrn_Last7);
        return repository.findByRrnHash(hash);
    }
}
