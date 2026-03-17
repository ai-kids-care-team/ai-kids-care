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
 * EventReviews
 */
@Generated(value = "org.openapitools.codegen.languages.SpringCodegen", comments = "Generator version: 7.20.0")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class EventReviews {

  
  @NonNull
  @Schema(name = "review_id", description = "이벤트 리뷰/처리 ID", requiredMode = Schema.RequiredMode.REQUIRED)
  @JsonProperty("review_id")
  private Long reviewId;

  
  @NonNull
  @Schema(name = "event_id", description = "이벤트 ID(DETECTION_EVENT.event_id)", requiredMode = Schema.RequiredMode.REQUIRED)
  @JsonProperty("event_id")
  private Long eventId;

  
  @NonNull
  @Schema(name = "kindergarten_id", description = "유치원 ID(KINDERGARTEN.kindergarten_id)", requiredMode = Schema.RequiredMode.REQUIRED)
  @JsonProperty("kindergarten_id")
  private Long kindergartenId;

  
  @NonNull
  @Schema(name = "user_id", description = "처리 사용자 ID(USER_ACCOUNT.user_id)", requiredMode = Schema.RequiredMode.REQUIRED)
  @JsonProperty("user_id")
  private Long userId;

  
  @Schema(name = "action", description = "처리 액션(ACK/RESOLVE/MARK_FALSE_POSITIVE/REOPEN)", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("action")
  private String action;

  /**
   * 처리 전 이벤트 상태
   */
  public enum FromStatusEnum {
    OPEN("OPEN"),
    
    ACKNOWLEDGED("ACKNOWLEDGED"),
    
    IN_REVIEW("IN_REVIEW"),
    
    RESOLVED("RESOLVED"),
    
    DISMISSED("DISMISSED"),
    
    ESCALATED("ESCALATED");

    private final String value;

    FromStatusEnum(String value) {
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
    public static FromStatusEnum fromValue(String value) {
      for (FromStatusEnum b : FromStatusEnum.values()) {
        if (b.value.equals(value)) {
          return b;
        }
      }
      throw new IllegalArgumentException("Unexpected value '" + value + "'");
    }
  }

  
  @Schema(name = "from_status", description = "처리 전 이벤트 상태", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("from_status")
  private FromStatusEnum fromStatus;

  /**
   * 처리 결과 상태(액션 후 이벤트 상태 등)
   */
  public enum ResultStatusEnum {
    OPEN("OPEN"),
    
    ACKNOWLEDGED("ACKNOWLEDGED"),
    
    IN_REVIEW("IN_REVIEW"),
    
    RESOLVED("RESOLVED"),
    
    DISMISSED("DISMISSED"),
    
    ESCALATED("ESCALATED");

    private final String value;

    ResultStatusEnum(String value) {
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
    public static ResultStatusEnum fromValue(String value) {
      for (ResultStatusEnum b : ResultStatusEnum.values()) {
        if (b.value.equals(value)) {
          return b;
        }
      }
      throw new IllegalArgumentException("Unexpected value '" + value + "'");
    }
  }

  
  @Schema(name = "result_status", description = "처리 결과 상태(액션 후 이벤트 상태 등)", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("result_status")
  private ResultStatusEnum resultStatus;

  
  @Schema(name = "comment", description = "코멘트/메모", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("comment")
  private String comment;

  
  @Schema(name = "created_at", description = "생성 일시", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("created_at")
  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
  private OffsetDateTime createdAt;

}

