package com.ai_kids_care.v1.entity;

import lombok.*;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.time.OffsetDateTime;
import org.springframework.format.annotation.DateTimeFormat;
import io.swagger.v3.oas.annotations.media.Schema;


import jakarta.annotation.Generated;

/**
 * Notifications
 */
@Generated(value = "org.openapitools.codegen.languages.SpringCodegen", comments = "Generator version: 7.20.0")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Notifications {

  
  @NonNull
  @Schema(name = "notification_id", description = "알림 ID", requiredMode = Schema.RequiredMode.REQUIRED)
  @JsonProperty("notification_id")
  private Long notificationId;

  
  @NonNull
  @Schema(name = "kindergarten_id", description = "유치원 ID(KINDERGARTEN.kindergarten_id)", requiredMode = Schema.RequiredMode.REQUIRED)
  @JsonProperty("kindergarten_id")
  private Long kindergartenId;

  
  @NonNull
  @Schema(name = "event_id", description = "관련 이벤트 ID(DETECTION_EVENT.event_id)", requiredMode = Schema.RequiredMode.REQUIRED)
  @JsonProperty("event_id")
  private Long eventId;

  
  @NonNull
  @Schema(name = "recipient_user_id", description = "수신자 사용자 ID(USER_ACCOUNT.user_id)", requiredMode = Schema.RequiredMode.REQUIRED)
  @JsonProperty("recipient_user_id")
  private Long recipientUserId;

  
  @Schema(name = "channel", description = "전송 채널(PUSH/SMS/EMAIL)", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("channel")
  private String channel;

  
  @Schema(name = "title", description = "알림 제목", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("title")
  private String title;

  
  @Schema(name = "body", description = "알림 본문", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("body")
  private String body;

  /**
   * 전송 상태(세분화)
   */
  public enum StatusEnum {
    QUEUED("QUEUED"),
    
    SENDING("SENDING"),
    
    SENT("SENT"),
    
    DELIVERED("DELIVERED"),
    
    READ("READ"),
    
    FAILED("FAILED"),
    
    CANCELED("CANCELED");

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

  
  @Schema(name = "status", description = "전송 상태(세분화)", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("status")
  private StatusEnum status;

  
  @Schema(name = "dedupe_key", description = "중복 방지 키(멱등성 키, 애플리케이션에서 생성)", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("dedupe_key")
  private String dedupeKey;

  
  @Schema(name = "sent_at", description = "전송 완료/시도 일시", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("sent_at")
  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
  private OffsetDateTime sentAt;

  
  @Schema(name = "fail_reason", description = "실패 사유", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("fail_reason")
  private String failReason;

  
  @Schema(name = "retry_count", description = "재시도 횟수", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("retry_count")
  private Integer retryCount;

  
  @Schema(name = "created_at", description = "생성 일시", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("created_at")
  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
  private OffsetDateTime createdAt;

}

