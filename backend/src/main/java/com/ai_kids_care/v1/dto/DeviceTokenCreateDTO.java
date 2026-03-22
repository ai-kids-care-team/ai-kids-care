package com.ai_kids_care.v1.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.OffsetDateTime;

/**
 * DTO for {@link com.ai_kids_care.v1.entity.DeviceToken}
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class DeviceTokenCreateDTO implements Serializable {
    private Long deviceId;
    private Long userId;
    private String platform;
    private String pushToken;
    private String status;
    private OffsetDateTime lastSeenAt;
    private OffsetDateTime createdAt;
}