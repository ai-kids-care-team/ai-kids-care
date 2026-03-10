package com.dashboard.entity;

import java.net.URI;
import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonValue;
import java.time.OffsetDateTime;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import io.swagger.v3.oas.annotations.media.Schema;


import java.util.*;
import jakarta.annotation.Generated;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Unified status enum from DBML.
 */

@Generated(value = "org.openapitools.codegen.languages.SpringCodegen", date = "2026-03-10T11:14:59.262725025Z[Etc/UTC]", comments = "Generator version: 7.6.0")
public enum StatusEnum {
  
  ACTIVE("ACTIVE"),
  
  PENDING("PENDING"),
  
  DISABLED("DISABLED");

  private String value;

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

