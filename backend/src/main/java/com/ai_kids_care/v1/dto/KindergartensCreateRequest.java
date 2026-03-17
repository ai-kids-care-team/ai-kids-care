package com.ai_kids_care.v1.dto;

import lombok.*;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import io.swagger.v3.oas.annotations.media.Schema;


import jakarta.annotation.Generated;

/**
 * KindergartensCreateRequest
 */
@Generated(value = "org.openapitools.codegen.languages.SpringCodegen", comments = "Generator version: 7.20.0")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class KindergartensCreateRequest {

  
  @Schema(name = "name", description = "유치원명", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("name")
  private String name;

  
  @Schema(name = "address", description = "주소", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("address")
  private String address;

  
  @Schema(name = "region_code", description = "지역 코드(행정구역/내부 코드)", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("region_code")
  private String regionCode;

  
  @Schema(name = "code", description = "유치원 코드(내부/기관 식별용)", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("code")
  private String code;

  
  @Schema(name = "business_registration_no", description = "사업자관리번호(유치원의 고유 사업자 등록 번호)", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("business_registration_no")
  private String businessRegistrationNo;

  
  @Schema(name = "contact_name", description = "담당자 이름", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("contact_name")
  private String contactName;

  
  @Schema(name = "contact_phone", description = "담당자 전화번호", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("contact_phone")
  private String contactPhone;

  
  @Schema(name = "contact_email", description = "담당자 이메일", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("contact_email")
  private String contactEmail;

  /**
   * 운영 상태
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

  
  @Schema(name = "status", description = "운영 상태", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("status")
  private StatusEnum status;

}

