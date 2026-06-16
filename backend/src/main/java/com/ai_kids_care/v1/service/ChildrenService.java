package com.ai_kids_care.v1.service;

import com.ai_kids_care.v1.entity.Child;
import com.ai_kids_care.v1.mapper.ChildMapper;
import com.ai_kids_care.v1.repository.ChildRepository;
import com.ai_kids_care.v1.security.EffectiveAuthorizationContext;
import com.ai_kids_care.v1.security.EffectiveAuthorizationContextHolder;
import com.ai_kids_care.v1.security.audit.AuditAction;
import com.ai_kids_care.v1.security.audit.AuditEvent;
import com.ai_kids_care.v1.security.audit.AuditResult;
import com.ai_kids_care.v1.security.audit.SecurityAuditWriter;
import com.ai_kids_care.v1.type.UserRoleAssignmentScopeType;
import com.ai_kids_care.v1.vo.ChildVO;
import com.ai_kids_care.v1.vo.GuardianChildVO;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ChildrenService {

    private final ChildRepository repository;
    private final ChildMapper mapper;
    private final PasswordEncoder passwordEncoder;
    private final SecurityAuditWriter auditWriter;

    // ── SPEC-0001 §3 / §349：Guardian 关系-scoped 读取 ───────────────────────────

    /**
     * GET /api/v1/children —— Guardian 列出与自己有 ACTIVE 关系的儿童（最小字段，无 S0/S1）。
     * 关系条件在 ChildRepository 的 SQL 内强制；仅同租户 + 活跃关系 + 活跃儿童。
     */
    @Transactional(readOnly = true)
    @PreAuthorize("@authorizationPolicy.isAllowed(T(com.ai_kids_care.v1.security.AuthorizationAction).GUARDIAN_CHILD_READ)")
    public List<GuardianChildVO> listRelatedChildren() {
        EffectiveAuthorizationContext context = EffectiveAuthorizationContextHolder.require();
        Long kindergartenId = EffectiveAuthorizationContextHolder.requireActiveKindergartenId();
        return repository.findRelatedChildrenForGuardian(
                        kindergartenId, context.userId(), LocalDate.now())
                .stream()
                .map(this::toGuardianChildVO)
                .toList();
    }

    /**
     * GET /api/v1/children/{id} —— Guardian 读取单个关联儿童。
     * 无 ACTIVE 关系（跨租户 / 不存在 / 关系已结束）→ 审计拒绝 + 隐藏 404（SPEC §3.4 / §349）。
     */
    @Transactional(readOnly = true)
    @PreAuthorize("@authorizationPolicy.isAllowed(T(com.ai_kids_care.v1.security.AuthorizationAction).GUARDIAN_CHILD_READ)")
    public GuardianChildVO getRelatedChild(Long childId) {
        EffectiveAuthorizationContext context = EffectiveAuthorizationContextHolder.require();
        Long kindergartenId = EffectiveAuthorizationContextHolder.requireActiveKindergartenId();
        Optional<Child> found = repository.findRelatedChildForGuardian(
                childId, kindergartenId, context.userId(), LocalDate.now());
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

    // ── 内部 / 历史方法（不发布；保留供注册流程与契约测试使用） ─────────────────────

    public Page<ChildVO> listChildren(String keyword, Pageable pageable) {
        return repository.findByNameContains(keyword, pageable).map(mapper::toVO);
    }

    public ChildVO getChild(Long id) {
        return repository.findById(id).map(mapper::toVO).orElseThrow(() -> new EntityNotFoundException("Children not found"));
    }

    Optional<Child> getChildEntityByRRN(String rrn_First6, String rrn_Last7) {
        return repository.findByRrnFirst6(rrn_First6).stream()
                .filter(child -> passwordEncoder.matches(rrn_Last7, child.getRrnEncrypted()))
                .findFirst();
    }
}
