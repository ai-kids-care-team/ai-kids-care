package com.ai_kids_care.v1.internal;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

/**
 * AI GPU-stack &rarr; backend Claim/Lease work-distribution request
 * (shard-live-detection-deployments D2, {@code POST /api/v1/internal/streams/claim}).
 *
 * <p>{@code deploymentId} identifies the calling GPU stack (one per stack, distinct from the shared
 * {@code AI_SERVICE_TOKEN} used only for authentication) and owns any Redis lease it claims/renews.
 * {@code capacity} is the caller's worker-pool ceiling (its {@code MAX_WORKERS}). {@code running}
 * doubles as a heartbeat/renewal signal: any stream id in it that is still {@code enabled=true} AND
 * whose lease is currently owned by {@code deploymentId} gets its TTL refreshed (compare-and-renew)
 * and is included in the response {@code assigned} set. An empty/null list is valid (cold start —
 * no stream previously claimed by this stack).
 */
public record StreamClaimRequest(
        @NotBlank String deploymentId,
        @Min(0) int capacity,
        List<Long> running
) {

    /** {@code running} is nullable per contract (cold-start empty array); callers should use this. */
    public List<Long> runningOrEmpty() {
        return running == null ? List.of() : running;
    }
}
