package com.ai_kids_care.v1.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * EMAIL 通道 SMTP 凭据 fail-fast 校验（{@code spring.mail.*}）。
 *
 * <p>{@code spring.mail.host/port/username/password} 通过属性注入（环境变量 {@code MAIL_HOST} /
 * {@code MAIL_PORT} / {@code MAIL_USERNAME} / {@code MAIL_PASSWORD}）。任一值缺失或空白时 Spring
 * 上下文启动即失败（fail-fast，镜像 {@link PushoverConfig}/{@link SolapiConfig} 模式）——杜绝
 * 「空凭据静默尝试发信」的隐患。
 *
 * <p>这是一个独立于 Spring Boot 自身 {@code MailProperties} 的校验专用配置类：两者绑定同一
 * {@code spring.mail.*} 命名空间（Spring 允许多个 {@code @ConfigurationProperties} 类绑定同一前缀），
 * 容器自动装配的 {@code JavaMailSender}（{@code spring-boot-starter-mail}，{@link SmtpEmailAdapter}
 * 注入使用）仍读取 Boot 自己的 {@code MailProperties}；本类只额外加 {@code @NotBlank} 校验，Boot 的
 * {@code MailProperties} 本身没有这层校验。{@code port} 在此按 {@code String} 校验非空（fail-fast
 * 语义），实际发信仍由 Boot 的 {@code MailProperties.port}（{@code Integer}）驱动。
 */
@Validated
@ConfigurationProperties(prefix = "spring.mail")
public class SmtpConfig {

    @NotBlank(message = "spring.mail.host must not be blank — set MAIL_HOST environment variable")
    private String host;

    @NotBlank(message = "spring.mail.port must not be blank — set MAIL_PORT environment variable")
    private String port;

    @NotBlank(message = "spring.mail.username must not be blank — set MAIL_USERNAME environment variable")
    private String username;

    @NotBlank(message = "spring.mail.password must not be blank — set MAIL_PASSWORD environment variable")
    private String password;

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public String getPort() {
        return port;
    }

    public void setPort(String port) {
        this.port = port;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
