package com.ai_kids_care.v1.dto;

import lombok.*;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.OffsetDateTime;
import org.springframework.format.annotation.DateTimeFormat;
import io.swagger.v3.oas.annotations.media.Schema;


import jakarta.annotation.Generated;

/**
 * VerifyVerificationCodeResponse
 */
@Generated(value = "org.openapitools.codegen.languages.SpringCodegen", comments = "Generator version: 7.20.0")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class VerifyVerificationCodeResponse {

  
  @NonNull
  @Schema(name = "verificationToken", description = "Short-lived token proving the challenge was verified", requiredMode = Schema.RequiredMode.REQUIRED)
  @JsonProperty("verificationToken")
  private String verificationToken;

  
  @NonNull
  @Schema(name = "expiresAt", description = "Token expiration time (UTC recommended)", requiredMode = Schema.RequiredMode.REQUIRED)
  @JsonProperty("expiresAt")
  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
  private OffsetDateTime expiresAt;

}

