package com.ai_kids_care.v1.dto;

import lombok.*;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;


import jakarta.annotation.Generated;

/**
 * AuthLoginRequest
 */
@Generated(value = "org.openapitools.codegen.languages.SpringCodegen", comments = "Generator version: 7.20.0")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AuthLoginDTO {

  @NotBlank(message = "로그인 ID를 입력해주세요.")
  @Schema(name = "identifier", description = "login_id or email or phone", requiredMode = Schema.RequiredMode.REQUIRED)
  @JsonProperty("identifier")
  private String identifier;


  @ToString.Exclude
  @NotBlank(message = "비밀번호를 입력해주세요.")
  @Schema(name = "password", requiredMode = Schema.RequiredMode.REQUIRED)
  @JsonProperty("password")
  private String password;

}
