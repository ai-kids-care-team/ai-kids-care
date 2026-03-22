package com.ai_kids_care.v1.vo;

import java.io.Serializable;
import java.time.OffsetDateTime;

/**
 * VO for {@link com.ai_kids_care.v1.entity.DeviceToken}
 */
public record DeviceTokenVO(
        Long deviceId,
        Long userId,
        String platform,
        String pushToken,
        String status,
        OffsetDateTime lastSeenAt,
        OffsetDateTime createdAt
) implements Serializable {
}