package com.ai_kids_care.v1.entity;

import lombok.*;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.OffsetDateTime;
import org.springframework.format.annotation.DateTimeFormat;
import io.swagger.v3.oas.annotations.media.Schema;


import jakarta.annotation.Generated;

/**
 * Superadmins
 */
@Generated(value = "org.openapitools.codegen.languages.SpringCodegen", comments = "Generator version: 7.20.0")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Superadmins {

  
  @NonNull
  @Schema(name = "superadmin_id", description = "슈퍼어드민 ID", requiredMode = Schema.RequiredMode.REQUIRED)
  @JsonProperty("superadmin_id")
  private Long superadminId;

  
  @NonNull
  @Schema(name = "user_id", description = "사용자 ID", requiredMode = Schema.RequiredMode.REQUIRED)
  @JsonProperty("user_id")
  private Long userId;

  
  @NonNull
  @Schema(name = "name", description = "담당자 이름", requiredMode = Schema.RequiredMode.REQUIRED)
  @JsonProperty("name")
  private String name;

  
  @Schema(name = "department", description = "행정청+부서 이름 e.g.서울시청 아동담당관", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("department")
  private String department;

  
  @Schema(name = "created_at", description = "생성 일시", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("created_at")
  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
  private OffsetDateTime createdAt;

  
  @Schema(name = "updated_at", description = "수정 일시", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("updated_at")
  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
  private OffsetDateTime updatedAt;

}

