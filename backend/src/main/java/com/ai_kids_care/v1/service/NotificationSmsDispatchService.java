package com.ai_kids_care.v1.service;

import com.ai_kids_care.v1.config.NotificationSmsProperties;
import com.ai_kids_care.v1.entity.Notification;
import com.ai_kids_care.v1.repository.NotificationRepository;
import com.ai_kids_care.v1.service.sms.SmsFailureCategory;
import com.ai_kids_care.v1.service.sms.SmsSendResult;
import com.ai_kids_care.v1.service.sms.SmsSender;
import com.ai_kids_care.v1.type.NotificationChannelEnum;
import com.ai_kids_care.v1.type.NotificationStatusEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationSmsDispatchService {

    private final NotificationRepository notificationRepository;
    private final NotificationSmsProperties smsProperties;
    private final SmsSender smsSender;

    public int dispatchPendingSmsNotifications() {
        int batchSize = Math.max(1, smsProperties.getBatchSize());
        int maxRetries = Math.max(1, smsProperties.getMaxRetries());

        Page<Notification> page = notificationRepository.findByChannelAndStatusInAndRetryCountLessThanOrderByCreatedAtAsc(
                NotificationChannelEnum.SMS,
                List.of(NotificationStatusEnum.QUEUED, NotificationStatusEnum.FAILED),
                maxRetries,
                PageRequest.of(0, batchSize)
        );

        int processed = 0;
        for (Notification notification : page.getContent()) {
            processSingle(notification);
            processed++;
        }
        return processed;
    }

    private void processSingle(Notification notification) {
        notification.setStatus(NotificationStatusEnum.SENDING);
        notification.setSentAt(OffsetDateTime.now());
        notificationRepository.save(notification);

        SmsSendResult result = smsProperties.isDryRun()
                ? SmsSendResult.success("DRY_RUN", "dry-run mode, provider call skipped", "DRY-" + notification.getId())
                : sendByNotification(notification);

        if (result.success()) {
            notification.setStatus(NotificationStatusEnum.SENT);
            notification.setFailReason("");
            notification.setSentAt(OffsetDateTime.now());
            notificationRepository.save(notification);
            return;
        }

        int currentRetry = notification.getRetryCount() == null ? 0 : notification.getRetryCount();
        int nextRetry = currentRetry + 1;
        int maxRetries = Math.max(1, smsProperties.getMaxRetries());
        if (!result.retryable()) {
            nextRetry = Math.max(nextRetry, maxRetries);
        }

        notification.setRetryCount(nextRetry);
        notification.setStatus(NotificationStatusEnum.FAILED);
        notification.setFailReason(buildFailReason(result));
        notification.setSentAt(OffsetDateTime.now());
        notificationRepository.save(notification);

        log.warn(
                "Notification SMS send failed. notificationId={}, retryCount={}, internalCode={}, providerCode={}, providerMessage={}",
                notification.getId(),
                nextRetry,
                result.internalCode(),
                safe(result.providerCode()),
                safe(result.providerMessage())
        );
    }

    private SmsSendResult sendByNotification(Notification notification) {
        if (notification.getRecipientUser() == null || isBlank(notification.getRecipientUser().getPhone())) {
            return SmsSendResult.failure(
                    "LOCAL_NO_RECIPIENT_PHONE",
                    "recipient user has no phone number",
                    null,
                    SmsFailureCategory.INVALID_RECIPIENT
            );
        }
        return smsSender.sendSms(notification.getRecipientUser().getPhone(), notification.getBody(), notification.getTitle());
    }

    private String buildFailReason(SmsSendResult result) {
        String providerCode = safe(result.providerCode());
        String providerMessage = safe(result.providerMessage());
        String providerMessageId = safe(result.providerMessageId());
        return "internalCode=" + result.internalCode()
                + "; providerCode=" + providerCode
                + "; providerMessageId=" + providerMessageId
                + "; providerMessage=" + providerMessage;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
