package com.ai_kids_care.v1.vo;

import com.ai_kids_care.v1.type.TokenTypeEnum;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
public class TokenVO {

    @NonNull
    @Schema(name = "accessToken", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonProperty("accessToken")
    private String accessToken;

    @Enumerated(EnumType.STRING)
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

    @Schema(name = "id")
    @JsonProperty("id")
    private Long id;

    /** {@link com.ai_kids_care.v1.type.UserRoleEnum} 이름 (예: SUPERADMIN, GUARDIAN) */
    @Schema(name = "role")
    @JsonProperty("role")
    private String role;

    @Schema(name = "loginId")
    @JsonProperty("loginId")
    private String loginId;
}

