package com.ai_kids_care.v1.service;

import com.ai_kids_care.v1.entity.Notification;
import com.ai_kids_care.v1.repository.NotificationRepository;
import com.ai_kids_care.v1.type.NotificationStatusEnum;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Step ③b: periodically dispatches guardian notifications whose quiet-hours deferral has elapsed.
 * Scans `status = DEFERRED AND deferred_until <= now` and dispatches each (best-effort, per-row;
 * `dispatch` overwrites DEFERRED with SENDING→SENT/FAILED). Interval + initial delay are
 * configurable so the test profile can disable auto-scan and call {@link #scan()} directly.
 * Single-instance deployment assumed — ShedLock multi-instance dedup is a follow-up.
 */
@Component
@RequiredArgsConstructor
public class DeferredNotificationScanner {

    private static final Logger log = LoggerFactory.getLogger(DeferredNotificationScanner.class);

    private final NotificationRepository notificationRepository;
    private final NotificationService notificationService;

    @Scheduled(
            fixedDelayString = "${notifications.deferred-scan-interval-ms:60000}",
            initialDelayString = "${notifications.deferred-scan-initial-ms:60000}")
    public void scan() {
        List<Notification> due = notificationRepository
                .findByStatusAndDeferredUntilLessThanEqual(NotificationStatusEnum.DEFERRED, OffsetDateTime.now());
        if (due.isEmpty()) {
            return;
        }
        log.debug("Step ③b: dispatching {} due deferred notification(s)", due.size());
        for (Notification n : due) {
            try {
                notificationService.dispatch(n);
            } catch (RuntimeException e) {
                // best-effort: one row's dispatch failure must not abort the rest
            }
        }
    }
}
