package com.ai_kids_care.v1.dto;

import lombok.*;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import org.springframework.format.annotation.DateTimeFormat;
import io.swagger.v3.oas.annotations.media.Schema;


import jakarta.annotation.Generated;

/**
 * TeachersUpdateRequest
 */
@Generated(value = "org.openapitools.codegen.languages.SpringCodegen", comments = "Generator version: 7.20.0")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class TeachersUpdateRequest {

  
  @Schema(name = "kindergarten_id", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("kindergarten_id")
  private Long kindergartenId;

  
  @Schema(name = "user_id", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("user_id")
  private Long userId;

  
  @Schema(name = "staff_no", description = "직원 번호(원내 사번)", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("staff_no")
  private String staffNo;

  
  @Schema(name = "name", description = "교사 이름", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("name")
  private String name;

  
  @Schema(name = "gender", description = "성별", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("gender")
  private String gender;

  
  @Schema(name = "emergency_contact_name", description = "비상 연락처 이름", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("emergency_contact_name")
  private String emergencyContactName;

  
  @Schema(name = "emergency_contact_phone", description = "비상 연락처 전화번호", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("emergency_contact_phone")
  private String emergencyContactPhone;

  
  @Schema(name = "rrn_encrypted", description = "주민등록번호 암호문(암호화 저장)", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("rrn_encrypted")
  private String rrnEncrypted;

  
  @Schema(name = "rrn_first6", description = "주민등록번호 앞 6자리(생년월일, 검색/중복확인용)", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("rrn_first6")
  private String rrnFirst6;

  
  @Schema(name = "level", description = "직급(예: 원장/부원장/일반교사 등)", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("level")
  private String level;

  
  @Schema(name = "start_date", description = "근무 시작일", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("start_date")
  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
  private LocalDate startDate;

  
  @Schema(name = "end_date", description = "근무 종료일", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("end_date")
  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
  private LocalDate endDate;

  /**
   * 재직 상태
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

  
  @Schema(name = "status", description = "재직 상태", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("status")
  private StatusEnum status;

  
  @Schema(name = "updated_at", description = "수정 일시", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("updated_at")
  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
  private OffsetDateTime updatedAt;

}

