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
 * DTO for {@link com.ai_kids_care.v1.entity.NotificationRule}
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class NotificationRuleCreateDTO implements Serializable {
    private Long ruleId;
    @NotNull
    private Long kindergartenId;
    @NotNull
    private Long userId;
    @NotBlank
    private String targetType;
    @NotNull
    private Long targetId;
    private String eventType;
    @NotNull
    @Min(0)
    private Integer minSeverity;
    private String quietHoursJson;
    @NotNull
    private Boolean enabled;
    private OffsetDateTime createdAt;
}