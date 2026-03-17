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
 * UsersUpdateRequest
 */
@Generated(value = "org.openapitools.codegen.languages.SpringCodegen", comments = "Generator version: 7.20.0")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class UsersUpdateRequest {

  
  @Schema(name = "login_id", description = "로그인 ID(사용자 식별자)", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("login_id")
  private String loginId;

  
  @Schema(name = "email", description = "사용자 이메일", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("email")
  private String email;

  
  @Schema(name = "phone", description = "사용자 휴대폰 번호", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("phone")
  private String phone;

  
  @Schema(name = "password_hash", description = "비밀번호 해시", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("password_hash")
  private String passwordHash;

  /**
   * 계정 상태
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

  
  @Schema(name = "status", description = "계정 상태", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("status")
  private StatusEnum status;

  
  @Schema(name = "last_login_at", description = "마지막 로그인 일시", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("last_login_at")
  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
  private OffsetDateTime lastLoginAt;

  
  @Schema(name = "updated_at", description = "수정 일시", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("updated_at")
  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
  private OffsetDateTime updatedAt;

}

