package com.ai_kids_care.v1.config;

import jakarta.annotation.PostConstruct;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * AES-256-GCM 摄像头流密码加密配置 — ADR-0026 Phase 1.
 *
 * <p>密钥通过 {@code camera-stream.crypto.keys.<version>} 属性注入（环境变量）。
 * 当前活跃版本由 {@code camera-stream.crypto.current-version} 指定。
 * 值缺失或空白时 Spring 上下文启动即失败（fail-fast）。
 */
@Validated
@ConfigurationProperties(prefix = "camera-stream.crypto")
public class CameraStreamCryptoConfig {

    @NotBlank(message = "camera-stream.crypto.current-version must not be blank")
    private String currentVersion;

    @NotEmpty(message = "camera-stream.crypto.keys must not be empty")
    private Map<String, String> keys = new LinkedHashMap<>();

    /**
     * 启动时验证所有已配置密钥值均非空白（fail-fast，镜像 RrnHashConfig 的 @NotBlank 模式）。
     * @NotEmpty 仅验证 Map 非空，不验证单个值 — 此方法补全该校验。
     */
    @PostConstruct
    void validateKeys() {
        for (Map.Entry<String, String> entry : keys.entrySet()) {
            if (entry.getValue() == null || entry.getValue().isBlank()) {
                throw new IllegalStateException(
                        "camera-stream.crypto.keys." + entry.getKey()
                                + " must not be blank — set CAMERA_STREAM_AES_KEY_V1");
            }
        }
    }

    /**
     * 返回当前活跃版本的 Base64 密钥（用于加密）。
     */
    public String activeKey() {
        String key = keys.get(currentVersion);
        if (key == null || key.isBlank()) {
            throw new IllegalStateException(
                    "No key configured for camera-stream.crypto.current-version=" + currentVersion);
        }
        return key;
    }

    /**
     * 按版本号取密钥（用于解密，支持密钥轮换）。
     */
    public String keyForVersion(String version) {
        String key = keys.get(version);
        if (key == null || key.isBlank()) {
            throw new IllegalStateException("No key configured for version=" + version);
        }
        return key;
    }

    public String getCurrentVersion() {
        return currentVersion;
    }

    public void setCurrentVersion(String currentVersion) {
        this.currentVersion = currentVersion;
    }

    public Map<String, String> getKeys() {
        return keys;
    }

    public void setKeys(Map<String, String> keys) {
        this.keys = keys;
    }
}
