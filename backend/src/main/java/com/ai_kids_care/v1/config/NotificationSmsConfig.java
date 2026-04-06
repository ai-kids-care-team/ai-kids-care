package com.ai_kids_care.v1.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({SolapiProperties.class, NotificationSmsProperties.class})
public class NotificationSmsConfig {
}
