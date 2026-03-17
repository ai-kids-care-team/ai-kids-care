package com.ai_kids_care.v1.entity;

import lombok.*;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.OffsetDateTime;
import org.springframework.format.annotation.DateTimeFormat;
import io.swagger.v3.oas.annotations.media.Schema;


import jakarta.annotation.Generated;

/**
 * AuditLogs
 */
@Generated(value = "org.openapitools.codegen.languages.SpringCodegen", comments = "Generator version: 7.20.0")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AuditLogs {

  
  @NonNull
  @Schema(name = "audit_id", description = "감사/추적 로그 ID", requiredMode = Schema.RequiredMode.REQUIRED)
  @JsonProperty("audit_id")
  private Long auditId;

  
  @NonNull
  @Schema(name = "kindergarten_id", description = "유치원 ID(KINDERGARTEN.kindergarten_id)", requiredMode = Schema.RequiredMode.REQUIRED)
  @JsonProperty("kindergarten_id")
  private Long kindergartenId;

  
  @NonNull
  @Schema(name = "user_id", description = "행위 사용자 ID(USER_ACCOUNT.user_id)", requiredMode = Schema.RequiredMode.REQUIRED)
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

  
  @Schema(name = "created_at", description = "생성 일시", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("created_at")
  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
  private OffsetDateTime createdAt;

}

