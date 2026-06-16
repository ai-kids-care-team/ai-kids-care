package com.ai_kids_care.v1.service;

import com.ai_kids_care.v1.entity.UserRoleAssignment;
import com.ai_kids_care.v1.repository.GuardianRepository;
import com.ai_kids_care.v1.repository.TeacherRepository;
import com.ai_kids_care.v1.repository.UserKindergartenMembershipRepository;
import com.ai_kids_care.v1.repository.UserRepository;
import com.ai_kids_care.v1.repository.UserRoleAssignmentRepository;
import com.ai_kids_care.v1.security.EffectiveAuthorizationContext;
import com.ai_kids_care.v1.security.EffectiveAuthorizationContextHolder;
import com.ai_kids_care.v1.security.KindergartenAdminPolicy;
import com.ai_kids_care.v1.security.SessionRevocationService;
import com.ai_kids_care.v1.type.StatusEnum;
import com.ai_kids_care.v1.type.UserRoleAssignmentScopeType;
import com.ai_kids_care.v1.type.UserRoleEnum;
import com.ai_kids_care.v1.vo.PendingRegistrationVO;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * SPEC-0002 Slice A: 园级审批 / 拒绝 / 停用服务。
 *
 * 授权分层（ADR-0019 §2）：
 * - 粗粒度门：@PreAuthorize 检查 KINDERGARTEN_ADMIN + tenantIdentity（在 AuthorizationPolicy）。
 * - 细粒度：KindergartenAdminPolicy 在事务内做 level 与禁自审判断；
 *   tenant 关系由 tenant-aware 条件更新保证（同园 + 正确 status 才更新，否则 0 行 → 隐藏 404）。
 *
 * TOCTOU 防护（ADR-0019 §4）：
 * - 批准/拒绝：user/档案/membership/role assignment 各用条件更新 WHERE status=PENDING。
 * - 停用：各用条件更新 WHERE status=ACTIVE。
 * - 只要有一个资源更新返回 0 行（已被他人处理/跨园/不存在），整个事务回滚 → 隐藏 404（OQ-4 裁定）。
 *
 * 会话吊销（ADR-0019 §6）：
 * - disable：在事务提交后调用 SessionRevocationService.revokeAllForUser(targetUserId)。
 */
@Service
@RequiredArgsConstructor
public class KindergartenAdminApprovalService {

    private final KindergartenAdminPolicy kinderAdminPolicy;
    private final UserRepository userRepository;
    private final UserRoleAssignmentRepository roleAssignmentRepository;
    private final UserKindergartenMembershipRepository membershipRepository;
    private final TeacherRepository teacherRepository;
    private final GuardianRepository guardianRepository;
    private final SessionRevocationService sessionRevocationService;

    // ─── LIST ────────────────────────────────────────────────────────────────

    /**
     * GET /api/v1/admin/kindergarten/registrations?status=PENDING
     * 列出本园 PENDING 申请（最小字段，无 S1）。
     */
    @Transactional(readOnly = true)
    @PreAuthorize("@authorizationPolicy.isAllowed(T(com.ai_kids_care.v1.security.AuthorizationAction).KINDERGARTEN_ADMIN_APPROVAL_READ)")
    public List<PendingRegistrationVO> listPendingRegistrations() {
        EffectiveAuthorizationContext context = EffectiveAuthorizationContextHolder.require();
        Long kindergartenId = context.activeKindergartenId();

        // 细粒度 level 校验（DIRECTOR/VICE_DIRECTOR）
        kinderAdminPolicy.requireEligibleLevel(context);

        List<UserRoleAssignment> pending = roleAssignmentRepository
                .findAllByStatusAndScopeTypeAndScopeId(
                        StatusEnum.PENDING,
                        UserRoleAssignmentScopeType.KINDERGARTEN,
                        kindergartenId);

        return pending.stream()
                .map(ura -> {
                    // level: 仅 TEACHER / KINDERGARTEN_ADMIN 有 teacher 档案
                    String level = resolveLevel(ura.getUser().getId(), kindergartenId, ura.getRole());
                    return new PendingRegistrationVO(
                            ura.getUser().getId(),
                            ura.getRole().name(),
                            level,
                            ura.getGrantedAt()  // registration submitted_at == grantedAt（注册时写入）
                    );
                })
                .toList();
    }

    // ─── APPROVE ─────────────────────────────────────────────────────────────

