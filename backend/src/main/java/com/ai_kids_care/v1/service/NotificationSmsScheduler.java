package com.ai_kids_care.v1.service;

import com.ai_kids_care.v1.config.NotificationSmsProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationSmsScheduler {
    private final NotificationSmsDispatchService notificationSmsDispatchService;
    private final NotificationSmsProperties smsProperties;

    @Scheduled(
            initialDelayString = "${notification.sms.initial-delay-ms:10000}",
            fixedDelayString = "${notification.sms.schedule-delay-ms:30000}"
    )
    public void dispatchPendingSmsNotifications() {
        if (!smsProperties.isSchedulerEnabled()) {
            return;
        }

        int processed = notificationSmsDispatchService.dispatchPendingSmsNotifications();
        if (processed > 0) {
            log.info("Notification SMS dispatcher processed {} item(s)", processed);
        }
    }
}
