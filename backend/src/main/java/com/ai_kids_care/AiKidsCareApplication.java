package com.ai_kids_care;

import com.ai_kids_care.v1.config.CameraStreamCryptoConfig;
import com.ai_kids_care.v1.config.InternalAiServiceConfig;
import com.ai_kids_care.v1.config.MinioProperties;
import com.ai_kids_care.v1.config.PushoverConfig;
import com.ai_kids_care.v1.config.RrnHashConfig;
import com.ai_kids_care.v1.config.SolapiConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableAsync
@EnableScheduling
@EnableConfigurationProperties({RrnHashConfig.class, CameraStreamCryptoConfig.class, InternalAiServiceConfig.class, PushoverConfig.class, SolapiConfig.class, MinioProperties.class})
public class AiKidsCareApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiKidsCareApplication.class, args);
    }
}
