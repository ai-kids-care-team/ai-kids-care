package com.ai_kids_care.v1.dto;

import lombok.*;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.time.OffsetDateTime;
import org.springframework.format.annotation.DateTimeFormat;
import io.swagger.v3.oas.annotations.media.Schema;


import jakarta.annotation.Generated;

/**
 * UserRoleAssignmentsUpdateRequest
 */
@Generated(value = "org.openapitools.codegen.languages.SpringCodegen", comments = "Generator version: 7.20.0")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class UserRoleAssignmentsUpdateRequest {

  
  @Schema(name = "user_id", description = "대상 사용자 ID(USER_ACCOUNT.user_id)", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("user_id")
  private Long userId;

  /**
   * 역할(GUARDIAN/TEACHER/KINDERGARTEN_ADMIN/PLATFORM_IT_ADMIN/SUPERADMIN)
   */
  public enum RoleEnum {
    GUARDIAN("GUARDIAN"),
    
    TEACHER("TEACHER"),
    
    KINDERGARTEN_ADMIN("KINDERGARTEN_ADMIN"),
    
    PLATFORM_IT_ADMIN("PLATFORM_IT_ADMIN"),
    
    SUPERADMIN("SUPERADMIN");

    private final String value;

    RoleEnum(String value) {
      this.value = value;
    }

    @JsonValue
    public String getValue() {
      return value;
    }

    @Override
    public String toString() {
      return String.valueOf(value);
    }

    @JsonCreator
    public static RoleEnum fromValue(String value) {
      for (RoleEnum b : RoleEnum.values()) {
        if (b.value.equals(value)) {
          return b;
        }
      }
      throw new IllegalArgumentException("Unexpected value '" + value + "'");
    }
  }

  
  @Schema(name = "role", description = "역할(GUARDIAN/TEACHER/KINDERGARTEN_ADMIN/PLATFORM_IT_ADMIN/SUPERADMIN)", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("role")
  private RoleEnum role;

  /**
   * 권한 범위 유형(PLATFORM/KINDERGARTEN)
   */
  public enum ScopeTypeEnum {
    PLATFORM("PLATFORM"),
    
    KINDERGARTEN("KINDERGARTEN");

    private final String value;

    ScopeTypeEnum(String value) {
      this.value = value;
    }

    @JsonValue
    public String getValue() {
      return value;
    }

    @Override
    public String toString() {
      return String.valueOf(value);
    }

    @JsonCreator
    public static ScopeTypeEnum fromValue(String value) {
      for (ScopeTypeEnum b : ScopeTypeEnum.values()) {
        if (b.value.equals(value)) {
          return b;
        }
      }
      throw new IllegalArgumentException("Unexpected value '" + value + "'");
    }
  }

  
  @Schema(name = "scope_type", description = "권한 범위 유형(PLATFORM/KINDERGARTEN)", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("scope_type")
  private ScopeTypeEnum scopeType;

  
  @Schema(name = "scope_id", description = "권한 범위 ID(NULL=PLATFORM; KINDERGARTEN일 때 kindergarten_id)", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("scope_id")
  private Long scopeId;

  /**
   * 권한 상태
   */
  public enum StatusEnum {
    ACTIVE("ACTIVE"),
    
    PENDING("PENDING"),
    
    DISABLED("DISABLED");

    private final String value;

    StatusEnum(String value) {
      this.value = value;
    }

    @JsonValue
    public String getValue() {
      return value;
    }

    @Override
    public String toString() {
      return String.valueOf(value);
    }

    @JsonCreator
    public static StatusEnum fromValue(String value) {
      for (StatusEnum b : StatusEnum.values()) {
        if (b.value.equals(value)) {
          return b;
        }
      }
      throw new IllegalArgumentException("Unexpected value '" + value + "'");
    }
  }

  
  @Schema(name = "status", description = "권한 상태", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("status")
  private StatusEnum status;

  
  @Schema(name = "granted_at", description = "권한 부여 일시", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("granted_at")
  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
  private OffsetDateTime grantedAt;

  
  @Schema(name = "granted_by_user_id", description = "권한 부여자 사용자 ID(USER_ACCOUNT.user_id)", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("granted_by_user_id")
  private Long grantedByUserId;

  
  @Schema(name = "revoked_at", description = "권한 회수 일시", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("revoked_at")
  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
  private OffsetDateTime revokedAt;

}

