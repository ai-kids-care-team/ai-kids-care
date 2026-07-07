package com.ai_kids_care.v1.internal;

import com.ai_kids_care.BaseIntegrationTest;
import com.ai_kids_care.v1.dto.internal.StreamClaimRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP contract tests for the Claim/Lease work-distribution endpoint
 * (shard-live-detection-deployments D2): {@code POST /api/v1/internal/streams/claim}, plus the
 * lease-ownership check added to {@code GET /api/v1/internal/streams/{id}/credentials}.
 *
 * <p>Uses the seeded {@code camera_streams} rows (ids discovered dynamically, currently 1/2/3, all
 * {@code enabled=true}). Each test normalizes to "all seeded streams enabled, no leases held" in
 * {@link #setUp()}/{@link #tearDown()} so tests are order-independent and don't leak into sibling
 * suites sharing this JVM's Redis/Postgres testcontainers.
 *
 * <p>Lease-expiry re-claim (which needs a short TTL) is covered separately in
 * {@link InternalStreamClaimLeaseExpiryApiTest} — that test overrides
 * {@code ai.stream-lease.ttl-seconds} via a dedicated Spring context so it does not slow down or
 * destabilize the fast assertions here (default TTL is 60s).
 */
@AutoConfigureMockMvc
class InternalStreamClaimApiTest extends BaseIntegrationTest {

    private static final String TOKEN = "Bearer test-ai-service-token-not-secret-2026";
    private static final String CLAIM_PATH = "/api/v1/internal/streams/claim";

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private StringRedisTemplate redisTemplate;

    private List<Long> allStreamIds;

    @BeforeEach
    void setUp() {
        allStreamIds = jdbc.queryForList("SELECT stream_id FROM camera_streams ORDER BY stream_id", Long.class);
        assertThat(allStreamIds).as("seed has at least 3 camera streams for these tests").hasSizeGreaterThanOrEqualTo(3);
        jdbc.update("UPDATE camera_streams SET enabled = true");
        clearLeases();
    }

    @AfterEach
    void tearDown() {
        jdbc.update("UPDATE camera_streams SET enabled = true");
        clearLeases();
    }

    private void clearLeases() {
        for (Long id : allStreamIds) {
            redisTemplate.delete("stream_lease:" + id);
        }
    }

    private List<Long> claimAssignedIds(String deploymentId, int capacity, List<Long> running) throws Exception {
        StreamClaimRequest request = new StreamClaimRequest(deploymentId, capacity, running);
        String body = mockMvc.perform(post(CLAIM_PATH)
                        .header("Authorization", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode assignedNode = objectMapper.readTree(body).get("assigned");
        List<Long> ids = new ArrayList<>();
        assignedNode.forEach(n -> ids.add(n.get("streamId").asLong()));
        return ids;
    }

    // --- capacity / claim ---------------------------------------------------

    @Test
    void claim_coldStart_emptyRunningAndZeroCapacity_returnsEmptyAssigned() throws Exception {
        List<Long> assigned = claimAssignedIds("dep-cold", 0, List.of());
        assertThat(assigned).isEmpty();
    }

    @Test
    void claim_assignsUpToCapacity_andNoMore() throws Exception {
        List<Long> assigned = claimAssignedIds("dep-cap", 1, List.of());
        assertThat(assigned).as("capacity=1 claims exactly one stream, not the whole active set")
                .hasSize(1);
        assertThat(allStreamIds).contains(assigned.get(0));
    }

    @Test
    void claim_capacityCoversAllActiveStreams_assignsEntireSet() throws Exception {
        List<Long> assigned = claimAssignedIds("dep-all", allStreamIds.size(), List.of());
        assertThat(assigned).containsExactlyInAnyOrderElementsOf(allStreamIds);
    }

    @Test
    void claim_running_isRenewed_andReturnsSameAssignmentOnNextPoll() throws Exception {
        List<Long> first = claimAssignedIds("dep-renew", allStreamIds.size(), List.of());
        assertThat(first).containsExactlyInAnyOrderElementsOf(allStreamIds);

        // Second poll: same deployment reports everything it got as "running" (heartbeat/renewal).
        // The renew path (compare-and-renew) must keep every stream assigned to the same owner,
        // never reassigning them elsewhere.
        List<Long> second = claimAssignedIds("dep-renew", allStreamIds.size(), first);
        assertThat(second).containsExactlyInAnyOrderElementsOf(allStreamIds);
    }

    @Test
    void claim_capacityLoweredBelowRunning_renewalIsCappedToCapacity() throws Exception {
        // Deployment first claims the whole active set...
        List<Long> first = claimAssignedIds("dep-shrink", allStreamIds.size(), List.of());
        assertThat(first).containsExactlyInAnyOrderElementsOf(allStreamIds);

        // ...then re-claims with a lowered capacity while still reporting all of them as running.
        // Renewal must be bounded by capacity, so assigned never exceeds the new (smaller) capacity —
        // the excess leases are simply not renewed (they expire by TTL) and drop out of assigned.
        List<Long> shrunk = claimAssignedIds("dep-shrink", 1, first);
        assertThat(shrunk).as("renewal path is capacity-bounded (assigned <= capacity)").hasSize(1);
    }

    @Test
    void claim_capacityZeroWithRunning_drainsDeployment() throws Exception {
        List<Long> first = claimAssignedIds("dep-drain", allStreamIds.size(), List.of());
        assertThat(first).containsExactlyInAnyOrderElementsOf(allStreamIds);

        // capacity=0 must drain: no renewals, empty assigned — the stack stops all its workers.
        List<Long> drained = claimAssignedIds("dep-drain", 0, first);
        assertThat(drained).isEmpty();
    }

    @Test
    void claim_runningStreamNoLongerActive_isNotRenewed() throws Exception {
        long disabledId = allStreamIds.get(0);
        jdbc.update("UPDATE camera_streams SET enabled = false WHERE stream_id = ?", disabledId);

        // Deployment reports it as still running (stale local state); backend must not renew a
        // lease for a stream that is no longer active, and must not include it in assigned.
        List<Long> assigned = claimAssignedIds("dep-stale", 5, List.of(disabledId));
        assertThat(assigned).doesNotContain(disabledId);
    }

    @Test
    void claim_concurrentClaimsOnSameStream_onlyOneDeploymentWins() throws Exception {
        long onlyId = allStreamIds.get(0);
        for (Long id : allStreamIds) {
            if (!id.equals(onlyId)) {
                jdbc.update("UPDATE camera_streams SET enabled = false WHERE stream_id = ?", id);
            }
        }

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        try {
            Callable<List<Long>> raceA = () -> {
                ready.countDown();
                go.await(5, TimeUnit.SECONDS);
                return claimAssignedIds("dep-race-a", 1, List.of());
            };
            Callable<List<Long>> raceB = () -> {
                ready.countDown();
                go.await(5, TimeUnit.SECONDS);
                return claimAssignedIds("dep-race-b", 1, List.of());
            };
            Future<List<Long>> futureA = pool.submit(raceA);
            Future<List<Long>> futureB = pool.submit(raceB);
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            go.countDown();

            List<Long> assignedA = futureA.get(5, TimeUnit.SECONDS);
            List<Long> assignedB = futureB.get(5, TimeUnit.SECONDS);

            assertThat(assignedA.size() + assignedB.size())
                    .as("SET NX is atomic: exactly one of the two concurrent claimers wins the single stream")
                    .isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    // --- error contract ------------------------------------------------------

    @Test
    void claim_blankDeploymentId_isRejected() throws Exception {
        mockMvc.perform(post(CLAIM_PATH)
                        .header("Authorization", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"deploymentId\":\"\",\"capacity\":1,\"running\":[]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void claim_negativeCapacity_isRejected() throws Exception {
        mockMvc.perform(post(CLAIM_PATH)
                        .header("Authorization", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"deploymentId\":\"dep-x\",\"capacity\":-1,\"running\":[]}"))
                .andExpect(status().isBadRequest());
    }

    // --- SEC-07: capacity upper bound -----------------------------------------

    @Test
    void claim_capacityOverMaxBound_isRejectedAndClaimsNothing() throws Exception {
        // Over-bound capacity must be rejected by request validation before any lease is touched —
        // no partial-claim side effect.
        mockMvc.perform(post(CLAIM_PATH)
                        .header("Authorization", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"deploymentId\":\"dep-over-max\",\"capacity\":"
                                + (StreamClaimRequest.MAX_CAPACITY + 1) + ",\"running\":[]}"))
                .andExpect(status().isBadRequest());

        for (Long id : allStreamIds) {
            assertThat(redisTemplate.opsForValue().get("stream_lease:" + id))
                    .as("rejected over-capacity request must not have claimed any stream")
                    .isNull();
        }
    }

    @Test
    void claim_capacityAtMaxBound_isAccepted() throws Exception {
        mockMvc.perform(post(CLAIM_PATH)
                        .header("Authorization", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"deploymentId\":\"dep-at-max\",\"capacity\":"
                                + StreamClaimRequest.MAX_CAPACITY + ",\"running\":[]}"))
                .andExpect(status().isOk());
    }

    @Test
    void claim_withoutToken_isRejected() throws Exception {
        mockMvc.perform(post(CLAIM_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new StreamClaimRequest("dep-x", 1, List.of()))))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void claim_withWrongToken_isRejected() throws Exception {
        mockMvc.perform(post(CLAIM_PATH)
                        .header("Authorization", "Bearer not-the-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new StreamClaimRequest("dep-x", 1, List.of()))))
                .andExpect(status().is4xxClientError());
    }

    // --- credentials lease-ownership check -----------------------------------

    @Test
    void credentials_withoutDeploymentHeader_isNotFound() throws Exception {
        long streamId = claimAssignedIds("dep-cred-owner", 1, List.of()).get(0);
        mockMvc.perform(get("/api/v1/internal/streams/{id}/credentials", streamId)
                        .header("Authorization", TOKEN))
                .andExpect(status().isNotFound());
    }

    @Test
    void credentials_withWrongDeploymentId_isNotFound() throws Exception {
        long streamId = claimAssignedIds("dep-cred-owner", 1, List.of()).get(0);
        mockMvc.perform(get("/api/v1/internal/streams/{id}/credentials", streamId)
                        .header("Authorization", TOKEN)
                        .header("X-Deployment-Id", "dep-cred-imposter"))
                .andExpect(status().isNotFound());
    }

    @Test
    void credentials_forUnclaimedActiveStream_isNotFoundEvenWithAHeader() throws Exception {
        // Active stream, never claimed via /claim: no lease exists at all, so any X-Deployment-Id
        // value must still be rejected (hides existence, same as the multi-tenant 404 convention).
        long unclaimedId = allStreamIds.get(0);
        mockMvc.perform(get("/api/v1/internal/streams/{id}/credentials", unclaimedId)
                        .header("Authorization", TOKEN)
                        .header("X-Deployment-Id", "dep-anything"))
                .andExpect(status().isNotFound());
    }

    @Test
    void credentials_withCorrectDeploymentId_returnsUnchangedShape() throws Exception {
        long streamId = claimAssignedIds("dep-cred-owner", 1, List.of()).get(0);
        String sourceUrl = jdbc.queryForObject(
                "SELECT source_url FROM camera_streams WHERE stream_id = ?", String.class, streamId);

        String body = mockMvc.perform(get("/api/v1/internal/streams/{id}/credentials", streamId)
                        .header("Authorization", TOKEN)
                        .header("X-Deployment-Id", "dep-cred-owner"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode node = objectMapper.readTree(body);

        assertThat(node.get("streamId").asLong()).isEqualTo(streamId);
        assertThat(node.get("sourceUrl").asText()).isEqualTo(sourceUrl);
        assertThat(node.has("streamUser")).isTrue();
        assertThat(node.has("streamPassword")).isTrue();
    }

    @Test
    void credentials_withoutToken_isRejected() throws Exception {
        long streamId = allStreamIds.get(0);
        mockMvc.perform(get("/api/v1/internal/streams/{id}/credentials", streamId)
                        .header("X-Deployment-Id", "dep-anything"))
                .andExpect(status().is4xxClientError());
    }
}
