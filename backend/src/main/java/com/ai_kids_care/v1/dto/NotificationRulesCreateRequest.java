package com.ai_kids_care.v1.dto;

import com.ai_kids_care.v1.type.EventTypeEnum;
import com.ai_kids_care.v1.type.NotificationTargetType;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Generated;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;

/**
 * NotificationRulesCreateRequest
 */
@Generated(value = "org.openapitools.codegen.languages.SpringCodegen", comments = "Generator version: 7.20.0")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class NotificationRulesCreateRequest {

    @NonNull
    @Schema(name = "kindergarten_id", description = "유치원 ID(KINDERGARTEN.kindergarten_id)", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonProperty("kindergarten_id")
    private Long kindergartenId;

    @NonNull
    @Schema(name = "user_id", description = "규칙 소유/대상 사용자 ID(USER_ACCOUNT.user_id)", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonProperty("user_id")
    private Long userId;

    @Schema(name = "target_type", description = "규칙 적용 범위(notification_target_type)", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    @JsonProperty("target_type")
    private NotificationTargetType targetType;

    @Schema(name = "target_id", description = "범위 ID(target_type에 따른 room_id/camera_id/kindergarten_id 등)", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    @JsonProperty("target_id")
    private Long targetId;

    @Schema(name = "event_type", description = "대상 이벤트 유형(event_type_enum, DB 저장 시 varchar 로 직렬화)", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    @JsonProperty("event_type")
    private EventTypeEnum eventType;

    @Schema(name = "min_severity", description = "최소 심각도(이 값 이상만 알림)", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    @JsonProperty("min_severity")
    private Integer minSeverity;

    @Schema(name = "quiet_hours_json", description = "방해금지 시간 설정(JSON 문자열)", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    @JsonProperty("quiet_hours_json")
    private String quietHoursJson;

    @Schema(name = "enabled", description = "규칙 활성화 여부", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    @JsonProperty("enabled")
    private Boolean enabled;
}
