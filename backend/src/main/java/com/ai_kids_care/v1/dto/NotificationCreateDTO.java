package com.ai_kids_care.v1.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.io.Serializable;
import java.time.OffsetDateTime;

/**
 * DTO for {@link com.ai_kids_care.v1.entity.Notification}
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class NotificationCreateDTO implements Serializable {
    private Long notificationId;
    @NotNull
    private Long kindergartenId;
    @NotNull
    private Long eventId;
    @NotNull
    private Long recipientUserId;
    @NotBlank
    private String channel;
    @NotBlank
    private String title;
    @NotBlank
    private String body;
    private String status;
    private String dedupeKey;
    private OffsetDateTime sentAt;
    private String failReason;
    @Min(0)
    private Integer retryCount;
    private OffsetDateTime createdAt;
}