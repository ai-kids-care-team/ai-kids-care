package com.ai_kids_care.v1.service;

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
import com.ai_kids_care.v1.type.PushProviderEnum;
import com.ai_kids_care.v1.type.StatusEnum;
import com.ai_kids_care.v1.type.UserRoleAssignmentScopeType;
import com.ai_kids_care.v1.type.UserRoleEnum;
import com.ai_kids_care.v1.vo.NotificationReadVO;
import com.ai_kids_care.v1.vo.NotificationVO;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository repository;
    private final NotificationMapper mapper;
    private final PushoverService pushoverService;
    private final SmsPort smsPort;
    private final SecurityAuditWriter auditWriter;
    private final PushSubscriptionRepository pushSubscriptionRepository;
    private final NotificationDeliveryStore deliveryStore;

    // PRF-01 SMS dispatch-layer timeout budget (ms). The Solapi SDK 4.3.0 exposes no OkHttp timeout
    // hook (see SolapiClientConfig), so the SMS provider call is bounded here. A non-positive value
    // disables the wrapper (call the port directly). Default 5000 ms.
    @Value("${notifications.sms-send-timeout-ms:5000}")
    private long smsSendTimeoutMs = 5000L;

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

    // Deliver a persisted notification over its channel and record the lifecycle (SENDING -> SENT/FAILED).
    // PRF-02 delivery atomicity: this method is deliberately NOT @Transactional. The DB writes are
    // isolated into short REQUIRES_NEW transactions owned by NotificationDeliveryStore: an IN_FLIGHT
    // attempt + SENDING are committed BEFORE the provider call, and the terminal SENT/FAILED is committed
    // AFTER it, so the provider network round-trip runs with NO transaction open and NO pooled connection
    // held. The committed-before attempt is the at-most-once guard: a retry of a notification that already
    // has an attempt row reconciles instead of re-sending (design Decision 2). Public signature unchanged.
    public void dispatch(Notification notification) {
        NotificationChannelEnum channel = notification.getChannel();
        if (channel == NotificationChannelEnum.SMS) {
            dispatchSms(notification);
            return;
        }
        if (channel != NotificationChannelEnum.PUSH) {
            return; // EMAIL delivery not implemented
        }
        dispatchPush(notification);
    }

    private void dispatchPush(Notification notification) {
        Optional<PushSubscription> subscription = pushSubscriptionRepository
                .findByUser_IdAndProviderAndStatus(
                        notification.getRecipientUser().getId(),
                        PushProviderEnum.PUSHOVER,
                        StatusEnum.ACTIVE)
                .stream().findFirst();

        if (subscription.isEmpty()) {
            // No delivery identity -> terminal FAILED. No attempt is recorded (provider never called),
            // so a later retry simply re-evaluates the gate; there is no double-send risk.
            deliveryStore.markFailed(notification, "no active Pushover subscription for recipient");
            return;
        }

        NotificationDeliveryStore.DeliveryDecision decision =
                deliveryStore.beginAttempt(notification, "PUSHOVER");
        if (decision.alreadyAttempted()) {
            deliveryStore.reconcile(notification, decision.existingOutcome());
            return;
        }

        // Provider call: NO DB transaction open, NO pooled connection held (PRF-02). Pushover already
        // enforces a hard per-call timeout at the client level (PushoverClientConfig), so no callsite
        // timeout guard is needed here (unlike SMS / PRF-01).
        try {
            pushoverService.sendToUser(
                    subscription.get().getAddress(),
                    notification.getTitle(),
                    notification.getBody());
            deliveryStore.markSucceeded(notification);
        } catch (IllegalStateException e) {
            deliveryStore.markFailed(notification, "Pushover delivery failed: " + e.getMessage());
        }
    }

    // Deliver an SMS via SmsPort to users.phone. Missing/blank phone -> FAILED without calling the port.
    // Otherwise SENDING -> SENT on success, or FAILED on a delivery failure or timeout (PRF-01).
    private void dispatchSms(Notification notification) {
        String phone = notification.getRecipientUser().getPhone();
        if (phone == null || phone.isBlank()) {
            deliveryStore.markFailed(notification, "no phone for SMS recipient");
            return;
        }

        NotificationDeliveryStore.DeliveryDecision decision =
                deliveryStore.beginAttempt(notification, "SOLAPI");
        if (decision.alreadyAttempted()) {
            deliveryStore.reconcile(notification, decision.existingOutcome());
            return;
        }

        // Provider call outside any DB transaction. PRF-01: the Solapi SDK 4.3.0 exposes no OkHttp
        // timeout hook, so bound the call here with a configurable wall-clock budget; a timeout is
        // translated to a delivery failure (FAILED), never a stuck SENDING.
        try {
            sendSmsWithinBudget(phone, notification.getBody());
            deliveryStore.markSucceeded(notification);
        } catch (IllegalStateException e) {
            deliveryStore.markFailed(notification, "SMS delivery failed: " + e.getMessage());
        }
    }

    // PRF-01: invoke SmsPort.send under a bounded wall-clock budget. A delivery failure thrown by the
    // adapter (IllegalStateException) propagates unchanged; a timeout is translated to an
    // IllegalStateException so dispatchSms records it as FAILED. A non-positive budget disables the
    // wrapper (direct call).
    private void sendSmsWithinBudget(String phone, String body) {
        if (smsSendTimeoutMs <= 0) {
            smsPort.send(phone, body);
            return;
        }
        try {
            CompletableFuture
                    .runAsync(() -> smsPort.send(phone, body))
                    .orTimeout(smsSendTimeoutMs, TimeUnit.MILLISECONDS)
                    .join();
        } catch (CompletionException | CancellationException e) {
            Throwable cause = (e instanceof CompletionException) ? e.getCause() : e;
            if (cause instanceof IllegalStateException ise) {
                throw ise; // provider delivery failure from the adapter
            }
            if (cause instanceof TimeoutException) {
                throw new IllegalStateException(
                        "Solapi SMS call timed out after " + smsSendTimeoutMs + "ms", cause);
            }
            if (cause instanceof RuntimeException re) {
                throw re; // e.g. IllegalArgumentException (programming error) propagates unchanged
            }
            throw new IllegalStateException("Solapi SMS call failed", cause);
        }
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
