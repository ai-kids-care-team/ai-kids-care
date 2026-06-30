package com.ai_kids_care.v1.detection;

import com.ai_kids_care.BaseIntegrationTest;
import com.ai_kids_care.v1.entity.DetectionEvent;
import com.ai_kids_care.v1.mapper.DetectionEventMapper;
import com.ai_kids_care.v1.repository.DetectionEventRepository;
import com.ai_kids_care.v1.vo.DetectionEventVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Regression guard for PRF-06: the three {@link DetectionEventRepository} read paths consumed by the
 * dashboard / SSE push / SSE replay must NOT issue one query per row to resolve the LAZY
 * {@code @ManyToOne} associations that {@link DetectionEventMapper#toVO} dereferences
 * ({@code kindergarten.name}, {@code cctvCameras.cameraName}, {@code rooms.name}). Before the
 * {@code @EntityGraph} fix this was an N+1 (LIST/REPLAY about 1 + up to 3*N SQL); after the fix each
 * path loads in a constant number of statements regardless of N.
 *
 * <p>SQL count is measured with Hibernate {@link Statistics#getPrepareStatementCount()} (JDBC
 * round-trips), enabled at runtime on the {@link SessionFactory}. Each repository call plus the VO
 * mapping (which is what actually triggers the lazy loads) runs inside one transaction via
 * {@link TransactionTemplate}, so an unfixed N+1 would inflate the count inside the measured window.
 *
 * <p>The assertion is on <em>growth</em>, not an absolute number: we run each path at a small N and a
 * large N and require the statement count to stay essentially flat (a tiny constant slack absorbs any
 * driver/session bookkeeping), which is the defining property of an N+1 being fixed.
 */
@AutoConfigureMockMvc
class DetectionEventEntityGraphNPlusOneTest extends BaseIntegrationTest {

    private static final String AI_TOKEN = "Bearer test-ai-service-token-not-secret-2026";
    private static final int SMALL_N = 3;
    private static final int LARGE_N = 30;
    /** Constant slack for per-call bookkeeping; an N+1 (at least LARGE_N-SMALL_N extra) blows past this. */
    private static final long SLACK = 2;

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private DetectionEventRepository repository;
    @Autowired private DetectionEventMapper mapper;
    @Autowired private TransactionTemplate txTemplate;
    @Autowired private EntityManagerFactory emf;

    private long kindergartenId;
    private long streamId;
    private long modelId;
    private Statistics stats;

    @BeforeEach
    void setUp() {
        Map<String, Object> row = jdbc.queryForMap("""
                SELECT cs.stream_id AS sid, cs.kindergarten_id AS kg
                FROM camera_streams cs
                JOIN room_camera_assignments rca
                  ON rca.camera_id = cs.camera_id AND rca.kindergarten_id = cs.kindergarten_id
                 AND (rca.end_at IS NULL OR rca.end_at > now())
                LIMIT 1
                """);
        streamId = ((Number) row.get("sid")).longValue();
        kindergartenId = ((Number) row.get("kg")).longValue();
        modelId = jdbc.queryForObject("SELECT model_id FROM ai_models LIMIT 1", Long.class);

        stats = emf.unwrap(SessionFactory.class).getStatistics();
        stats.setStatisticsEnabled(true);
    }

    @Test
    void listPath_doesNotIssueOneQueryPerRow() throws Exception {
        long sessionId = createSession();
        // Two ingests so we can compare the SQL cost of paging SMALL_N vs LARGE_N rows for this KG.
        ingest(sessionId, "eg-list-small-", SMALL_N);
        long smallSql = measure(() -> mapPage(SMALL_N));

        ingest(sessionId, "eg-list-large-", LARGE_N - SMALL_N); // now at least LARGE_N rows exist
        long largeSql = measure(() -> mapPage(LARGE_N));

        // Constant-ish: paging 10x more rows must not add ~one SELECT per extra row.
        assertThat(largeSql - smallSql).isLessThanOrEqualTo(SLACK);
    }

    @Test
    void replayPath_doesNotIssueOneQueryPerRow() throws Exception {
        long sessionId = createSession();
        ingest(sessionId, "eg-replay-small-", SMALL_N);
        long smallSql = measure(() -> mapReplay(SMALL_N));

        ingest(sessionId, "eg-replay-large-", LARGE_N - SMALL_N);
        long largeSql = measure(() -> mapReplay(LARGE_N));

        assertThat(largeSql - smallSql).isLessThanOrEqualTo(SLACK);
    }

    @Test
    void getForPushPath_singleEventResolvesAssociationsInOneSelect() throws Exception {
        long sessionId = createSession();
        List<Long> ids = ingest(sessionId, "eg-push-", 1);
        long eventId = ids.get(0);

        long sql = measure(() -> {
            DetectionEvent e = txRead(() ->
                    repository.findByIdAndKindergarten_Id(eventId, kindergartenId).orElseThrow());
            DetectionEventVO vo = mapper.toVO(e);
            assertNonNullAssociations(List.of(vo));
        });

        // base SELECT with the to-one fetch joins = 1 statement; allow a little slack. The point is it
        // is a small constant, never 1 + 3 (one extra per dereferenced association).
        assertThat(sql).isLessThanOrEqualTo(1 + SLACK);
    }

    // -- measured workloads (each runs inside ONE transaction so lazy loads, if any, are counted) --

    private void mapPage(int size) {
        List<DetectionEventVO> vos = txRead(() ->
                repository.findByKindergarten_Id(kindergartenId, PageRequest.of(0, size))
                        .map(mapper::toVO).getContent());
        assertThat(vos).hasSize(size);
        assertNonNullAssociations(vos);
    }

    private void mapReplay(int size) {
        List<DetectionEventVO> vos = txRead(() ->
                repository.findByKindergarten_IdAndIdGreaterThanOrderByIdDesc(kindergartenId, 0L, Limit.of(size))
                        .stream().map(mapper::toVO).toList());
        assertThat(vos).hasSize(size);
        assertNonNullAssociations(vos);
    }

    /** Touch every association field the mapper reads, proving they resolved (no lazy init exception). */
    private void assertNonNullAssociations(List<DetectionEventVO> vos) {
        assertThat(vos).allSatisfy(vo -> {
            assertThat(vo.kindergartenName()).isNotBlank();
            assertThat(vo.cameraName()).isNotBlank();
            assertThat(vo.roomName()).isNotBlank();
            assertThat(vo.sessionId()).isNotNull(); // FK id only -- must not require a session SELECT
        });
    }

    /** Reset the prepared-statement counter, run the workload, return statements issued during it. */
    private long measure(Runnable workload) {
        awaitAsyncQuiescence(); // drain ingest @Async staff-alert side-effects so their SQL (on the same
                                // SessionFactory, another thread) can't inflate this global statement count
        stats.clear();
        long before = stats.getPrepareStatementCount();
        workload.run();
        return stats.getPrepareStatementCount() - before;
    }

    private <T> T txRead(Supplier<T> body) {
        return txTemplate.execute(s -> body.get());
    }

    // awaitAsyncQuiescence() is inherited from BaseIntegrationTest (shared by the SSE replay test too).

    // -- ingest helpers (mirror DetectionEventStreamReplayTest, real FK-linked rows) ---------------

    private long createSession() throws Exception {
        String body = mockMvc.perform(post("/api/v1/internal/detection-sessions")
                        .header("Authorization", AI_TOKEN).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("streamId", streamId, "modelId", modelId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").exists())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("sessionId").asLong();
    }

    private List<Long> ingest(long sessionId, String dedupPrefix, int count) throws Exception {
        ArrayList<Long> ids = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            Map<String, Object> payload = Map.of(
                    "sessionId", sessionId,
                    "eventType", "ASSAULT",
                    "severity", 3,
                    "confidence", 0.91,
                    "startTime", OffsetDateTime.now().minusSeconds(30).toString(),
                    "endTime", OffsetDateTime.now().toString(),
                    "dedupKey", dedupPrefix + i,
                    "status", "OPEN");
            String body = mockMvc.perform(post("/api/v1/internal/detection-events")
                            .header("Authorization", AI_TOKEN).contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(payload)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.eventId").exists())
                    .andReturn().getResponse().getContentAsString();
            ids.add(objectMapper.readTree(body).get("eventId").asLong());
        }
        return ids;
    }
}
