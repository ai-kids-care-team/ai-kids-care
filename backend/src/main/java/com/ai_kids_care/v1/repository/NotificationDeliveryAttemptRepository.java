package com.ai_kids_care.v1.repository;

import com.ai_kids_care.v1.entity.NotificationDeliveryAttempt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Storage for per-notification external-delivery attempts (PRF-02 idempotency / at-most-once).
 * Lookup is by notification id — exactly one attempt row exists per notification.
 */
public interface NotificationDeliveryAttemptRepository
        extends JpaRepository<NotificationDeliveryAttempt, Long> {

    Optional<NotificationDeliveryAttempt> findByNotification_Id(Long notificationId);
}
