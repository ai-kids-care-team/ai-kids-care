package com.ai_kids_care.v1.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

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
 */
@Service
public class StreamLeaseService {

    private static final String LEASE_KEY_PREFIX = "stream_lease:";

    private static final DefaultRedisScript<Long> RENEW_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('GET', KEYS[1]) == ARGV[1] then "
                    + "return redis.call('PEXPIRE', KEYS[1], ARGV[2]) "
                    + "else return 0 end",
            Long.class);

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

    private static String leaseKey(Long streamId) {
        return LEASE_KEY_PREFIX + streamId;
    }
}