    /**
     * POST /api/v1/admin/kindergarten/registrations/{userId}/approve
     * PENDING → ACTIVE（user + 档案 + membership + role assignment，单一事务）。
     */
    @Transactional
    @PreAuthorize("@authorizationPolicy.isAllowed(T(com.ai_kids_care.v1.security.AuthorizationAction).KINDERGARTEN_ADMIN_APPROVAL_WRITE)")
    public void approve(Long targetUserId) {
        EffectiveAuthorizationContext context = EffectiveAuthorizationContextHolder.require();
        Long kindergartenId = context.activeKindergartenId();

        // 细粒度校验：level + 禁自审
        kinderAdminPolicy.requireEligibleLevel(context);
        kinderAdminPolicy.requireNotSelf(context, targetUserId);

        // 条件更新：User PENDING→ACTIVE
        int userRows = userRepository.conditionalUpdateStatus(
                targetUserId, StatusEnum.PENDING, StatusEnum.ACTIVE);
        if (userRows == 0) {
            // 目标不存在 / 已被处理 / 状态不符 → 隐藏 404（OQ-4）
            throw new EntityNotFoundException("Registration not found");
        }

        // 条件更新：membership PENDING→ACTIVE（同园约束）
        int memberRows = membershipRepository.conditionalUpdateStatus(
                targetUserId, kindergartenId, StatusEnum.PENDING, StatusEnum.ACTIVE);
        if (memberRows == 0) {
            throw new EntityNotFoundException("Registration not found");
        }

        // 条件更新：role assignment PENDING→ACTIVE（同园约束）
        int roleRows = roleAssignmentRepository.conditionalUpdateStatus(
                targetUserId, StatusEnum.PENDING, StatusEnum.ACTIVE,
                UserRoleAssignmentScopeType.KINDERGARTEN, kindergartenId,
                userRepository.getReferenceById(context.userId()));
        if (roleRows == 0) {
            throw new EntityNotFoundException("Registration not found");
        }

        // 条件更新：业务档案（Teacher 或 Guardian，按 role assignment 类型）PENDING→ACTIVE
        activateProfileIfExists(targetUserId, kindergartenId, StatusEnum.PENDING, StatusEnum.ACTIVE);

        // TODO(SPEC-0002 #1): SecurityAuditWriter.record(actor=context.userId(), target=targetUserId,
        //     action=KINDERGARTEN_APPROVE, kindergartenId=kindergartenId, result=SUCCESS)
    }

    // ─── REJECT ──────────────────────────────────────────────────────────────

    /**
     * POST /api/v1/admin/kindergarten/registrations/{userId}/reject
     * PENDING → REJECTED（user + 档案 + membership + role assignment，单一事务）。
     */
    @Transactional
    @PreAuthorize("@authorizationPolicy.isAllowed(T(com.ai_kids_care.v1.security.AuthorizationAction).KINDERGARTEN_ADMIN_APPROVAL_WRITE)")
    public void reject(Long targetUserId) {
        EffectiveAuthorizationContext context = EffectiveAuthorizationContextHolder.require();
        Long kindergartenId = context.activeKindergartenId();

        kinderAdminPolicy.requireEligibleLevel(context);
        kinderAdminPolicy.requireNotSelf(context, targetUserId);

        // 条件更新：User PENDING→REJECTED
        int userRows = userRepository.conditionalUpdateStatus(
                targetUserId, StatusEnum.PENDING, StatusEnum.REJECTED);
        if (userRows == 0) {
            throw new EntityNotFoundException("Registration not found");
        }

        // 条件更新：membership PENDING→REJECTED（同园约束——租户隔离门，0 行则回滚 + 隐藏 404）
        int memberRows = membershipRepository.conditionalUpdateStatus(
                targetUserId, kindergartenId, StatusEnum.PENDING, StatusEnum.REJECTED);
        if (memberRows == 0) {
            throw new EntityNotFoundException("Registration not found");
        }

        // 条件更新：role assignment PENDING→REJECTED（同园约束——租户隔离门）
        int roleRows = roleAssignmentRepository.conditionalUpdateStatus(
                targetUserId, StatusEnum.PENDING, StatusEnum.REJECTED,
                UserRoleAssignmentScopeType.KINDERGARTEN, kindergartenId,
                userRepository.getReferenceById(context.userId()));
        if (roleRows == 0) {
            throw new EntityNotFoundException("Registration not found");
        }

        // 条件更新：业务档案 PENDING→REJECTED
        activateProfileIfExists(targetUserId, kindergartenId, StatusEnum.PENDING, StatusEnum.REJECTED);

        // TODO(SPEC-0002 #1): SecurityAuditWriter.record(actor=context.userId(), target=targetUserId,
        //     action=KINDERGARTEN_REJECT, kindergartenId=kindergartenId, result=SUCCESS)
    }

    // ─── DISABLE ─────────────────────────────────────────────────────────────

