package com.ai_kids_care.v1.dto;

import lombok.*;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;


import jakarta.annotation.Generated;

/**
 * AuthLoginRequest
 */
@Generated(value = "org.openapitools.codegen.languages.SpringCodegen", comments = "Generator version: 7.20.0")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AuthLoginRequest {

  
  @NonNull
  @Schema(name = "identifier", description = "login_id or email or phone", requiredMode = Schema.RequiredMode.REQUIRED)
  @JsonProperty("identifier")
  private String identifier;

  
  @ToString.Exclude
  @NonNull
  @Schema(name = "password", requiredMode = Schema.RequiredMode.REQUIRED)
  @JsonProperty("password")
  private String password;

}

