package com.ai_kids_care.v1.type;

/**
 * Outcome of a single external notification delivery attempt (PRF-02 atomicity).
 *
 * <ul>
 *   <li>{@code IN_FLIGHT} — the attempt was recorded and committed BEFORE the provider call;
 *       the provider may or may not have actually delivered. A retry that observes IN_FLIGHT
 *       MUST NOT re-send (at-most-once on the retry path, design Decision 2).</li>
 *   <li>{@code SUCCEEDED} — the provider call returned success and the terminal state was recorded.</li>
 *   <li>{@code FAILED} — the provider call failed (error/timeout) and the terminal state was recorded.</li>
 * </ul>
 *
 * Persisted as a plain {@code varchar} (not a PG enum) to keep the migration additive and simple.
 */
public enum DeliveryAttemptOutcome {
    IN_FLIGHT,
    SUCCEEDED,
    FAILED
}
