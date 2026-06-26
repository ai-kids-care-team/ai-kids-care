package com.ai_kids_care.v1.vo;

import java.io.Serializable;
import java.time.OffsetDateTime;

/**
 * VO for {@link com.ai_kids_care.v1.entity.PushSubscription}.
 * Note: {@code address} (provider delivery secret/identity) is intentionally NOT exposed.
 */
public record PushSubscriptionVO(
        Long pushSubscriptionId,
        Long userId,
        String provider,
        String deviceLabel,
        String status,
        OffsetDateTime lastVerifiedAt,
        OffsetDateTime createdAt
) implements Serializable {
}
