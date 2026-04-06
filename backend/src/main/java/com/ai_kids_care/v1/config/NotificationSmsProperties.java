package com.ai_kids_care.v1.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "notification.sms")
public class NotificationSmsProperties {
    private boolean schedulerEnabled = true;
    private long initialDelayMs = 10000L;
    private long scheduleDelayMs = 30000L;
    private int batchSize = 50;
    private int maxRetries = 3;
    private boolean dryRun = true;
}
