package com.ai_kids_care.v1.entity;

import lombok.*;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.time.OffsetDateTime;
import org.springframework.format.annotation.DateTimeFormat;
import io.swagger.v3.oas.annotations.media.Schema;


import jakarta.annotation.Generated;

/**
 * ClassRoomAssignments
 */
@Generated(value = "org.openapitools.codegen.languages.SpringCodegen", comments = "Generator version: 7.20.0")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ClassRoomAssignments {

  
  @NonNull
  @Schema(name = "assignment_id", description = "반-공간 배정 ID", requiredMode = Schema.RequiredMode.REQUIRED)
  @JsonProperty("assignment_id")
  private Long assignmentId;

  
  @NonNull
  @Schema(name = "kindergarten_id", description = "유치원 ID(테넌트/필터링용)", requiredMode = Schema.RequiredMode.REQUIRED)
  @JsonProperty("kindergarten_id")
  private Long kindergartenId;

  
  @NonNull
  @Schema(name = "class_id", description = "반 ID", requiredMode = Schema.RequiredMode.REQUIRED)
  @JsonProperty("class_id")
  private Long classId;

  
  @NonNull
  @Schema(name = "room_id", description = "공간 ID", requiredMode = Schema.RequiredMode.REQUIRED)
  @JsonProperty("room_id")
  private Long roomId;

  
  @NonNull
  @Schema(name = "start_at", description = "사용 시작 일시", requiredMode = Schema.RequiredMode.REQUIRED)
  @JsonProperty("start_at")
  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
  private OffsetDateTime startAt;

  
  @Schema(name = "end_at", description = "사용 종료 일시, NULL=현재사용중", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("end_at")
  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
  private OffsetDateTime endAt;

  
  @Schema(name = "purpose", description = "사용 목적(수업/체육/행사/기타)", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("purpose")
  private String purpose;

  
  @Schema(name = "note", description = "비고/메모", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("note")
  private String note;

  /**
   * 배정 상태
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

  
  @Schema(name = "status", description = "배정 상태", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("status")
  private StatusEnum status;

  
  @Schema(name = "created_by_user_id", description = "생성자 사용자 ID", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("created_by_user_id")
  private Long createdByUserId;

  
  @Schema(name = "created_at", description = "생성 일시", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("created_at")
  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
  private OffsetDateTime createdAt;

  
  @Schema(name = "updated_at", description = "수정 일시", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("updated_at")
  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
  private OffsetDateTime updatedAt;

}

