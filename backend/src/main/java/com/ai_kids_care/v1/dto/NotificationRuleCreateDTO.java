package com.ai_kids_care.v1.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.OffsetDateTime;

/**
 * DTO for {@link com.ai_kids_care.v1.entity.NotificationRule}
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class NotificationRuleCreateDTO implements Serializable {
    private Long ruleId;
    private Long kindergartenId;
    private Long userId;
    private String targetType;
    private Long targetId;
    private String eventType;
    private Integer minSeverity;
    private String quietHoursJson;
    private Boolean enabled;
    private OffsetDateTime createdAt;
}