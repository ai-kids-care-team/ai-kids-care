package com.ai_kids_care.v1.dto;

import lombok.*;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;


import jakarta.annotation.Generated;

/**
 * At least one of loginId/email/phone must be provided (enforced by server).
 */
@Schema(name = "AuthRegisterRequest", description = "At least one of loginId/email/phone must be provided (enforced by server).")
@Generated(value = "org.openapitools.codegen.languages.SpringCodegen", comments = "Generator version: 7.20.0")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AuthRegisterRequest {

  
  @Schema(name = "loginId", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("loginId")
  private String loginId = null;

  
  @Schema(name = "email", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("email")
  private String email = null;

  
  @Schema(name = "phone", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("phone")
  private String phone = null;

  
  @ToString.Exclude
  @NonNull
  @Schema(name = "password", requiredMode = Schema.RequiredMode.REQUIRED)
  @JsonProperty("password")
  private String password;

  
  @Schema(name = "verification", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("verification")
  private AuthRegisterRequestVerification verification = null;

}

