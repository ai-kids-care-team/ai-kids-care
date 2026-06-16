package com.ai_kids_care.v1.service;

import com.ai_kids_care.v1.entity.UserRoleAssignment;
import com.ai_kids_care.v1.repository.SuperadminRepository;
import com.ai_kids_care.v1.repository.UserRepository;
import com.ai_kids_care.v1.repository.UserRoleAssignmentRepository;
import com.ai_kids_care.v1.security.EffectiveAuthorizationContext;
import com.ai_kids_care.v1.security.EffectiveAuthorizationContextHolder;
import com.ai_kids_care.v1.security.PlatformPolicy;
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
 * SPEC-0002 Slice B: 平台级审批 / 拒绝 / 停用服务。
 *
 * 授权分层（ADR-0019 §2）：
 * - 粗粒度门：@PreAuthorize 检查 PLATFORM_IT_ADMIN + PLATFORM scope（在 AuthorizationPolicy）。
 * - 细粒度：PlatformPolicy 在事务内做禁自审判断；
 *   平台级 role 隔离由 scopeId IS NULL + role=SUPERADMIN 条件更新保证
 *   （0 行 → 隐藏 404，OQ-4 裁定）。
 *
 * 平台特有差异（区别于园级 KindergartenAdminApprovalService）：
 * 1. PLATFORM role assignment 的 scope_id 为 NULL；不能使用 = :scopeId 条件（NULL != NULL）。
 *    必须使用 platformConditionalUpdateStatus / platformConditionalDisable（含 IS NULL 条件）。
 * 2. 平台账户无 membership；approve/disable 不碰 user_kindergarten_memberships。
 * 3. 目标必须是 SUPERADMIN；条件更新加 AND role=SUPERADMIN——非 SUPERADMIN 目标 → 0 行 → 隐藏 404。
 * 4. approve 激活 Superadmin 业务档案（conditionalUpdateStatus PENDING→ACTIVE）。
 *
 * TOCTOU 防护（ADR-0019 §4）：
 * - 以 PLATFORM role assignment 的条件更新（含 role=SUPERADMIN AND scopeId IS NULL）作为授权门。
 * - 0 行 → EntityNotFoundException → 事务回滚 → 隐藏 404。
 * - 其余（user/superadmin 档案）在 role 门成功后顺序执行，各查行数兜底。
 *
 * 会话吊销（ADR-0019 §6）：
 * - disable：在事务提交后调用 SessionRevocationService.revokeAllForUser(targetUserId)。
 */
@Service
@RequiredArgsConstructor
public class PlatformAdminApprovalService {

    private final PlatformPolicy platformPolicy;
    private final UserRepository userRepository;
    private final UserRoleAssignmentRepository roleAssignmentRepository;
    private final SuperadminRepository superadminRepository;
    private final SessionRevocationService sessionRevocationService;

    // ─── LIST ────────────────────────────────────────────────────────────────

    /**
     * GET /api/v1/admin/platform/superadmin-registrations?status=PENDING
     * 列出 PENDING SUPERADMIN 申请（最小字段，无 S1）。
     */
    @Transactional(readOnly = true)
    @PreAuthorize("@authorizationPolicy.isAllowed(T(com.ai_kids_care.v1.security.AuthorizationAction).PLATFORM_SUPERADMIN_APPROVAL_READ)")
    public List<PendingRegistrationVO> listPendingSuperadminRegistrations() {
        // 粗粒度已过；平台无 level 要求，无须调用 requireEligibleLevel
        List<UserRoleAssignment> pending = roleAssignmentRepository
                .findAllByStatusAndScopeTypeAndScopeIdIsNullAndRole(
                        StatusEnum.PENDING,
                        UserRoleAssignmentScopeType.PLATFORM,
                        UserRoleEnum.SUPERADMIN);

        return pending.stream()
                .map(ura -> new PendingRegistrationVO(
                        ura.getUser().getId(),
                        ura.getRole().name(),
                        null,          // SUPERADMIN 无 level（平台无此概念）
                        ura.getGrantedAt()))
                .toList();
    }

