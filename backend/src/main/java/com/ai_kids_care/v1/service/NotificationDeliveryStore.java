package com.ai_kids_care.v1.service;

import com.ai_kids_care.v1.entity.Notification;
import com.ai_kids_care.v1.entity.NotificationDeliveryAttempt;
import com.ai_kids_care.v1.repository.NotificationDeliveryAttemptRepository;
import com.ai_kids_care.v1.repository.NotificationRepository;
import com.ai_kids_care.v1.type.DeliveryAttemptOutcome;
import com.ai_kids_care.v1.type.NotificationStatusEnum;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Optional;

/**
 * Transaction-boundary collaborator for NotificationService that keeps the database writes of the
 * delivery lifecycle in short REQUIRES_NEW transactions, each committed on its own connection, so the
 * external provider call in NotificationService.dispatch runs with NO database transaction open and NO
 * pooled connection checked out for its duration (PRF-02).
 *
 * Phase A (beginAttempt): persist SENDING + an IN_FLIGHT idempotency attempt and commit, BEFORE the
 * provider call. Phase B (markSucceeded / markFailed): write the terminal SENT / FAILED state + attempt
 * outcome, AFTER the provider call. reconcile handles a retry that observes a pre-existing attempt: a
 * previously SUCCEEDED delivery settles to SENT (already sent); any non-terminal / failed prior attempt
 * settles to FAILED WITHOUT re-sending (at-most-once on the retry path, design Decision 2).
 *
 * This is a separate bean (not a self-invoked method) so the REQUIRES_NEW proxy actually opens a fresh
 * transaction even when dispatch is called from a non-transactional caller.
 */
@Component
@RequiredArgsConstructor
public class NotificationDeliveryStore {

    private final NotificationRepository notificationRepository;
    private final NotificationDeliveryAttemptRepository attemptRepository;

    /**
     * Decision returned by beginAttempt. A null existingOutcome means "proceed with the provider call"
     * (first attempt); a non-null outcome means a prior attempt already exists, so the caller skips the
     * provider call and reconciles from that outcome instead.
     */
    public record DeliveryDecision(DeliveryAttemptOutcome existingOutcome) {
        static DeliveryDecision proceed() {
            return new DeliveryDecision(null);
        }

        static DeliveryDecision alreadyAttempted(DeliveryAttemptOutcome outcome) {
            return new DeliveryDecision(outcome);
        }

        public boolean alreadyAttempted() {
            return existingOutcome != null;
        }
    }

    /**
     * Phase A (short, REQUIRES_NEW): record an IN_FLIGHT attempt + set SENDING and commit, returning
     * proceed(). If an attempt already exists (or a concurrent insert wins the unique race), return
     * alreadyAttempted so the caller skips the provider call and reconciles instead (at-most-once guard).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public DeliveryDecision beginAttempt(Notification notification, String provider) {
        Optional<NotificationDeliveryAttempt> existing =
                attemptRepository.findByNotification_Id(notification.getId());
        if (existing.isPresent()) {
            return DeliveryDecision.alreadyAttempted(existing.get().getOutcome());
        }
        try {
            NotificationDeliveryAttempt attempt = NotificationDeliveryAttempt.builder()
                    .notification(notificationRepository.getReferenceById(notification.getId()))
                    .idempotencyKey(idempotencyKey(notification))
                    .channel(notification.getChannel())
                    .provider(provider)
                    .outcome(DeliveryAttemptOutcome.IN_FLIGHT)
                    .build();
            attemptRepository.saveAndFlush(attempt); // flush now to surface a unique-violation as a race
        } catch (DataIntegrityViolationException race) {
            DeliveryAttemptOutcome outcome = attemptRepository
                    .findByNotification_Id(notification.getId())
                    .map(NotificationDeliveryAttempt::getOutcome)
                    .orElse(DeliveryAttemptOutcome.IN_FLIGHT);
            return DeliveryDecision.alreadyAttempted(outcome);
        }
        notification.setStatus(NotificationStatusEnum.SENDING);
        notificationRepository.save(notification);
        return DeliveryDecision.proceed();
    }

    /**
     * Phase B (short, REQUIRES_NEW): the provider call returned success; record the attempt as SUCCEEDED
     * and the notification as SENT with a sent timestamp.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markSucceeded(Notification notification) {
        OffsetDateTime now = OffsetDateTime.now();
        attemptRepository.findByNotification_Id(notification.getId()).ifPresent(attempt -> {
            attempt.setOutcome(DeliveryAttemptOutcome.SUCCEEDED);
            attempt.setDetail(null);
            attemptRepository.save(attempt);
        });
        notification.setStatus(NotificationStatusEnum.SENT);
        notification.setSentAt(now);
        notificationRepository.save(notification);
    }

    /**
     * Phase B (short, REQUIRES_NEW): the delivery failed (provider error/timeout, or a pre-flight gate
     * such as a missing subscription/phone where no attempt was recorded). Record FAILED + reason,
     * increment retry_count, and mark the attempt FAILED if one exists.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(Notification notification, String reason) {
        attemptRepository.findByNotification_Id(notification.getId()).ifPresent(attempt -> {
            attempt.setOutcome(DeliveryAttemptOutcome.FAILED);
            attempt.setDetail(reason);
            attemptRepository.save(attempt);
        });
        notification.setStatus(NotificationStatusEnum.FAILED);
        notification.setFailReason(reason);
        notification.setRetryCount(
                (notification.getRetryCount() == null ? 0 : notification.getRetryCount()) + 1);
        notificationRepository.save(notification);
    }

    /**
     * Phase B for the retry path (short, REQUIRES_NEW): a prior attempt already exists, so the provider
     * was NOT called again. Settle the terminal state from that prior outcome: SUCCEEDED to SENT (already
     * delivered), otherwise FAILED without re-sending (at-most-once). An IN_FLIGHT prior attempt
     * (provider-success-then-crash) settles to FAILED: the deliberate trade-off of never double-notifying
     * a guardian (design Decision 2).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void reconcile(Notification notification, DeliveryAttemptOutcome priorOutcome) {
        if (priorOutcome == DeliveryAttemptOutcome.SUCCEEDED) {
            notification.setStatus(NotificationStatusEnum.SENT);
            if (notification.getSentAt() == null) {
                notification.setSentAt(OffsetDateTime.now());
            }
            notificationRepository.save(notification);
            return;
        }
        markFailed(notification,
                "delivery already attempted (at-most-once); not re-sending on retry");
    }

    private static String idempotencyKey(Notification notification) {
        return "notif-" + notification.getId();
    }
}
