package com.ai_kids_care.v1.entity;

import lombok.*;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import org.springframework.format.annotation.DateTimeFormat;
import io.swagger.v3.oas.annotations.media.Schema;


import jakarta.annotation.Generated;

/**
 * ChildGuardianRelationships
 */
@Generated(value = "org.openapitools.codegen.languages.SpringCodegen", comments = "Generator version: 7.20.0")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ChildGuardianRelationships {

  
  @NonNull
  @Schema(name = "kindergarten_id", requiredMode = Schema.RequiredMode.REQUIRED)
  @JsonProperty("kindergarten_id")
  private Long kindergartenId;

  
  @NonNull
  @Schema(name = "child_id", requiredMode = Schema.RequiredMode.REQUIRED)
  @JsonProperty("child_id")
  private Long childId;

  
  @NonNull
  @Schema(name = "guardian_id", requiredMode = Schema.RequiredMode.REQUIRED)
  @JsonProperty("guardian_id")
  private Long guardianId;

  
  @Schema(name = "relationship", description = "관계(부/모/조부모/후견인 등)", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("relationship")
  private String relationship;

  
  @Schema(name = "is_primary", description = "주 보호자 여부", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("is_primary")
  private Boolean isPrimary;

  
  @Schema(name = "priority", description = "우선순위(연락/권한 순서)", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("priority")
  private Integer priority;

  
  @Schema(name = "start_date", description = "관계 시작일", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("start_date")
  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
  private LocalDate startDate;

  
  @Schema(name = "end_date", description = "관계 종료일", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("end_date")
  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
  private LocalDate endDate;

  
  @Schema(name = "created_at", description = "생성 일시", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("created_at")
  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
  private OffsetDateTime createdAt;

  
  @Schema(name = "updated_at", description = "수정 일시", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("updated_at")
  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
  private OffsetDateTime updatedAt;

}

