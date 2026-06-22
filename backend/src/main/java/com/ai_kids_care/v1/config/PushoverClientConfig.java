package com.ai_kids_care.v1.config;

import net.pushover.client.PushoverClient;
import net.pushover.client.PushoverRestClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Provides the Pushover client as an injectable bean so {@code PushoverService} can be
 * unit-tested with a stubbed client (no real Pushover calls in CI).
 */
@Configuration
public class PushoverClientConfig {

    @Bean
    public PushoverClient pushoverClient() {
        return new PushoverRestClient();
    }
}
