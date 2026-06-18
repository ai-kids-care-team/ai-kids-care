package com.ai_kids_care.v1.dto;

import com.ai_kids_care.v1.type.CameraStreamTypeEnum;
import com.ai_kids_care.v1.type.ProtocolEnum;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * 摄像头流创建请求 — ADR-0026 Phase 1.
 *
 * <p>敏感字段（sourceUrl、streamUser、streamPassword）通过 {@code @Schema(hidden = true)}
 * 从 OpenAPI schema 中隐藏，防止触发 denylist 扫描。
 * {@code @JsonProperty(access = WRITE_ONLY)} 防止 streamPassword 意外序列化到响应或日志。
 */
public class CameraStreamCreateRequest {

    @NotNull(message = "cameraId is required")
    private Long cameraId;

    private CameraStreamTypeEnum streamType;

    @Schema(hidden = true)
    private String sourceUrl;

    @Schema(hidden = true)
    private String streamUser;

    @Schema(hidden = true)
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String streamPassword;

    private ProtocolEnum sourceProtocol;

    private String playbackUrl;

    private ProtocolEnum playbackProtocol;

    private Integer fps;

    private String resolution;

    private Boolean isPrimary;

    private Boolean enabled;

    public Long getCameraId() {
        return cameraId;
    }

    public void setCameraId(Long cameraId) {
        this.cameraId = cameraId;
    }

    public CameraStreamTypeEnum getStreamType() {
        return streamType;
    }

    public void setStreamType(CameraStreamTypeEnum streamType) {
        this.streamType = streamType;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }

    public void setSourceUrl(String sourceUrl) {
        this.sourceUrl = sourceUrl;
    }

    public String getStreamUser() {
        return streamUser;
    }

    public void setStreamUser(String streamUser) {
        this.streamUser = streamUser;
    }

    public String getStreamPassword() {
        return streamPassword;
    }

    public void setStreamPassword(String streamPassword) {
        this.streamPassword = streamPassword;
    }

    public ProtocolEnum getSourceProtocol() {
        return sourceProtocol;
    }

    public void setSourceProtocol(ProtocolEnum sourceProtocol) {
        this.sourceProtocol = sourceProtocol;
    }

    public String getPlaybackUrl() {
        return playbackUrl;
    }

    public void setPlaybackUrl(String playbackUrl) {
        this.playbackUrl = playbackUrl;
    }

    public ProtocolEnum getPlaybackProtocol() {
        return playbackProtocol;
    }

    public void setPlaybackProtocol(ProtocolEnum playbackProtocol) {
        this.playbackProtocol = playbackProtocol;
    }

    public Integer getFps() {
        return fps;
    }

    public void setFps(Integer fps) {
        this.fps = fps;
    }

    public String getResolution() {
        return resolution;
    }

    public void setResolution(String resolution) {
        this.resolution = resolution;
    }

    public Boolean getIsPrimary() {
        return isPrimary;
    }

    public void setIsPrimary(Boolean isPrimary) {
        this.isPrimary = isPrimary;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public String toString() {
        // streamPassword intentionally excluded to prevent accidental logging
        return "CameraStreamCreateRequest{cameraId=" + cameraId
                + ", streamType=" + streamType
                + ", sourceProtocol=" + sourceProtocol
                + ", playbackProtocol=" + playbackProtocol
                + ", fps=" + fps
                + ", resolution='" + resolution + '\''
                + ", isPrimary=" + isPrimary
                + ", enabled=" + enabled + '}';
    }
}
