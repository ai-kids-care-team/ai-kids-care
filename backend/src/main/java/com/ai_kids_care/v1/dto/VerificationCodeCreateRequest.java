package com.ai_kids_care.v1.dto;

import lombok.*;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import io.swagger.v3.oas.annotations.media.Schema;


import jakarta.annotation.Generated;

/**
 * VerificationCodeCreateRequest
 */
@Generated(value = "org.openapitools.codegen.languages.SpringCodegen", comments = "Generator version: 7.20.0")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class VerificationCodeCreateRequest {

  /**
   * Gets or Sets channel
   */
  public enum ChannelEnum {
    EMAIL("EMAIL"),
    
    SMS("SMS");

    private final String value;

    ChannelEnum(String value) {
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
    public static ChannelEnum fromValue(String value) {
      for (ChannelEnum b : ChannelEnum.values()) {
        if (b.value.equals(value)) {
          return b;
        }
      }
      throw new IllegalArgumentException("Unexpected value '" + value + "'");
    }
  }

  
  @NonNull
  @Schema(name = "channel", requiredMode = Schema.RequiredMode.REQUIRED)
  @JsonProperty("channel")
  private ChannelEnum channel;

  
  @NonNull
  @Schema(name = "to", requiredMode = Schema.RequiredMode.REQUIRED)
  @JsonProperty("to")
  private String to;

  /**
   * Gets or Sets purpose
   */
  public enum PurposeEnum {
    SIGNUP("SIGNUP"),
    
    PASSWORD_RESET("PASSWORD_RESET"),
    
    VERIFY_EMAIL("VERIFY_EMAIL"),
    
    VERIFY_PHONE("VERIFY_PHONE"),
    
    CHANGE_EMAIL("CHANGE_EMAIL"),
    
    CHANGE_PHONE("CHANGE_PHONE"),
    
    MFA_LOGIN("MFA_LOGIN");

    private final String value;

    PurposeEnum(String value) {
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
    public static PurposeEnum fromValue(String value) {
      for (PurposeEnum b : PurposeEnum.values()) {
        if (b.value.equals(value)) {
          return b;
        }
      }
      throw new IllegalArgumentException("Unexpected value '" + value + "'");
    }
  }

  
  @NonNull
  @Schema(name = "purpose", requiredMode = Schema.RequiredMode.REQUIRED)
  @JsonProperty("purpose")
  private PurposeEnum purpose;

}

