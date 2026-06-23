package com.ai_kids_care.v1.service;

import com.ai_kids_care.v1.dto.NotificationCreateDTO;
import com.ai_kids_care.v1.dto.NotificationUpdateDTO;
import com.ai_kids_care.v1.entity.Notification;
import com.ai_kids_care.v1.mapper.NotificationMapper;
import com.ai_kids_care.v1.entity.PushSubscription;
import com.ai_kids_care.v1.repository.NotificationRepository;
import com.ai_kids_care.v1.repository.PushSubscriptionRepository;
import com.ai_kids_care.v1.security.EffectiveAuthorizationContext;
import com.ai_kids_care.v1.security.EffectiveAuthorizationContextHolder;
import com.ai_kids_care.v1.security.audit.AuditAction;
import com.ai_kids_care.v1.security.audit.AuditEvent;
import com.ai_kids_care.v1.security.audit.AuditResult;
import com.ai_kids_care.v1.security.audit.SecurityAuditWriter;
import com.ai_kids_care.v1.type.NotificationChannelEnum;
import com.ai_kids_care.v1.type.NotificationStatusEnum;
import com.ai_kids_care.v1.type.PushProviderEnum;
import com.ai_kids_care.v1.type.StatusEnum;
import com.ai_kids_care.v1.type.UserRoleAssignmentScopeType;
import com.ai_kids_care.v1.type.UserRoleEnum;
import com.ai_kids_care.v1.vo.NotificationReadVO;
import com.ai_kids_care.v1.vo.NotificationVO;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository repository;
    private final NotificationMapper mapper;
    private final PushoverService pushoverService;
    private final SmsPort smsPort;
    private final SecurityAuditWriter auditWriter;
    private final PushSubscriptionRepository pushSubscriptionRepository;

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

    /**
     * Persist a notification (unpublished write path). Delivery is performed separately via
     * {@link #dispatch(Notification)} — the previous inline hard-coded blank-credential
     * Pushover call has been removed (it would always throw at runtime).
     */
    public NotificationVO createNotification(NotificationCreateDTO createDTO) {
        return mapper.toVO(repository.save(mapper.toEntity(createDTO)));
    }

    /**
     * Deliver a persisted notification over its channel and record the delivery lifecycle
     * (SENDING → SENT / FAILED, with sent_at / fail_reason / retry_count). PUSH (Pushover)
     * and SMS (Solapi via {@link SmsPort}, sending to {@code users.phone}) have implemented
     * delivery paths; EMAIL is not yet wired (see notifications spec). The trigger that decides
     * WHEN to dispatch (rule engine) is out of scope — this is the delivery primitive it will call.
     */
    @Transactional
    public void dispatch(Notification notification) {
        if (notification.getChannel() == NotificationChannelEnum.SMS) {
            dispatchSms(notification);
            return;
        }
        if (notification.getChannel() != NotificationChannelEnum.PUSH) {
            return; // EMAIL delivery not implemented
        }

        notification.setStatus(NotificationStatusEnum.SENDING);
        repository.save(notification);

        Optional<PushSubscription> subscription = pushSubscriptionRepository
                .findByUser_IdAndProviderAndStatus(
                        notification.getRecipientUser().getId(),
                        PushProviderEnum.PUSHOVER,
                        StatusEnum.ACTIVE)
                .stream().findFirst();

        if (subscription.isEmpty()) {
            markFailed(notification, "no active Pushover subscription for recipient");
            return;
        }

        // Delivery-atomicity gap (acceptable for this v1 primitive, no live trigger yet):
        // the external Pushover call sits inside the @Transactional boundary. If the push
        // succeeds but the subsequent SENT save fails, the transaction rolls back and the
        // row is left at SENDING (the push already went out). A trigger added later (rule
        // engine) should add an idempotency key / two-phase mark before relying on this at
        // volume. We catch only IllegalStateException (the translated PushoverException) so
        // that programming errors (IllegalArgumentException) and DataAccessException
        // propagate and roll back rather than being recorded as a delivery FAILED.
        try {
            pushoverService.sendToUser(
                    subscription.get().getAddress(),
                    notification.getTitle(),
                    notification.getBody());
            notification.setStatus(NotificationStatusEnum.SENT);
            notification.setSentAt(OffsetDateTime.now());
            repository.save(notification);
        } catch (IllegalStateException e) {
            markFailed(notification, "Pushover delivery failed: " + e.getMessage());
        }
    }

    /**
     * Deliver an SMS notification via {@link SmsPort} to the recipient's {@code users.phone}.
     * A missing/blank phone is recorded FAILED without calling the port (no SMS to an empty number);
     * otherwise the row goes SENDING → SENT/sent_at on success, or FAILED on a delivery failure
     * (the translated {@link IllegalStateException} from the adapter). Same lifecycle as the PUSH
     * path — SMS has no title concept, so only the body is sent. Same delivery-atomicity caveat as
     * PUSH applies (external call inside the @Transactional boundary).
     */
    private void dispatchSms(Notification notification) {
        String phone = notification.getRecipientUser().getPhone();
        if (phone == null || phone.isBlank()) {
            markFailed(notification, "no phone for SMS recipient");
            return;
        }

        notification.setStatus(NotificationStatusEnum.SENDING);
        repository.save(notification);

        try {
            smsPort.send(phone, notification.getBody());
            notification.setStatus(NotificationStatusEnum.SENT);
            notification.setSentAt(OffsetDateTime.now());
            repository.save(notification);
        } catch (IllegalStateException e) {
            markFailed(notification, "SMS delivery failed: " + e.getMessage());
        }
    }

    private void markFailed(Notification notification, String reason) {
        notification.setStatus(NotificationStatusEnum.FAILED);
        notification.setFailReason(reason);
        notification.setRetryCount(
                (notification.getRetryCount() == null ? 0 : notification.getRetryCount()) + 1);
        repository.save(notification);
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