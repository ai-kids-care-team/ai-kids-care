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
 * DetectionEventsCreateRequest
 */
@Generated(value = "org.openapitools.codegen.languages.SpringCodegen", comments = "Generator version: 7.20.0")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class DetectionEventsCreateRequest {

  
  @NonNull
  @Schema(name = "kindergarten_id", description = "유치원 ID(KINDERGARTEN.kindergarten_id)", requiredMode = Schema.RequiredMode.REQUIRED)
  @JsonProperty("kindergarten_id")
  private Long kindergartenId;

  
  @NonNull
  @Schema(name = "camera_id", description = "카메라 ID(CCTV_CAMERA.camera_id)", requiredMode = Schema.RequiredMode.REQUIRED)
  @JsonProperty("camera_id")
  private Long cameraId;

  
  @NonNull
  @Schema(name = "room_id", description = "룸/구역 ID(ROOM 테이블 FK 가정)", requiredMode = Schema.RequiredMode.REQUIRED)
  @JsonProperty("room_id")
  private Long roomId;

  
  @NonNull
  @Schema(name = "session_id", description = "탐지 세션 ID(DETECTION_SESSION.session_id)", requiredMode = Schema.RequiredMode.REQUIRED)
  @JsonProperty("session_id")
  private Long sessionId;

  
  @Schema(name = "event_type", description = "이벤트 유형(탐지 카테고리/코드)", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("event_type")
  private String eventType;

  
  @Schema(name = "severity", description = "심각도(정수 등급)", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("severity")
  private Integer severity;

  
  @Schema(name = "confidence", description = "신뢰도(0~1)", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("confidence")
  private Double confidence;

  
  @Schema(name = "detected_at", description = "탐지 발생 일시", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("detected_at")
  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
  private OffsetDateTime detectedAt;

  
  @Schema(name = "start_time", description = "이벤트 시작 시각", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("start_time")
  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
  private OffsetDateTime startTime;

  
  @Schema(name = "end_time", description = "이벤트 종료 시각", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("end_time")
  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
  private OffsetDateTime endTime;

  /**
   * 이벤트 상태(세분화)
   */
  public enum StatusEnum {
    OPEN("OPEN"),
    
    ACKNOWLEDGED("ACKNOWLEDGED"),
    
    IN_REVIEW("IN_REVIEW"),
    
    RESOLVED("RESOLVED"),
    
    DISMISSED("DISMISSED"),
    
    ESCALATED("ESCALATED");

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

  
  @Schema(name = "status", description = "이벤트 상태(세분화)", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("status")
  private StatusEnum status;

}