    // ─── APPROVE ─────────────────────────────────────────────────────────────

    /**
     * POST /api/v1/admin/platform/superadmin-registrations/{userId}/approve
     * PENDING → ACTIVE（user + superadmin 档案 + PLATFORM role assignment，单一事务）。
     * 平台账户无 membership，不碰 user_kindergarten_memberships。
     */
    @Transactional
    @PreAuthorize("@authorizationPolicy.isAllowed(T(com.ai_kids_care.v1.security.AuthorizationAction).PLATFORM_SUPERADMIN_APPROVAL_WRITE)")
    public void approve(Long targetUserId) {
        EffectiveAuthorizationContext context = EffectiveAuthorizationContextHolder.require();

        // 细粒度校验：禁自审
        platformPolicy.requireNotSelf(context, targetUserId);

        // 授权门：条件更新 PLATFORM role assignment PENDING→ACTIVE（scopeId IS NULL + role=SUPERADMIN）
        // 0 行 → 目标不存在 / 非 SUPERADMIN / 已被处理 / 状态不符 → 隐藏 404（OQ-4）
        int roleRows = roleAssignmentRepository.platformConditionalUpdateStatus(
                targetUserId,
                StatusEnum.PENDING,
                StatusEnum.ACTIVE,
                UserRoleAssignmentScopeType.PLATFORM,
                UserRoleEnum.SUPERADMIN,
                userRepository.getReferenceById(context.userId()));
        if (roleRows == 0) {
            throw new EntityNotFoundException("Registration not found");
        }

        // 条件更新：Superadmin 档案 PENDING→ACTIVE
        int superadminRows = superadminRepository.conditionalUpdateStatus(
                targetUserId, StatusEnum.PENDING, StatusEnum.ACTIVE);
        if (superadminRows == 0) {
            // 档案数据不一致（数据库完整性漏洞）→ 回滚，隐藏 404
            throw new EntityNotFoundException("Registration not found");
        }

        // 条件更新：User PENDING→ACTIVE
        int userRows = userRepository.conditionalUpdateStatus(
                targetUserId, StatusEnum.PENDING, StatusEnum.ACTIVE);
        if (userRows == 0) {
            throw new EntityNotFoundException("Registration not found");
        }

        // TODO(SPEC-0002 #1): SecurityAuditWriter.record(actor=context.userId(), target=targetUserId,
        //     action=PLATFORM_SUPERADMIN_APPROVE, scopeType=PLATFORM, result=SUCCESS)
    }

    // ─── REJECT ──────────────────────────────────────────────────────────────

    /**
     * POST /api/v1/admin/platform/superadmin-registrations/{userId}/reject
     * PENDING → REJECTED（user + superadmin 档案 + PLATFORM role assignment，单一事务）。
     */
    @Transactional
    @PreAuthorize("@authorizationPolicy.isAllowed(T(com.ai_kids_care.v1.security.AuthorizationAction).PLATFORM_SUPERADMIN_APPROVAL_WRITE)")
    public void reject(Long targetUserId) {
        EffectiveAuthorizationContext context = EffectiveAuthorizationContextHolder.require();

        // 细粒度校验：禁自审
        platformPolicy.requireNotSelf(context, targetUserId);

        // 授权门：条件更新 PLATFORM role assignment PENDING→REJECTED（scopeId IS NULL + role=SUPERADMIN）
        int roleRows = roleAssignmentRepository.platformConditionalUpdateStatus(
                targetUserId,
                StatusEnum.PENDING,
                StatusEnum.REJECTED,
                UserRoleAssignmentScopeType.PLATFORM,
                UserRoleEnum.SUPERADMIN,
                userRepository.getReferenceById(context.userId()));
        if (roleRows == 0) {
            throw new EntityNotFoundException("Registration not found");
        }

        // 条件更新：Superadmin 档案 PENDING→REJECTED
        int superadminRows = superadminRepository.conditionalUpdateStatus(
                targetUserId, StatusEnum.PENDING, StatusEnum.REJECTED);
        if (superadminRows == 0) {
            throw new EntityNotFoundException("Registration not found");
        }

        // 条件更新：User PENDING→REJECTED
        int userRows = userRepository.conditionalUpdateStatus(
                targetUserId, StatusEnum.PENDING, StatusEnum.REJECTED);
        if (userRows == 0) {
            throw new EntityNotFoundException("Registration not found");
        }

        // TODO(SPEC-0002 #1): SecurityAuditWriter.record(actor=context.userId(), target=targetUserId,
        //     action=PLATFORM_SUPERADMIN_REJECT, scopeType=PLATFORM, result=SUCCESS)
    }

