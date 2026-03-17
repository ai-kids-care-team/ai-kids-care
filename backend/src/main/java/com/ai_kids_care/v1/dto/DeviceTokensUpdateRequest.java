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
 * DeviceTokensUpdateRequest
 */
@Generated(value = "org.openapitools.codegen.languages.SpringCodegen", comments = "Generator version: 7.20.0")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class DeviceTokensUpdateRequest {

  
  @Schema(name = "user_id", description = "사용자 ID(USER_ACCOUNT.user_id)", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("user_id")
  private Long userId;

  
  @Schema(name = "platform", description = "플랫폼(IOS/ANDROID)", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("platform")
  private String platform;

  
  @Schema(name = "push_token", description = "푸시 토큰(APNs/FCM 등)", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("push_token")
  private String pushToken;

  /**
   * 토큰 상태
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

  
  @Schema(name = "status", description = "토큰 상태", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("status")
  private StatusEnum status;

  
  @Schema(name = "last_seen_at", description = "마지막 사용/갱신 일시", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("last_seen_at")
  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
  private OffsetDateTime lastSeenAt;

}

