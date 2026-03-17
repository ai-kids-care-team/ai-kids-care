package com.ai_kids_care.v1.dto;

import lombok.*;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;


import jakarta.annotation.Generated;

/**
 * AuditLogsUpdateRequest
 */
@Generated(value = "org.openapitools.codegen.languages.SpringCodegen", comments = "Generator version: 7.20.0")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AuditLogsUpdateRequest {

  
  @Schema(name = "kindergarten_id", description = "유치원 ID(KINDERGARTEN.kindergarten_id)", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("kindergarten_id")
  private Long kindergartenId;

  
  @Schema(name = "user_id", description = "행위 사용자 ID(USER_ACCOUNT.user_id)", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("user_id")
  private Long userId;

  
  @Schema(name = "action", description = "행위(예: CREATE/UPDATE/DELETE/LOGIN 등)", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("action")
  private String action;

  
  @Schema(name = "resource_type", description = "대상 리소스 유형(테이블/도메인명 등)", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("resource_type")
  private String resourceType;

  
  @Schema(name = "resource_id", description = "대상 리소스 ID", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("resource_id")
  private Long resourceId;

  
  @Schema(name = "ip", description = "요청 IP", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("ip")
  private String ip;

  
  @Schema(name = "user_agent", description = "User-Agent", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("user_agent")
  private String userAgent;

}

