package com.ai_kids_care.v1.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Redis-backed lease store for cross-GPU-stack stream distribution
 * (shard-live-detection-deployments D2, Claim/Lease dynamic pool).
 *
 * <p>Key {@code stream_lease:{streamId}} = owning {@code deploymentId}; TTL is configurable
 * ({@code ai.stream-lease.ttl-seconds}, default 60s — several multiples of the AI poll interval,
 * default 20s, so 1-2 dropped polls don't cause spurious failover). No schema migration: leases are
 * ephemeral runtime state, not authoritative data — Redis TTL/atomicity fit natively. Reuses the
 * existing {@link StringRedisTemplate}/{@code RedisConnectionFactory} (same infra as
 * {@link com.ai_kids_care.v1.security.LoginThrottleService}; zero new dependency).
 *
 * <p>Atomicity: claiming a free stream is a single {@code SET NX EX} — at most one deployment ever
 * wins a race for the same key. Renewing is a Lua compare-and-renew
 * ({@code if GET==deploymentId then PEXPIRE}) so a stack can never accidentally steal or extend
 * another stack's lease.
 *
 * <p><b>PRF-09 batch variants (harden-claim-lease-internal C2)</b>: {@link #renewBatch} and
 * {@link #tryClaimBatch} cover a whole candidate list in a single Redis round trip (one Lua script
 * execution) instead of one round trip per stream, while keeping the exact same per-key atomicity
 * as {@link #renew} / {@link #tryClaim} — each candidate inside the script is still an independent
 * compare-and-renew / {@code SET NX}, and the cap (capacity / spare) is only ever incremented on a
 * <i>successful</i> per-key operation, mirroring the original sequential-loop semantics exactly
 * (see {@code CameraStreamInternalService#claimStreams}).
 */
@Service
public class StreamLeaseService {

    private static final String LEASE_KEY_PREFIX = "stream_lease:";

    private static final DefaultRedisScript<Long> RENEW_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('GET', KEYS[1]) == ARGV[1] then "
                    + "return redis.call('PEXPIRE', KEYS[1], ARGV[2]) "
                    + "else return 0 end",
            Long.class);

    // Batch compare-and-renew: candidates are attempted in KEYS order; a key only counts against
    // `capacity` when the compare-and-renew for THAT key actually succeeds (a candidate owned by a
    // different/no deployment is skipped without consuming a slot) — same rule as the original
    // Java loop (`renewedIds.size() >= request.capacity()` checked only after successes).
    @SuppressWarnings("rawtypes")
    private static final DefaultRedisScript<List> BATCH_RENEW_SCRIPT = new DefaultRedisScript<>(
            """
            local deploymentId = ARGV[1]
            local capacity = tonumber(ARGV[2])
            local ttlMs = ARGV[3]
            local renewed = 0
            local results = {}
            for i, key in ipairs(KEYS) do
                if renewed < capacity and redis.call('GET', key) == deploymentId then
                    redis.call('PEXPIRE', key, ttlMs)
                    results[i] = 1
                    renewed = renewed + 1
                else
                    results[i] = 0
                end
            end
            return results
            """,
            List.class);

    // Batch claim: candidates are attempted in KEYS order; a key only counts against `spare` when
    // its own SET NX actually succeeds (an already-leased candidate is skipped without consuming a
    // slot) — same rule as the original Java loop (`spare--` only inside the `if (tryClaim(...))`
    // branch). Each SET NX is still individually atomic; Redis serializes whole script executions,
    // so a concurrent race on the same key still yields at most one winner.
    @SuppressWarnings("rawtypes")
    private static final DefaultRedisScript<List> BATCH_CLAIM_SCRIPT = new DefaultRedisScript<>(
            """
            local deploymentId = ARGV[1]
            local ttlMs = ARGV[2]
            local spare = tonumber(ARGV[3])
            local claimed = 0
            local results = {}
            for i, key in ipairs(KEYS) do
                if claimed < spare and redis.call('set', key, deploymentId, 'NX', 'PX', ttlMs) then
                    results[i] = 1
                    claimed = claimed + 1
                else
                    results[i] = 0
                end
            end
            return results
            """,
            List.class);

    private final StringRedisTemplate redisTemplate;
    private final Duration ttl;

    public StreamLeaseService(
            StringRedisTemplate redisTemplate,
            @Value("${ai.stream-lease.ttl-seconds:60}") long ttlSeconds
    ) {
        this.redisTemplate = redisTemplate;
        this.ttl = Duration.ofSeconds(Math.max(1, ttlSeconds));
    }

    /**
     * Atomically claim a currently-unleased (or expired-lease) stream for {@code deploymentId}.
     * {@code SET NX EX} — under a concurrent claim race on the same stream, at most one caller wins.
     */
    public boolean tryClaim(Long streamId, String deploymentId) {
        Boolean claimed = redisTemplate.opsForValue()
                .setIfAbsent(leaseKey(streamId), deploymentId, ttl);
        return Boolean.TRUE.equals(claimed);
    }

    /**
     * Compare-and-renew: refreshes the TTL only if {@code deploymentId} currently owns the lease.
     * Never extends or steals another stack's lease; a lease that already expired (or never
     * existed, or is held by a different deployment) returns false — the caller's claim algorithm
     * then does not include that stream in {@code assigned}, so the requesting stack stops it.
     */
    public boolean renew(Long streamId, String deploymentId) {
        Long result = redisTemplate.execute(
                RENEW_SCRIPT,
                List.of(leaseKey(streamId)),
                deploymentId,
                String.valueOf(ttl.toMillis()));
        return result != null && result == 1L;
    }

    /**
     * Whether {@code deploymentId} currently holds the lease for {@code streamId} — defense-in-depth
     * check for the credentials endpoint (shard-live-detection-deployments D2 risk mitigation).
     * A {@code null}/blank deploymentId never matches (missing {@code X-Deployment-Id} header).
     */
    public boolean isOwnedBy(Long streamId, String deploymentId) {
        if (deploymentId == null || deploymentId.isBlank()) {
            return false;
        }
        String owner = redisTemplate.opsForValue().get(leaseKey(streamId));
        return deploymentId.equals(owner);
    }

    /**
     * Batch compare-and-renew (PRF-09): one Redis round trip for the whole candidate list, capped
     * at {@code capacity} successful renewals. Equivalent to calling {@link #renew(Long, String)}
     * once per candidate, in order, stopping once {@code capacity} renewals have succeeded — but as
     * a single atomic Lua script instead of N round trips. Never extends or steals another stack's
     * lease (same per-key compare-and-renew as {@link #renew}).
     *
     * @param streamIds    candidates in the order they should be attempted
     * @param deploymentId the single owner id every candidate is compared against
     * @param capacity     max number of successful renewals; {@code <= 0} short-circuits (no Redis call)
     * @return the subset of {@code streamIds} whose lease was renewed
     */
    public Set<Long> renewBatch(List<Long> streamIds, String deploymentId, int capacity) {
        if (streamIds.isEmpty() || capacity <= 0) {
            return Set.of();
        }
        List<String> keys = streamIds.stream().map(StreamLeaseService::leaseKey).toList();
        @SuppressWarnings("unchecked")
        List<Long> results = redisTemplate.execute(
                BATCH_RENEW_SCRIPT,
                keys,
                deploymentId,
                String.valueOf(capacity),
                String.valueOf(ttl.toMillis()));
        return succeededIds(streamIds, results);
    }

    /**
     * Batch claim (PRF-09): one Redis round trip attempts {@code SET NX EX} for every candidate in
     * order, capped at {@code spare} successful claims. Each individual key is still an independent
     * {@code SET NX} — a concurrent claim race on the same key still yields at most one winner
     * (Redis serializes whole script executions), whether the racing callers batch or not.
     *
     * @param streamIds    candidates in the order they should be attempted (already excluding any
     *                     stream renewed earlier in the same {@code claimStreams} call)
     * @param deploymentId the claiming deployment
     * @param spare        max number of successful claims; {@code <= 0} short-circuits (no Redis call)
     * @return the subset of {@code streamIds} newly claimed by {@code deploymentId}
     */
    public Set<Long> tryClaimBatch(List<Long> streamIds, String deploymentId, int spare) {
        if (streamIds.isEmpty() || spare <= 0) {
            return Set.of();
        }
        List<String> keys = streamIds.stream().map(StreamLeaseService::leaseKey).toList();
        @SuppressWarnings("unchecked")
        List<Long> results = redisTemplate.execute(
                BATCH_CLAIM_SCRIPT,
                keys,
                deploymentId,
                String.valueOf(ttl.toMillis()),
                String.valueOf(spare));
        return succeededIds(streamIds, results);
    }

    private static Set<Long> succeededIds(List<Long> streamIds, List<Long> results) {
        Set<Long> succeeded = new LinkedHashSet<>();
        for (int i = 0; i < streamIds.size(); i++) {
            Long flag = (results != null && i < results.size()) ? results.get(i) : null;
            if (Long.valueOf(1L).equals(flag)) {
                succeeded.add(streamIds.get(i));
            }
        }
        return succeeded;
    }

    private static String leaseKey(Long streamId) {
        return LEASE_KEY_PREFIX + streamId;
    }
}
