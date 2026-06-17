package com.ai_kids_care;

import com.ai_kids_care.v1.config.RrnHashConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(RrnHashConfig.class)
public class AiKidsCareApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiKidsCareApplication.class, args);
    }
}
