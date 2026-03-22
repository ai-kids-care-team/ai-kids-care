package com.ai_kids_care.v1.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.OffsetDateTime;

/**
 * DTO for {@link com.ai_kids_care.v1.entity.Notification}
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class NotificationUpdateDTO implements Serializable {
    private Long notificationId;
    private Long kindergartenId;
    private Long eventId;
    private Long recipientUserId;
    private String channel;
    private String title;
    private String body;
    private String status;
    private String dedupeKey;
    private OffsetDateTime sentAt;
    private String failReason;
    private Integer retryCount;
    private OffsetDateTime createdAt;
}