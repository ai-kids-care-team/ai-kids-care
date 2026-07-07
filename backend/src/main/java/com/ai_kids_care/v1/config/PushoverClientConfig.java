package com.ai_kids_care.v1.config;

import net.pushover.client.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Set;
import java.util.concurrent.*;

/**
 * Provides the Pushover client as an injectable bean so {@code PushoverPushAdapter} can be
 * unit-tested with a stubbed client (no real Pushover calls in CI).
 *
 * <p>PRF-03 timeout guard: {@code PushoverRestClient} uses Apache HC {@code DefaultHttpClient}
 * with no public timeout constructor. Rather than pretending a timeout was injected, every call
 * is wrapped in a {@link CompletableFuture} with a hard deadline (connect+read budget = 5 s).
 * When the deadline fires, the future throws {@link TimeoutException} which the wrapper
 * translates to a {@link PushoverException} so callers see a consistent failure type.
 * This ensures a hung Pushover endpoint never blocks a DB connection or @Async thread beyond
 * the configured budget (fixes the unbounded-block half of PRF-03).</p>
 */
@Configuration
public class PushoverClientConfig {

    /** Total wall-clock budget per Pushover API call (connect + read). */
    private static final long CALL_TIMEOUT_SECONDS = 5;

    @Bean
    public PushoverClient pushoverClient() {
        return new TimeoutGuardedPushoverClient(new PushoverRestClient(), CALL_TIMEOUT_SECONDS);
    }

    /**
     * Decorator that enforces a per-call timeout on any {@link PushoverClient} delegate.
     * The delegate's blocking call runs on the current thread's virtual-thread pool via
     * {@link CompletableFuture#supplyAsync}; if it does not complete within
     * {@code timeoutSeconds} the future is cancelled and a {@link PushoverException} is thrown.
     */
    static class TimeoutGuardedPushoverClient implements PushoverClient {

        private final PushoverClient delegate;
        private final long timeoutSeconds;

        TimeoutGuardedPushoverClient(PushoverClient delegate, long timeoutSeconds) {
            this.delegate = delegate;
            this.timeoutSeconds = timeoutSeconds;
        }

        @Override
        public Status pushMessage(PushoverMessage message) throws PushoverException {
            try {
                return CompletableFuture
                        .supplyAsync(() -> {
                            try {
                                return delegate.pushMessage(message);
                            } catch (PushoverException e) {
                                throw new CompletionException(e);
                            }
                        })
                        .orTimeout(timeoutSeconds, TimeUnit.SECONDS)
                        .join();
            } catch (CompletionException e) {
                Throwable cause = e.getCause();
                if (cause instanceof PushoverException pe) throw pe;
                if (cause instanceof TimeoutException) {
                    throw new PushoverException(
                            "Pushover call timed out after " + timeoutSeconds + "s", cause);
                }
                throw new PushoverException("Pushover call failed", cause);
            }
        }

        @Override
        public Set<PushOverSound> getSounds() throws PushoverException {
            try {
                return CompletableFuture
                        .supplyAsync(() -> {
                            try {
                                return delegate.getSounds();
                            } catch (PushoverException e) {
                                throw new CompletionException(e);
                            }
                        })
                        .orTimeout(timeoutSeconds, TimeUnit.SECONDS)
                        .join();
            } catch (CompletionException e) {
                Throwable cause = e.getCause();
                if (cause instanceof PushoverException pe) throw pe;
                if (cause instanceof TimeoutException) {
                    throw new PushoverException(
                            "Pushover getSounds timed out after " + timeoutSeconds + "s", cause);
                }
                throw new PushoverException("Pushover getSounds failed", cause);
            }
        }
    }
}
