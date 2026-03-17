package com.ai_kids_care.v1.dto;

import lombok.*;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import org.springframework.format.annotation.DateTimeFormat;
import io.swagger.v3.oas.annotations.media.Schema;


import jakarta.annotation.Generated;

/**
 * ChildClassAssignmentsUpdateRequest
 */
@Generated(value = "org.openapitools.codegen.languages.SpringCodegen", comments = "Generator version: 7.20.0")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ChildClassAssignmentsUpdateRequest {

  
  @Schema(name = "kindergarten_id", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("kindergarten_id")
  private Long kindergartenId;

  
  @Schema(name = "child_id", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("child_id")
  private Long childId;

  
  @Schema(name = "class_id", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("class_id")
  private Long classId;

  
  @Schema(name = "start_date", description = "배정 시작일", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("start_date")
  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
  private LocalDate startDate;

  
  @Schema(name = "end_date", description = "배정 종료일", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("end_date")
  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
  private LocalDate endDate;

  
  @Schema(name = "reason", description = "변경 사유(전반/이동/신설반 등)", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("reason")
  private String reason;

  
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

  
  @Schema(name = "updated_at", description = "수정 일시", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("updated_at")
  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
  private OffsetDateTime updatedAt;

}

