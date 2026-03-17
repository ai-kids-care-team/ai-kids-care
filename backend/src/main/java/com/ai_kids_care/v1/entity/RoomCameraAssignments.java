package com.ai_kids_care.v1.entity;

import lombok.*;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.OffsetDateTime;
import org.springframework.format.annotation.DateTimeFormat;
import io.swagger.v3.oas.annotations.media.Schema;


import jakarta.annotation.Generated;

/**
 * RoomCameraAssignments
 */
@Generated(value = "org.openapitools.codegen.languages.SpringCodegen", comments = "Generator version: 7.20.0")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class RoomCameraAssignments {

  
  @NonNull
  @Schema(name = "assignment_id", requiredMode = Schema.RequiredMode.REQUIRED)
  @JsonProperty("assignment_id")
  private Long assignmentId;

  
  @NonNull
  @Schema(name = "kindergarten_id", requiredMode = Schema.RequiredMode.REQUIRED)
  @JsonProperty("kindergarten_id")
  private Long kindergartenId;

  
  @NonNull
  @Schema(name = "camera_id", requiredMode = Schema.RequiredMode.REQUIRED)
  @JsonProperty("camera_id")
  private Long cameraId;

  
  @NonNull
  @Schema(name = "room_id", requiredMode = Schema.RequiredMode.REQUIRED)
  @JsonProperty("room_id")
  private Long roomId;

  
  @NonNull
  @Schema(name = "start_at", description = "설치/배치 시작 일시", requiredMode = Schema.RequiredMode.REQUIRED)
  @JsonProperty("start_at")
  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
  private OffsetDateTime startAt;

  
  @Schema(name = "end_at", description = "설치/배치 종료 일시(철거/이동 시, NULL=현재 유효)", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("end_at")
  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
  private OffsetDateTime endAt;

  
  @Schema(name = "created_at", description = "생성 일시", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("created_at")
  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
  private OffsetDateTime createdAt;

  
  @Schema(name = "updated_at", description = "수정 일시", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("updated_at")
  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
  private OffsetDateTime updatedAt;

}

