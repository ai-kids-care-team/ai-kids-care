package com.ai_kids_care.v1.service;

import com.ai_kids_care.v1.dto.NotificationCreateDTO;
import com.ai_kids_care.v1.dto.NotificationUpdateDTO;
import com.ai_kids_care.v1.entity.Notification;
import com.ai_kids_care.v1.mapper.NotificationMapper;
import com.ai_kids_care.v1.repository.NotificationRepository;
import com.ai_kids_care.v1.security.EffectiveAuthorizationContext;
import com.ai_kids_care.v1.security.EffectiveAuthorizationContextHolder;
import com.ai_kids_care.v1.security.audit.AuditAction;
import com.ai_kids_care.v1.security.audit.AuditEvent;
import com.ai_kids_care.v1.security.audit.AuditResult;
import com.ai_kids_care.v1.security.audit.SecurityAuditWriter;
import com.ai_kids_care.v1.type.NotificationChannelEnum;
import com.ai_kids_care.v1.type.UserRoleAssignmentScopeType;
import com.ai_kids_care.v1.type.UserRoleEnum;
import com.ai_kids_care.v1.vo.NotificationReadVO;
import com.ai_kids_care.v1.vo.NotificationVO;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import net.pushover.client.Status;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository repository;
    private final NotificationMapper mapper;
    private final PushoverService pushoverService;
    private final SecurityAuditWriter auditWriter;

    // ── SPEC-0001 / ADR-0018 A3d：通知读取（tenant-scoped；细粒度作用域由 Repository SQL 强制）────

    /**
     * GET /api/v1/notifications —— GUARDIAN/TEACHER 读取自己的通知列表；KINDERGARTEN_ADMIN 读取所在园的全部通知。
     * 细粒度作用域（受体仅自己 / Admin 仅本园）由 NotificationRepository 的 JPQL 在 SQL 内强制。
     */
    @Transactional(readOnly = true)
    @PreAuthorize("@authorizationPolicy.isAllowed(T(com.ai_kids_care.v1.security.AuthorizationAction).NOTIFICATION_READ)")
    public List<NotificationReadVO> listNotifications() {
        EffectiveAuthorizationContext context = EffectiveAuthorizationContextHolder.require();
        Long kindergartenId = EffectiveAuthorizationContextHolder.requireActiveKindergartenId();
        List<Notification> notifications = context.role() == UserRoleEnum.KINDERGARTEN_ADMIN
                ? repository.findKindergartenNotifications(kindergartenId)
                : repository.findRecipientNotifications(kindergartenId, context.userId());
        return notifications.stream().map(this::toReadVO).toList();
    }

    /**
     * GET /api/v1/notifications/{id} —— 受体读取自己的单条通知；KINDERGARTEN_ADMIN 读取本园的任意通知。
     * 无访问权（跨租户 / 他人通知 / 不存在）→ AUTHORIZATION_DENIED 审计 + 隐藏 404（SPEC §3.4）。
     */
    @Transactional(readOnly = true)
    @PreAuthorize("@authorizationPolicy.isAllowed(T(com.ai_kids_care.v1.security.AuthorizationAction).NOTIFICATION_READ)")
    public NotificationReadVO getNotification(Long id) {
        EffectiveAuthorizationContext context = EffectiveAuthorizationContextHolder.require();
        Long kindergartenId = EffectiveAuthorizationContextHolder.requireActiveKindergartenId();
        Optional<Notification> found = context.role() == UserRoleEnum.KINDERGARTEN_ADMIN
                ? repository.findKindergartenNotification(id, kindergartenId)
                : repository.findRecipientNotification(id, kindergartenId, context.userId());
        if (found.isEmpty()) {
            auditWriter.record(AuditEvent.builder()
                    .action(AuditAction.AUTHORIZATION_DENIED)
                    .result(AuditResult.DENIED)
                    .actorUserId(context.userId())
                    .scopeType(UserRoleAssignmentScopeType.KINDERGARTEN)
                    .kindergartenId(kindergartenId)
                    .effectiveRole(context.role().name())
                    .resourceType("NOTIFICATION")
                    .resourceId(id)
                    .build());
            throw new EntityNotFoundException("Notification not found");
        }
        return toReadVO(found.get());
    }

    private NotificationReadVO toReadVO(Notification n) {
        return new NotificationReadVO(
                n.getId(),
                n.getTitle(),
                n.getBody(),
                n.getStatus() == null ? null : n.getStatus().name(),
                n.getCreatedAt()
        );
    }

    // ── 内部 / 历史方法（不发布；保留供旧 CRUD 流程使用）────────────────────────────
    // 注意：listNotifications(keyword, pageable) 和 getNotificationInternal(id) は
    // NotificationController で公開されない（Phase 1A closed）。
    // getNotification(Long id) は上記の NOTIFICATION_READ メソッドとシグネチャが重複するため
    // 旧メソッドを getNotificationInternal に改名（内部用途のみ；旧 controller は空だったため呼び元なし）。

    public Page<NotificationVO> listNotifications(String keyword, Pageable pageable) {
        // TODO: filter Notification by keyword
        return repository.findAll(pageable).map(mapper::toVO);
    }

    public NotificationVO getNotificationInternal(Long id) {
        return repository.findById(id).map(mapper::toVO)
                .orElseThrow(() -> new EntityNotFoundException("Notification not found"));
    }

    public NotificationVO createNotification(NotificationCreateDTO createDTO) {
        NotificationChannelEnum channel = NotificationChannelEnum.from(createDTO.getChannel());

        if (channel == NotificationChannelEnum.PUSH) {
            Status result = pushoverService.sendMessage(
                    "",
                    "",
                    createDTO.getBody(),
                    null,
                    createDTO.getTitle(),
                    "https://github.com/ai-kids-care-team/ai-kids-care",
                    "ai-kids-care",
                    "alien"
            );
        }

        return mapper.toVO(repository.save(mapper.toEntity(createDTO)));
    }

    public NotificationVO updateNotification(Long id, NotificationUpdateDTO updateDTO) {
        Notification entity = repository.findById(id).
                orElseThrow(() -> new EntityNotFoundException("Notification not found"));
        mapper.updateEntity(updateDTO, entity);
        return mapper.toVO(repository.save(entity));
    }

    public void deleteNotification(Long id) {
        Notification entity = repository.findById(id).
                orElseThrow(() -> new EntityNotFoundException("Notification not found"));
        repository.delete(entity);
    }
}