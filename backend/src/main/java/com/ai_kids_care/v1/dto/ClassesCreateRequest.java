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
 * ClassesCreateRequest
 */
@Generated(value = "org.openapitools.codegen.languages.SpringCodegen", comments = "Generator version: 7.20.0")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ClassesCreateRequest {

  
  @NonNull
  @Schema(name = "kindergarten_id", requiredMode = Schema.RequiredMode.REQUIRED)
  @JsonProperty("kindergarten_id")
  private Long kindergartenId;

  
  @Schema(name = "name", description = "반/학급 이름", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("name")
  private String name;

  
  @Schema(name = "grade", description = "학년/연령대(예: 만3/만4/만5)", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("grade")
  private String grade;

  
  @Schema(name = "academic_year", description = "학년도(예: 2026)", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("academic_year")
  private Long academicYear;

  
  @Schema(name = "start_date", description = "운영 시작일", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("start_date")
  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
  private LocalDate startDate;

  
  @Schema(name = "end_date", description = "운영 종료일", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("end_date")
  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
  private LocalDate endDate;

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

