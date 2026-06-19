package com.ai_kids_care.v1.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * AI 推理服务调用内部凭据接口的共享 Bearer token 配置 — ADR-0026 Phase 2（OQ-1=A）。
 *
 * <p>token 通过 {@code internal.ai.service-token} 属性注入（环境变量 {@code AI_SERVICE_TOKEN}）。
 * 值缺失或空白时 Spring 上下文启动即失败（fail-fast，镜像 {@link RrnHashConfig} 模式）。
 *
 * <p>注意：此 token 同时存在于 Java 与 AI 两端运行时，仅用于 {@code /api/v1/internal/**} 端点鉴权；
 * AES 加解密密钥（{@link CameraStreamCryptoConfig}）仍仅存在于 Java 后端。
 */
@Validated
@ConfigurationProperties(prefix = "internal.ai")
public class InternalAiServiceConfig {

    @NotBlank(message = "internal.ai.service-token must not be blank — set AI_SERVICE_TOKEN environment variable")
    private String serviceToken;

    public String getServiceToken() {
        return serviceToken;
    }

    public void setServiceToken(String serviceToken) {
        this.serviceToken = serviceToken;
    }
}
