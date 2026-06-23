package com.ai_kids_care;

import com.ai_kids_care.v1.config.CameraStreamCryptoConfig;
import com.ai_kids_care.v1.config.InternalAiServiceConfig;
import com.ai_kids_care.v1.config.PushoverConfig;
import com.ai_kids_care.v1.config.RrnHashConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
@EnableConfigurationProperties({RrnHashConfig.class, CameraStreamCryptoConfig.class, InternalAiServiceConfig.class, PushoverConfig.class})
public class AiKidsCareApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiKidsCareApplication.class, args);
    }
}
