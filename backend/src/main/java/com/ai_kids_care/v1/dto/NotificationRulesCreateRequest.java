package com.ai_kids_care.v1.dto;

import lombok.*;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import io.swagger.v3.oas.annotations.media.Schema;


import jakarta.annotation.Generated;

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

  /**
   * 규칙 적용 범위(ROOM/CAMERA/KINDERGARTEN)
   */
  public enum TargetTypeEnum {
    ROOM("ROOM"),
    
    CAMERA("CAMERA"),
    
    KINDERGARTEN("KINDERGARTEN");

    private final String value;

    TargetTypeEnum(String value) {
      this.value = value;
    }

    @JsonValue
    public String getValue() {
      return value;
    }

    @Override
    public String toString() {
      return String.valueOf(value);
    }

    @JsonCreator
    public static TargetTypeEnum fromValue(String value) {
      for (TargetTypeEnum b : TargetTypeEnum.values()) {
        if (b.value.equals(value)) {
          return b;
        }
      }
      throw new IllegalArgumentException("Unexpected value '" + value + "'");
    }
  }

  
  @Schema(name = "target_type", description = "규칙 적용 범위(ROOM/CAMERA/KINDERGARTEN)", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("target_type")
  private TargetTypeEnum targetType;

  
  @Schema(name = "target_id", description = "범위 ID(target_type에 따른 room_id/camera_id/kindergarten_id 등)", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("target_id")
  private Long targetId;

  
  @Schema(name = "event_type", description = "대상 이벤트 유형", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("event_type")
  private String eventType;

  
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

