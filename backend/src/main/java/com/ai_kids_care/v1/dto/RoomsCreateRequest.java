package com.ai_kids_care.v1.dto;

import lombok.*;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import io.swagger.v3.oas.annotations.media.Schema;


import jakarta.annotation.Generated;

/**
 * RoomsCreateRequest
 */
@Generated(value = "org.openapitools.codegen.languages.SpringCodegen", comments = "Generator version: 7.20.0")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class RoomsCreateRequest {

  
  @NonNull
  @Schema(name = "kindergarten_id", requiredMode = Schema.RequiredMode.REQUIRED)
  @JsonProperty("kindergarten_id")
  private Long kindergartenId;

  
  @Schema(name = "name", description = "공간/교실 이름", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("name")
  private String name;

  
  @Schema(name = "room_code", description = "공간 코드(내부 식별용)", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("room_code")
  private String roomCode;

  
  @Schema(name = "location_note", description = "위치 설명(층/동/출입구 등)", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("location_note")
  private String locationNote;

  
  @Schema(name = "room_type", description = "공간 유형(교실/복도/현관/놀이터 등)", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("room_type")
  private String roomType;

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

