package com.ai_kids_care.v1.dto;

import lombok.*;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;


import jakarta.annotation.Generated;

/**
 * AuthLogoutRequest
 */
@Generated(value = "org.openapitools.codegen.languages.SpringCodegen", comments = "Generator version: 7.20.0")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AuthLogoutRequest {

  
  @NonNull
  @Schema(name = "refreshToken", requiredMode = Schema.RequiredMode.REQUIRED)
  @JsonProperty("refreshToken")
  private String refreshToken;

  
  @Schema(name = "allSessions", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("allSessions")
  @Builder.Default
  private Boolean allSessions = false;

}

