package com.ai_kids_care.v1.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Solapi 应용级 SMS 凭据 + 발신번호 配置(SMS 通道)。
 *
 * <p>{@code solapi.api-key} / {@code solapi.api-secret} / {@code solapi.sender} 通过属性注入
 * (环境变量 {@code SOLAPI_API_KEY} / {@code SOLAPI_API_SECRET} / {@code SOLAPI_SENDER})。任一值
 * 缺失或空白时 Spring 上下文启动即失败(fail-fast,镜像 {@link PushoverConfig} 模式)—— 杜绝
 * 「空凭据静默发送」的隐患。
 *
 * <p>注意:每个收件人的手机号是**投递地址**,存于 {@code users.phone},不在此配置内(此处仅应用级凭据
 * + 발신번호)。{@code sender} 须在 Solapi 控制台预注册(발신번호 사전등록)方可真实发送 —— 见部署 follow-up。
 */
@Validated
@ConfigurationProperties(prefix = "solapi")
public class SolapiConfig {

    @NotBlank(message = "solapi.api-key must not be blank — set SOLAPI_API_KEY environment variable")
    private String apiKey;

    @NotBlank(message = "solapi.api-secret must not be blank — set SOLAPI_API_SECRET environment variable")
    private String apiSecret;

    @NotBlank(message = "solapi.sender must not be blank — set SOLAPI_SENDER environment variable (a pre-registered 발신번호)")
    private String sender;

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getApiSecret() {
        return apiSecret;
    }

    public void setApiSecret(String apiSecret) {
        this.apiSecret = apiSecret;
    }

    public String getSender() {
        return sender;
    }

    public void setSender(String sender) {
        this.sender = sender;
    }
}