    /**
     * POST /api/v1/admin/kindergarten/members/{userId}/disable
     * ACTIVE → DISABLED（user + 档案 + membership + role assignment，单一事务）。
     * 事务提交后调用 SessionRevocationService（ADR-0019 §6）。
     */
    @Transactional
    @PreAuthorize("@authorizationPolicy.isAllowed(T(com.ai_kids_care.v1.security.AuthorizationAction).KINDERGARTEN_ADMIN_MEMBER_WRITE)")
    public void disable(Long targetUserId) {
        EffectiveAuthorizationContext context = EffectiveAuthorizationContextHolder.require();
        Long kindergartenId = context.activeKindergartenId();
        OffsetDateTime now = OffsetDateTime.now();

        kinderAdminPolicy.requireEligibleLevel(context);
        kinderAdminPolicy.requireNotSelf(context, targetUserId);

        // 条件更新：role assignment ACTIVE→DISABLED（同园约束，填 revokedAt/revokedByUser）
        int roleRows = roleAssignmentRepository.conditionalDisable(
                targetUserId,
                UserRoleAssignmentScopeType.KINDERGARTEN,
                kindergartenId,
                now,
                userRepository.getReferenceById(context.userId()),
                StatusEnum.ACTIVE,
                StatusEnum.DISABLED);
        if (roleRows == 0) {
            // 目标在本园无 ACTIVE role → 跨园 or 不存在 → 隐藏 404（OQ-4）
            throw new EntityNotFoundException("Member not found");
        }

        // 条件更新：membership ACTIVE→DISABLED（同园约束，填 leftAt）
        int memberRows = membershipRepository.conditionalDisable(
                targetUserId, kindergartenId, now,
                StatusEnum.ACTIVE, StatusEnum.DISABLED);
        if (memberRows == 0) {
            throw new EntityNotFoundException("Member not found");
        }

        // 条件更新：User ACTIVE→DISABLED
        int userRows = userRepository.conditionalUpdateStatus(
                targetUserId, StatusEnum.ACTIVE, StatusEnum.DISABLED);
        if (userRows == 0) {
            throw new EntityNotFoundException("Member not found");
        }

        // 业务档案 ACTIVE→DISABLED
        disableProfileIfExists(targetUserId, kindergartenId);

        // TODO(SPEC-0002 #1): SecurityAuditWriter.record(actor=context.userId(), target=targetUserId,
        //     action=KINDERGARTEN_DISABLE_MEMBER, kindergartenId=kindergartenId, result=SUCCESS)

        // ADR-0019 §6: 事务提交后吊销目标用户全部 session（快路径；每请求权威解析为正确性兜底）
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                sessionRevocationService.revokeAllForUser(targetUserId);
            }
        });
    }

    // ─── Private helpers ─────────────────────────────────────────────────────

    /**
     * 激活/拒绝业务档案（PENDING→newStatus）。
     * 园级目标（GUARDIAN/TEACHER/KINDERGARTEN_ADMIN 注册）必有 Teacher 或 Guardian 档案；
     * 二者均无匹配行 = 数据异常 → 抛 EntityNotFoundException 回滚（防御性，release-gate P2-1）。
     */
    private void activateProfileIfExists(Long targetUserId, Long kindergartenId,
                                         StatusEnum expected, StatusEnum newStatus) {
        // 尝试 Teacher 档案（KINDERGARTEN_ADMIN 的 DIRECTOR/VICE_DIRECTOR 档案在 teachers 表）
        int tRows = teacherRepository.conditionalUpdateStatus(
                targetUserId, kindergartenId, expected, newStatus);
        if (tRows > 0) return;
        // 尝试 Guardian 档案
        int gRows = guardianRepository.conditionalUpdateStatus(
                targetUserId, kindergartenId, expected, newStatus);
        if (gRows == 0) {
            throw new EntityNotFoundException("Registration not found");
        }
    }

    /**
     * 停用业务档案（ACTIVE→DISABLED）。
     * 园级在职成员必有 Teacher 或 Guardian 档案；二者均无 → 抛 EntityNotFoundException 回滚（防御性，release-gate P2-1）。
     */
    private void disableProfileIfExists(Long targetUserId, Long kindergartenId) {
        int tRows = teacherRepository.conditionalUpdateStatus(
                targetUserId, kindergartenId, StatusEnum.ACTIVE, StatusEnum.DISABLED);
        if (tRows > 0) return;
        int gRows = guardianRepository.conditionalUpdateStatus(
                targetUserId, kindergartenId, StatusEnum.ACTIVE, StatusEnum.DISABLED);
        if (gRows == 0) {
            throw new EntityNotFoundException("Member not found");
        }
    }

    /**
     * 根据申请 role 查询 teacher 档案的 level（列表使用）。
     * 无档案（如未来其他 role 类型）返回 null。
     */
    private String resolveLevel(Long userId, Long kindergartenId, UserRoleEnum role) {
        if (role == UserRoleEnum.TEACHER || role == UserRoleEnum.KINDERGARTEN_ADMIN) {
            return teacherRepository
                    .findByUser_IdAndKindergarten_IdAndStatus(userId, kindergartenId, StatusEnum.PENDING)
                    .map(t -> t.getLevel().name())
                    .orElse(null);
        }
        return null;
    }
}
