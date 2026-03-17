package com.ai_kids_care.v1.dto;

import lombok.*;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;


import jakarta.annotation.Generated;

/**
 * SuperadminsCreateRequest
 */
@Generated(value = "org.openapitools.codegen.languages.SpringCodegen", comments = "Generator version: 7.20.0")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class SuperadminsCreateRequest {

  
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

}

