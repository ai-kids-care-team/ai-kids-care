package com.ai_kids_care.v1.dto;

import lombok.*;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.v3.oas.annotations.media.Schema;


import jakarta.annotation.Generated;

/**
 * V1AuthPasswordResetsPost200Response
 */
@JsonTypeName("_v1_auth_password_resets_post_200_response")
@Generated(value = "org.openapitools.codegen.languages.SpringCodegen", comments = "Generator version: 7.20.0")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class V1AuthPasswordResetsPost200Response {

  
  @NonNull
  @Schema(name = "requestId", requiredMode = Schema.RequiredMode.REQUIRED)
  @JsonProperty("requestId")
  private String requestId;

}

