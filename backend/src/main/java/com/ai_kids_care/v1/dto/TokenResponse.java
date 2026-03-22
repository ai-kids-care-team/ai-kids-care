package com.ai_kids_care.v1.dto;

import com.ai_kids_care.v1.type.TokenTypeEnum;
import lombok.*;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;


import jakarta.annotation.Generated;

/**
 * TokenResponse
 */
@Generated(value = "org.openapitools.codegen.languages.SpringCodegen", comments = "Generator version: 7.20.0")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class TokenResponse {

  @NonNull
  @Schema(name = "accessToken", requiredMode = Schema.RequiredMode.REQUIRED)
  @JsonProperty("accessToken")
  private String accessToken;

  @NonNull
  @Schema(name = "tokenType", requiredMode = Schema.RequiredMode.REQUIRED)
  @JsonProperty("tokenType")
  private TokenTypeEnum tokenType;

  @NonNull
  @Schema(name = "expiresIn", description = "seconds", requiredMode = Schema.RequiredMode.REQUIRED)
  @JsonProperty("expiresIn")
  private Integer expiresIn;

  @NonNull
  @Schema(name = "refreshToken", requiredMode = Schema.RequiredMode.REQUIRED)
  @JsonProperty("refreshToken")
  private String refreshToken;

  @Schema(name = "refreshExpiresIn", description = "seconds", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("refreshExpiresIn")
  private Integer refreshExpiresIn;

  /** 로그인 계정의 로그인 ID (프론트 헤더·세션 표시용) */
  @Schema(name = "loginId", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("loginId")
  private String loginId;

  /** 표시용 이름 (양육자/교사·원장/행정청 프로필에서 조회, 없으면 loginId) */
  @Schema(name = "name", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("name")
  private String name;

  /** {@link com.ai_kids_care.v1.type.UserRoleEnum} 이름 문자열, 예: GUARDIAN, KINDERGARTEN_ADMIN */
  @Schema(name = "role", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("role")
  private String role;

}

