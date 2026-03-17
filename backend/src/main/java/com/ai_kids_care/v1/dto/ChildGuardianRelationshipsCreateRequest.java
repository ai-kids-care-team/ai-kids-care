package com.ai_kids_care.v1.dto;

import lombok.*;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import io.swagger.v3.oas.annotations.media.Schema;


import jakarta.annotation.Generated;

/**
 * ChildGuardianRelationshipsCreateRequest
 */
@Generated(value = "org.openapitools.codegen.languages.SpringCodegen", comments = "Generator version: 7.20.0")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ChildGuardianRelationshipsCreateRequest {

  
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

}

