package com.ai_kids_care.v1.vo;

import java.io.Serializable;
import java.time.OffsetDateTime;

/**
 * VO for {@link com.ai_kids_care.v1.entity.Notification}
 */
public record NotificationVO(
        Long notificationId,
        Long kindergartenId,
        Long eventId,
        Long recipientUserId,
        String channel,
        String title,
        String body,
        String status,
        String dedupeKey,
        OffsetDateTime sentAt,
        String failReason,
        Integer retryCount,
        OffsetDateTime createdAt
) implements Serializable {
}