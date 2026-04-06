package com.ai_kids_care.v1.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "solapi")
public class SolapiProperties {
    private boolean enabled = false;
    private String apiKey = "";
    private String apiSecret = "";
    private String senderNumber = "";
    private String baseUrl = "https://api.solapi.com";
    private int connectTimeoutMs = 3000;
    private int readTimeoutMs = 5000;
}
