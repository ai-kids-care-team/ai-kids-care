package com.ai_kids_care.v1.dto;

import lombok.*;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import io.swagger.v3.oas.annotations.media.Schema;


import jakarta.annotation.Generated;

/**
 * ChildrenCreateRequest
 */
@Generated(value = "org.openapitools.codegen.languages.SpringCodegen", comments = "Generator version: 7.20.0")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ChildrenCreateRequest {

  
  @NonNull
  @Schema(name = "kindergarten_id", requiredMode = Schema.RequiredMode.REQUIRED)
  @JsonProperty("kindergarten_id")
  private Long kindergartenId;

  
  @Schema(name = "name", description = "원아 이름", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("name")
  private String name;

  
  @Schema(name = "child_no", description = "원아 번호(원내 관리번호)", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("child_no")
  private String childNo;

  
  @Schema(name = "rrn_encrypted", description = "주민등록번호 뒤자리 암호문(암호화 저장)", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("rrn_encrypted")
  private String rrnEncrypted;

  
  @Schema(name = "rrn_first6", description = "주민등록번호 앞 6자리(생년월일, 검색/중복확인용)", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("rrn_first6")
  private String rrnFirst6;

  
  @Schema(name = "birth_date", description = "생년월일", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("birth_date")
  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
  private LocalDate birthDate;

  
  @Schema(name = "gender", description = "성별", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("gender")
  private String gender;

  
  @Schema(name = "address", description = "주소", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("address")
  private String address;

  
  @Schema(name = "enroll_date", description = "입원/등록일", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("enroll_date")
  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
  private LocalDate enrollDate;

  
  @Schema(name = "leave_date", description = "퇴원/전원/졸업일", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("leave_date")
  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
  private LocalDate leaveDate;

  /**
   * 재원 상태
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

  
  @Schema(name = "status", description = "재원 상태", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("status")
  private StatusEnum status;

}

