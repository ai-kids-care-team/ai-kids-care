package com.ai_kids_care.v1.dto;

import lombok.*;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import io.swagger.v3.oas.annotations.media.Schema;


import jakarta.annotation.Generated;

/**
 * CameraStreamsCreateRequest
 */
@Generated(value = "org.openapitools.codegen.languages.SpringCodegen", comments = "Generator version: 7.20.0")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CameraStreamsCreateRequest {

  
  @NonNull
  @Schema(name = "kindergarten_id", description = "유치원 ID(KINDERGARTEN.kindergarten_id)", requiredMode = Schema.RequiredMode.REQUIRED)
  @JsonProperty("kindergarten_id")
  private Long kindergartenId;

  
  @NonNull
  @Schema(name = "camera_id", description = "카메라 ID(CCTV_CAMERA.camera_id)", requiredMode = Schema.RequiredMode.REQUIRED)
  @JsonProperty("camera_id")
  private Long cameraId;

  /**
   * 스트림 타입(메인/서브 등)
   */
  public enum StreamTypeEnum {
    MAIN("MAIN"),
    
    SUB("SUB"),
    
    SNAPSHOT("SNAPSHOT"),
    
    RECORDING("RECORDING"),
    
    OTHER("OTHER");

    private final String value;

    StreamTypeEnum(String value) {
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
    public static StreamTypeEnum fromValue(String value) {
      for (StreamTypeEnum b : StreamTypeEnum.values()) {
        if (b.value.equals(value)) {
          return b;
        }
      }
      throw new IllegalArgumentException("Unexpected value '" + value + "'");
    }
  }

  
  @Schema(name = "stream_type", description = "스트림 타입(메인/서브 등)", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("stream_type")
  private StreamTypeEnum streamType;

  
  @Schema(name = "stream_url", description = "스트림 URL(예: RTSP/HTTP 엔드포인트)", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("stream_url")
  private String streamUrl;

  
  @Schema(name = "stream_user", description = "스트림 인증 사용자명", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("stream_user")
  private String streamUser;

  
  @Schema(name = "stream_password_encrypted", description = "스트림 인증 비밀번호(암호화 저장)", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("stream_password_encrypted")
  private String streamPasswordEncrypted;

  
  @Schema(name = "protocol", description = "스트림 프로토콜(RTSP/HTTP)", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("protocol")
  private String protocol;

  
  @Schema(name = "fps", description = "스트림 FPS(초당 프레임 수)", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("fps")
  private Integer fps;

  
  @Schema(name = "resolution", description = "해상도(예: 1920x1080)", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("resolution")
  private String resolution;

  
  @Schema(name = "is_primary", description = "대표 스트림 여부(카메라당 1개 권장)", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("is_primary")
  private Boolean isPrimary;

  
  @Schema(name = "enabled", description = "스트림 사용 여부", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("enabled")
  private Boolean enabled;

  /**
   * 스트림 상태(운영/비활성 등)
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

  
  @Schema(name = "status", description = "스트림 상태(운영/비활성 등)", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("status")
  private StatusEnum status;

}

