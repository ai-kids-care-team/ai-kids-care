package com.ai_kids_care.v1.vo;

import java.io.Serializable;
import java.time.OffsetDateTime;

/**
 * VO for {@link com.ai_kids_care.v1.entity.NotificationRule}
 */
public record NotificationRuleVO(
        Long ruleId,
        Long kindergartenId,
        Long userId,
        String targetType,
        Long targetId,
        String eventType,
        Integer minSeverity,
        String quietHoursJson,
        Boolean enabled,
        OffsetDateTime createdAt
) implements Serializable {
}