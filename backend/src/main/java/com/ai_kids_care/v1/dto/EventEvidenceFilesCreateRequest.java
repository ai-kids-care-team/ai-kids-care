package com.ai_kids_care.v1.dto;

import lombok.*;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.OffsetDateTime;
import org.springframework.format.annotation.DateTimeFormat;
import io.swagger.v3.oas.annotations.media.Schema;


import jakarta.annotation.Generated;

/**
 * EventEvidenceFilesCreateRequest
 */
@Generated(value = "org.openapitools.codegen.languages.SpringCodegen", comments = "Generator version: 7.20.0")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class EventEvidenceFilesCreateRequest {

  
  @NonNull
  @Schema(name = "event_id", description = "이벤트 ID(DETECTION_EVENT.event_id)", requiredMode = Schema.RequiredMode.REQUIRED)
  @JsonProperty("event_id")
  private Long eventId;

  
  @NonNull
  @Schema(name = "kindergarten_id", description = "유치원 ID(KINDERGARTEN.kindergarten_id)", requiredMode = Schema.RequiredMode.REQUIRED)
  @JsonProperty("kindergarten_id")
  private Long kindergartenId;

  
  @Schema(name = "type", description = "증거 유형(IMAGE/VIDEO)", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("type")
  private String type;

  
  @Schema(name = "storage_uri", description = "저장 위치 URI(internal MinIO/NAS 등)", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("storage_uri")
  private String storageUri;

  
  @Schema(name = "mime_type", description = "MIME 타입(예: image/jpeg, video/mp4)", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("mime_type")
  private String mimeType;

  
  @Schema(name = "retention_until", description = "보관 만료 예정 일시", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("retention_until")
  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
  private OffsetDateTime retentionUntil;

  
  @Schema(name = "hold", description = "보관 홀드 여부(만료/삭제 방지 플래그)", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("hold")
  private Boolean hold;

  
  @Schema(name = "hash", description = "무결성 확인용 해시(파일 해시)", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("hash")
  private String hash;

}

