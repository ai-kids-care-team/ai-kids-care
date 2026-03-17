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
 * DetectionSessionsUpdateRequest
 */
@Generated(value = "org.openapitools.codegen.languages.SpringCodegen", comments = "Generator version: 7.20.0")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class DetectionSessionsUpdateRequest {

  
  @Schema(name = "kindergarten_id", description = "유치원 ID(KINDERGARTEN.kindergarten_id)", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("kindergarten_id")
  private Long kindergartenId;

  
  @Schema(name = "camera_id", description = "카메라 ID(CCTV_CAMERA.camera_id)", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("camera_id")
  private Long cameraId;

  
  @Schema(name = "stream_id", description = "스트림 ID(CAMERA_STREAM.stream_id), 다중 스트림 중 선택", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("stream_id")
  private Long streamId;

  
  @Schema(name = "model_id", description = "모델 ID(AI_MODEL.model_id)", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("model_id")
  private Long modelId;

  
  @Schema(name = "started_at", description = "세션 시작 일시", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("started_at")
  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
  private OffsetDateTime startedAt;

  
  @Schema(name = "ended_at", description = "세션 종료 일시", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("ended_at")
  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
  private OffsetDateTime endedAt;

  /**
   * 세션 상태
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

  
  @Schema(name = "status", description = "세션 상태", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("status")
  private StatusEnum status;

  
  @Schema(name = "avg_latency_ms", description = "평균 지연 시간(ms)", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("avg_latency_ms")
  private Integer avgLatencyMs;

  
  @Schema(name = "inference_fps", description = "추론 처리 FPS", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("inference_fps")
  private Double inferenceFps;

}

