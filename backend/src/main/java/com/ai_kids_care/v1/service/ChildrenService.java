package com.ai_kids_care.v1.service;

import com.ai_kids_care.v1.config.RrnHashConfig;
import com.ai_kids_care.v1.entity.Child;
import com.ai_kids_care.v1.mapper.ChildMapper;
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
import com.ai_kids_care.v1.vo.ChildVO;
import com.ai_kids_care.v1.vo.GuardianChildVO;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
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

    // ── 内部 / 历史方法（不发布；保留供注册流程与契约测试使用） ─────────────────────

    public Page<ChildVO> listChildren(String keyword, Pageable pageable) {
        return repository.findByNameContains(keyword, pageable).map(mapper::toVO);
    }

    public ChildVO getChild(Long id) {
        return repository.findById(id).map(mapper::toVO).orElseThrow(() -> new EntityNotFoundException("Children not found"));
    }

    /**
     * HMAC 우선 + BCrypt 회퇴 + 게으른 역충전(lazy backfill) — ADR-0024 D3.
     *
     * <p>1. HMAC 해시로 빠른 단일 조회(O(1) index scan).
     * <p>2. 미스 시 BCrypt 후보 필터링(레거시 행, rrn_hash IS NULL).
     * <p>3. BCrypt 명중 시 rrn_hash를 동일 트랜잭션 내 역충전.
     *
     * <p>트랜잭션 전략(Q3): REQUIRES_NEW 로 독립 쓰기 트랜잭션 확보.
     * 호출자(AuthService)가 readOnly=true 인 경우에도 역충전 쓰기가 안전하게 커밋된다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    Optional<Child> getChildEntityByRRN(String rrn_First6, String rrn_Last7) {
        // Step 1: HMAC 명중 경로
        String hash = RrnHashUtil.hash(rrnHashConfig.getPepper(), rrn_First6, rrn_Last7);
        Optional<Child> byHash = repository.findByRrnHash(hash);
        if (byHash.isPresent()) {
            return byHash;
        }

        // Step 2: BCrypt 회퇴 경로 (rrn_hash IS NULL 인 레거시 행)
        Optional<Child> fallback = repository.findByRrnFirst6(rrn_First6).stream()
                .filter(child -> child.getRrnEncrypted() != null
                        && passwordEncoder.matches(rrn_Last7, child.getRrnEncrypted()))
                .findFirst();

        // Step 3: 게으른 역충전 (명중한 경우만 씀)
        if (fallback.isPresent()) {
            Child child = fallback.get();
            child.setRrnHash(hash);
            repository.save(child);
        }

        return fallback;
    }
}
