package com.ai_kids_care.v1.config;

import net.nurigo.sdk.NurigoApp;
import net.nurigo.sdk.message.service.DefaultMessageService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Provides the Solapi SDK client ({@code DefaultMessageService}) as an injectable bean so
 * {@code SolapiSmsAdapter} can be unit-tested with a stubbed client (no real Solapi calls in CI).
 * Credentials come from {@link SolapiConfig} (fail-fast on blank). Mirrors {@code PushoverClientConfig}.
 *
 * <p>PRF-03 timeout limitation: {@code NurigoApp.INSTANCE.initialize(...)} creates a
 * Retrofit2/OkHttp client internally; the SDK 4.3.0 public API does not expose an
 * {@code OkHttpClient} injection point, so HTTP connect/read timeouts cannot be set here
 * without reflection or SDK upgrade.
 *
 * <p>Recommended callsite guard: wrap SMS delivery in a bounded budget at the dispatch layer,
 * e.g.:
 * <pre>
 *   CompletableFuture
 *       .supplyAsync(() -> smsPort.send(...))
 *       .orTimeout(5, TimeUnit.SECONDS)
 *       .join();
 * </pre>
 * This matches the approach used for Pushover in {@link PushoverClientConfig} and ensures that
 * a hung Solapi endpoint does not permanently hold a DB connection (PRF-02) or @Async thread
 * (PRF-01). A future SDK upgrade or a wrapping {@code OkHttpClient} injection can replace this
 * callsite guard once the SDK exposes the hook.
 * </p>
 */
@Configuration
public class SolapiClientConfig {

    private static final String SOLAPI_API_DOMAIN = "https://api.solapi.com";

    @Bean
    public DefaultMessageService solapiMessageService(SolapiConfig config) {
        // PRF-03 NOTE: Nurigo SDK 4.3.0 does not expose OkHttpClient injection;
        // timeouts must be enforced at the callsite (see Javadoc above).
        return NurigoApp.INSTANCE.initialize(config.getApiKey(), config.getApiSecret(), SOLAPI_API_DOMAIN);
    }
}
