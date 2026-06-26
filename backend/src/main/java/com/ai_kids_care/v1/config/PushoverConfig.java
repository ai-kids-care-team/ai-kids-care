package com.ai_kids_care.v1.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Pushover 应用级凭据配置（PUSH 主通道）。
 *
 * <p>应用 API token 通过 {@code pushover.api-token} 属性注入（环境变量 {@code PUSHOVER_API_TOKEN}）。
 * 值缺失或空白时 Spring 上下文启动即失败（fail-fast，镜像 {@link InternalAiServiceConfig} /
 * {@link RrnHashConfig} 模式）—— 杜绝此前「硬编码空串、运行即抛」的隐患。
 *
 * <p>注意：每个收件人的 Pushover user-key 是**投递地址**，存于 {@code push_subscriptions.address}，
 * 不在此配置内（此处仅应用级 token）。
 */
@Validated
@ConfigurationProperties(prefix = "pushover")
public class PushoverConfig {

    @NotBlank(message = "pushover.api-token must not be blank — set PUSHOVER_API_TOKEN environment variable")
    private String apiToken;

    public String getApiToken() {
        return apiToken;
    }

    public void setApiToken(String apiToken) {
        this.apiToken = apiToken;
    }
}
