package com.ai_kids_care.v1.dto;

import lombok.*;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.time.OffsetDateTime;
import org.springframework.format.annotation.DateTimeFormat;
import io.swagger.v3.oas.annotations.media.Schema;


import jakarta.annotation.Generated;

/**
 * CctvCamerasCreateRequest
 */
@Generated(value = "org.openapitools.codegen.languages.SpringCodegen", comments = "Generator version: 7.20.0")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CctvCamerasCreateRequest {

  
  @NonNull
  @Schema(name = "kindergarten_id", description = "유치원 ID(KINDERGARTEN.kindergarten_id)", requiredMode = Schema.RequiredMode.REQUIRED)
  @JsonProperty("kindergarten_id")
  private Long kindergartenId;

  
  @Schema(name = "serial_no", description = "카메라 시리얼 번호/제조사 식별자", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("serial_no")
  private String serialNo;

  
  @Schema(name = "camera_name", description = "카메라 이름/별칭 또는 표시 이름", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("camera_name")
  private String cameraName;

  
  @Schema(name = "model", description = "모델명", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("model")
  private String model;

  
  @Schema(name = "created_by_user_id", description = "등록한 사용자 ID", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("created_by_user_id")
  private Long createdByUserId;

  /**
   * 운영 상태
   */
  public enum StatusEnum {
    ACTIVE("ACTIVE"),
    
    PENDING("PENDING"),
    
    DISABLED("DISABLED");

    private final String value;

    StatusEnum(String value) {
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
    public static StatusEnum fromValue(String value) {
      for (StatusEnum b : StatusEnum.values()) {
        if (b.value.equals(value)) {
          return b;
        }
      }
      throw new IllegalArgumentException("Unexpected value '" + value + "'");
    }
  }

  
  @Schema(name = "status", description = "운영 상태", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("status")
  private StatusEnum status;

  
  @Schema(name = "last_seen_at", description = "마지막 접속/하트비트 일시", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("last_seen_at")
  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
  private OffsetDateTime lastSeenAt;

}

