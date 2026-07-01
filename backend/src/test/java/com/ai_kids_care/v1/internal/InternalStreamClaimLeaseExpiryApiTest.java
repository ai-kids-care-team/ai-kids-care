package com.ai_kids_care.v1.internal;

import com.ai_kids_care.BaseIntegrationTest;
import com.ai_kids_care.v1.service.CameraStreamService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies failover re-claim after lease expiry (shard-live-detection-deployments D2 risk
 * mitigation: "some stack dies, its lease TTLs out, another stack picks the stream back up").
 *
 * <p>Isolated into its own test class because it overrides {@code ai.stream-lease.ttl-seconds} down
 * to 2s via {@link DynamicPropertySource} so the expiry can be observed without a 60s real-time
 * wait — like {@code GraphIntegrationTest}, changing the property set builds a separate cached
 * Spring context, so it does not slow down or destabilize {@link InternalStreamClaimApiTest}'s
 * fast assertions (which keep the default 60s TTL). The PG/Redis containers are still the ones
 * inherited from {@link BaseIntegrationTest}.
 */
class InternalStreamClaimLeaseExpiryApiTest extends BaseIntegrationTest {

    @DynamicPropertySource
    static void shortLeaseTtl(DynamicPropertyRegistry registry) {
        registry.add("ai.stream-lease.ttl-seconds", () -> "2");
    }

    @Autowired private JdbcTemplate jdbc;
    @Autowired private StringRedisTemplate redisTemplate;
    @Autowired private CameraStreamService cameraStreamService;

    private List<Long> allStreamIds;

    @BeforeEach
    void setUp() {
        allStreamIds = jdbc.queryForList("SELECT stream_id FROM camera_streams ORDER BY stream_id", Long.class);
        assertThat(allStreamIds).isNotEmpty();
        jdbc.update("UPDATE camera_streams SET enabled = true");
        for (Long id : allStreamIds) {
            redisTemplate.delete("stream_lease:" + id);
        }
    }

    @AfterEach
    void tearDown() {
        jdbc.update("UPDATE camera_streams SET enabled = true");
        for (Long id : allStreamIds) {
            redisTemplate.delete("stream_lease:" + id);
        }
    }

    private List<Long> claimAssignedIds(String deploymentId, int capacity, List<Long> running) {
        return cameraStreamService.claimStreams(new StreamClaimRequest(deploymentId, capacity, running))
                .assigned().stream().map(ActiveStreamVO::streamId).toList();
    }

    @Test
    void claim_afterLeaseExpiry_isReclaimedByAnotherDeployment() throws InterruptedException {
        long onlyId = allStreamIds.get(0);
        for (Long id : allStreamIds) {
            if (!id.equals(onlyId)) {
                jdbc.update("UPDATE camera_streams SET enabled = false WHERE stream_id = ?", id);
            }
        }

        List<Long> assignedA = claimAssignedIds("dep-expiry-a", 1, List.of());
        assertThat(assignedA).containsExactly(onlyId);

        // Stack A goes silent (crash/network partition): it stops polling/renewing. Stack B keeps
        // polling; once the 2s TTL lapses it must pick the now-unowned stream back up. Bounded
        // retry (not a fixed sleep) keeps this robust under slow CI while staying well within the
        // TTL-plus-margin budget.
        List<Long> assignedB = List.of();
        for (int i = 0; i < 40 && assignedB.isEmpty(); i++) {
            assignedB = claimAssignedIds("dep-expiry-b", 1, List.of());
            if (assignedB.isEmpty()) {
                Thread.sleep(150);
            }
        }
        assertThat(assignedB).as("after TTL expiry, a different deployment can claim the released stream")
                .containsExactly(onlyId);
    }
}
