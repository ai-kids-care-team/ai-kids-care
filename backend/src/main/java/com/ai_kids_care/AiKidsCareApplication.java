package com.ai_kids_care;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class AiKidsCareApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiKidsCareApplication.class, args);
    }
}