    // ─── DISABLE ─────────────────────────────────────────────────────────────

    /**
     * POST /api/v1/admin/platform/users/{userId}/disable
     * ACTIVE → DISABLED（user + PLATFORM role assignment，单一事务）。
     * 事务提交后调用 SessionRevocationService（ADR-0019 §6）。
     * 平台账户无 membership，不碰 user_kindergarten_memberships。
     * PLATFORM_IT_ADMIN 自身的 role 为 PLATFORM_IT_ADMIN 而非 SUPERADMIN，
     * 故以 role=SUPERADMIN 条件停用也隐式阻止误停 PLATFORM_IT_ADMIN 自身。
     * 禁自审（403）在 platformPolicy.requireNotSelf 中额外防护。
     */
    @Transactional
    @PreAuthorize("@authorizationPolicy.isAllowed(T(com.ai_kids_care.v1.security.AuthorizationAction).PLATFORM_USER_WRITE)")
    public void disable(Long targetUserId) {
        EffectiveAuthorizationContext context = EffectiveAuthorizationContextHolder.require();
        OffsetDateTime now = OffsetDateTime.now();

        // 细粒度校验：禁自审
        platformPolicy.requireNotSelf(context, targetUserId);

        // 授权门：条件更新 PLATFORM role assignment ACTIVE→DISABLED（scopeId IS NULL + role=SUPERADMIN）
        // 0 行 → 目标在平台无 ACTIVE SUPERADMIN role → 跨 scope / 不存在 / 已停用 → 隐藏 404（OQ-4）
        int roleRows = roleAssignmentRepository.platformConditionalDisable(
                targetUserId,
                UserRoleAssignmentScopeType.PLATFORM,
                UserRoleEnum.SUPERADMIN,
                now,
                userRepository.getReferenceById(context.userId()),
                StatusEnum.ACTIVE,
                StatusEnum.DISABLED);
        if (roleRows == 0) {
            throw new EntityNotFoundException("User not found");
        }

        // 条件更新：User ACTIVE→DISABLED
        int userRows = userRepository.conditionalUpdateStatus(
                targetUserId, StatusEnum.ACTIVE, StatusEnum.DISABLED);
        if (userRows == 0) {
            throw new EntityNotFoundException("User not found");
        }

        // 条件更新：Superadmin 档案 ACTIVE→DISABLED（平台账户 superadmin 档案）
        // disable 端点面向 SUPERADMIN；档案若存在则停用，不存在时忽略（PLATFORM_IT_ADMIN 无 superadmin 档案）
        superadminRepository.conditionalUpdateStatus(
                targetUserId, StatusEnum.ACTIVE, StatusEnum.DISABLED);

        // TODO(SPEC-0002 #1): SecurityAuditWriter.record(actor=context.userId(), target=targetUserId,
        //     action=PLATFORM_USER_DISABLE, scopeType=PLATFORM, result=SUCCESS)

        // ADR-0019 §6: 事务提交后吊销目标用户全部 session（快路径；每请求权威解析为正确性兜底）
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                sessionRevocationService.revokeAllForUser(targetUserId);
            }
        });
    }
}
