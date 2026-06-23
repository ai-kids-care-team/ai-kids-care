package com.ai_kids_care.v1.config;

import net.nurigo.sdk.NurigoApp;
import net.nurigo.sdk.message.service.DefaultMessageService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Provides the Solapi SDK client ({@code DefaultMessageService}) as an injectable bean so
 * {@code SolapiSmsAdapter} can be unit-tested with a stubbed client (no real Solapi calls in CI).
 * Credentials come from {@link SolapiConfig} (fail-fast on blank). Mirrors {@code PushoverClientConfig}.
 */
@Configuration
public class SolapiClientConfig {

    private static final String SOLAPI_API_DOMAIN = "https://api.solapi.com";

    @Bean
    public DefaultMessageService solapiMessageService(SolapiConfig config) {
        return NurigoApp.INSTANCE.initialize(config.getApiKey(), config.getApiSecret(), SOLAPI_API_DOMAIN);
    }
}
