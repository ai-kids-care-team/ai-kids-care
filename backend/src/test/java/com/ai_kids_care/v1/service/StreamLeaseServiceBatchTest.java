package com.ai_kids_care.v1.service;

import com.ai_kids_care.BaseIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PRF-09 (harden-claim-lease-internal C2): {@link StreamLeaseService#renewBatch} /
 * {@link StreamLeaseService#tryClaimBatch} collapse the previous one-round-trip-per-stream renew
 * / claim loop into a single Lua script call, while preserving the exact per-key atomicity and cap
 * semantics of the original sequential {@link StreamLeaseService#renew} /
 * {@link StreamLeaseService#tryClaim} calls. These tests exercise the batch primitives directly
 * against real Redis (not through the HTTP claim endpoint, which is covered end-to-end by
 * {@code InternalStreamClaimApiTest}).
 */
class StreamLeaseServiceBatchTest extends BaseIntegrationTest {

    @Autowired private StreamLeaseService leaseService;
    @Autowired private StringRedisTemplate redisTemplate;

    private static final List<Long> STREAM_IDS = List.of(9001L, 9002L, 9003L, 9004L, 9005L);

    @BeforeEach
    void setUp() {
        clearLeases();
    }

    @AfterEach
    void tearDown() {
        clearLeases();
    }

    private void clearLeases() {
        for (Long id : STREAM_IDS) {
            redisTemplate.delete("stream_lease:" + id);
        }
    }

    // --- tryClaimBatch: equivalence with sequential tryClaim ------------------

    @Test
    void tryClaimBatch_allFree_claimsExactlyThoseRequestedUpToSpare_sameAsSequential() {
        // Reference: what the old sequential loop would produce for the same candidates/spare.
        List<Long> sequentialWinners = STREAM_IDS.stream()
                .limit(3)
                .filter(id -> leaseService.tryClaim(id, "dep-sequential-ref"))
                .toList();
        clearLeases();

        Set<Long> batchWinners = leaseService.tryClaimBatch(STREAM_IDS, "dep-batch", 3);

        assertThat(batchWinners)
                .as("batch claim over an all-free candidate set matches the sequential result")
                .containsExactlyInAnyOrderElementsOf(sequentialWinners);
        assertThat(batchWinners).hasSize(3);
        for (Long id : batchWinners) {
            assertThat(leaseService.isOwnedBy(id, "dep-batch")).isTrue();
        }
    }

    @Test
    void tryClaimBatch_respectsSpareCeiling_neverClaimsMoreThanSpare() {
        Set<Long> claimed = leaseService.tryClaimBatch(STREAM_IDS, "dep-spare", 2);
        assertThat(claimed).as("spare=2 claims exactly 2 of the 5 free candidates").hasSize(2);
    }

    @Test
    void tryClaimBatch_zeroSpare_claimsNothing_noRedisSideEffect() {
        Set<Long> claimed = leaseService.tryClaimBatch(STREAM_IDS, "dep-zero", 0);
        assertThat(claimed).isEmpty();
        for (Long id : STREAM_IDS) {
            assertThat(redisTemplate.opsForValue().get("stream_lease:" + id)).isNull();
        }
    }

    @Test
    void tryClaimBatch_alreadyLeasedCandidate_isSkippedWithoutStealingItOrConsumingASlot() {
        assertThat(leaseService.tryClaim(STREAM_IDS.get(0), "dep-owner")).isTrue();

        // Candidate list includes the already-leased stream first; spare=1 should still land on
        // the next free candidate rather than stopping after the (failed) first attempt.
        Set<Long> claimed = leaseService.tryClaimBatch(STREAM_IDS, "dep-challenger", 1);

        assertThat(claimed).hasSize(1);
        assertThat(claimed).doesNotContain(STREAM_IDS.get(0));
        assertThat(leaseService.isOwnedBy(STREAM_IDS.get(0), "dep-owner"))
                .as("an already-leased stream is never stolen by a batch claim")
                .isTrue();
    }

    @Test
    void tryClaimBatch_concurrentOverlappingCandidates_onlyOneWinnerPerStream() throws Exception {
        List<Long> contested = List.of(STREAM_IDS.get(0));

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        try {
            Callable<Set<Long>> raceA = () -> {
                ready.countDown();
                go.await(5, TimeUnit.SECONDS);
                return leaseService.tryClaimBatch(contested, "dep-race-a", 1);
            };
            Callable<Set<Long>> raceB = () -> {
                ready.countDown();
                go.await(5, TimeUnit.SECONDS);
                return leaseService.tryClaimBatch(contested, "dep-race-b", 1);
            };
            Future<Set<Long>> futureA = pool.submit(raceA);
            Future<Set<Long>> futureB = pool.submit(raceB);
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            go.countDown();

            Set<Long> winnersA = futureA.get(5, TimeUnit.SECONDS);
            Set<Long> winnersB = futureB.get(5, TimeUnit.SECONDS);

            assertThat(winnersA.size() + winnersB.size())
                    .as("batched SET NX is still atomic per key: exactly one racer wins")
                    .isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    // --- renewBatch: equivalence with sequential renew ------------------------

    @Test
    void renewBatch_ownedCandidates_renewsAllUpToCapacity_sameAsSequential() {
        for (Long id : STREAM_IDS) {
            assertThat(leaseService.tryClaim(id, "dep-owner")).isTrue();
        }

        Set<Long> renewed = leaseService.renewBatch(STREAM_IDS, "dep-owner", STREAM_IDS.size());

        assertThat(renewed).as("every owned candidate is renewed when capacity covers all of them")
                .containsExactlyInAnyOrderElementsOf(STREAM_IDS);
    }

    @Test
    void renewBatch_respectsCapacityCeiling_neverRenewsMoreThanCapacity() {
        for (Long id : STREAM_IDS) {
            assertThat(leaseService.tryClaim(id, "dep-owner")).isTrue();
        }

        Set<Long> renewed = leaseService.renewBatch(STREAM_IDS, "dep-owner", 2);

        assertThat(renewed).as("capacity=2 renews exactly 2 of the 5 owned candidates").hasSize(2);
    }

    @Test
    void renewBatch_candidateOwnedByAnotherDeployment_isNotRenewed_leavesOtherOwnerIntact() {
        assertThat(leaseService.tryClaim(STREAM_IDS.get(0), "dep-other")).isTrue();
        assertThat(leaseService.tryClaim(STREAM_IDS.get(1), "dep-mine")).isTrue();

        Set<Long> renewed = leaseService.renewBatch(
                List.of(STREAM_IDS.get(0), STREAM_IDS.get(1)), "dep-mine", 2);

        assertThat(renewed)
                .as("renew never steals another deployment's lease, even when batched")
                .containsExactly(STREAM_IDS.get(1));
        assertThat(leaseService.isOwnedBy(STREAM_IDS.get(0), "dep-other"))
                .as("the other deployment's lease is left untouched")
                .isTrue();
    }

    @Test
    void renewBatch_zeroCapacity_renewsNothing() {
        assertThat(leaseService.tryClaim(STREAM_IDS.get(0), "dep-owner")).isTrue();

        Set<Long> renewed = leaseService.renewBatch(STREAM_IDS, "dep-owner", 0);

        assertThat(renewed).isEmpty();
    }
}
