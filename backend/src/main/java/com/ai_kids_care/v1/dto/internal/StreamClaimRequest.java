package com.ai_kids_care.v1.internal;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * AI GPU-stack &rarr; backend Claim/Lease work-distribution request
 * (shard-live-detection-deployments D2, {@code POST /api/v1/internal/streams/claim}).
 *
 * <p>{@code deploymentId} identifies the calling GPU stack (one per stack, distinct from the shared
 * {@code AI_SERVICE_TOKEN} used only for authentication) and owns any Redis lease it claims/renews.
 * {@code capacity} is the caller's worker-pool ceiling (its {@code MAX_WORKERS}, bounded by
 * {@link #MAX_CAPACITY} — SEC-07, harden-claim-lease-internal C2). {@code running} doubles as a
 * heartbeat/renewal signal: any stream id in it that is still {@code enabled=true} AND whose lease
 * is currently owned by {@code deploymentId} gets its TTL refreshed (compare-and-renew) and is
 * included in the response {@code assigned} set. An empty/null list is valid (cold start — no
 * stream previously claimed by this stack).
 */
public record StreamClaimRequest(
        @NotBlank @Size(max = 128) String deploymentId,
        @Min(0) @Max(StreamClaimRequest.MAX_CAPACITY) int capacity,
        List<Long> running
) {

    /**
     * SEC-07 (harden-claim-lease-internal C2): upper bound on a single claim call's {@code capacity}.
     * {@code AI_SERVICE_TOKEN} is a single Bearer token shared by every GPU stack (OQ-3=B, accepted
     * blast-radius risk) — without an upper bound, any caller holding that token could set
     * {@code capacity} to an arbitrarily large number and claim every active stream on the platform
     * in one call. 64 is a defensible ceiling: the compose default worker pool is
     * {@code MAX_WORKERS=2} and the benchmark runbook tunes it in the single/low-double-digit range
     * per GPU stack, so 64 leaves an order-of-magnitude margin for legitimate growth while still
     * being far short of "the whole platform" and making an over-capacity call detectable/rejectable
     * (400) rather than silently draining the shared lease pool.
     */
    public static final int MAX_CAPACITY = 64;

    /** {@code running} is nullable per contract (cold-start empty array); callers should use this. */
    public List<Long> runningOrEmpty() {
        return running == null ? List.of() : running;
    }
}
