package com.ai_kids_care.v1.dto;

import com.ai_kids_care.v1.security.validation.ValidPassword;
import lombok.*;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;


import jakarta.annotation.Generated;

/**
 * ResetPasswordRequest
 */
@Generated(value = "org.openapitools.codegen.languages.SpringCodegen", comments = "Generator version: 7.20.0")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ResetPasswordRequest {


  @ToString.Exclude
  @NotBlank(message = "새 비밀번호를 입력해주세요.")
  @ValidPassword
  @Schema(name = "newPassword", requiredMode = Schema.RequiredMode.REQUIRED)
  @JsonProperty("newPassword")
  private String newPassword;

}
