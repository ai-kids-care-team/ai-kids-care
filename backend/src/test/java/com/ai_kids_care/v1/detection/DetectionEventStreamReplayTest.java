package com.ai_kids_care.v1.detection;

import com.ai_kids_care.BaseIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration test for SSE reconnect replay via {@code Last-Event-ID} on
 * {@code GET /api/v1/detection-events/stream}. Real detection events are ingested through the
 * internal ingest API (so event_id / dedup_key / FK columns are written exactly as in production),
 * then the stream is opened with a {@code Last-Event-ID} and the synchronously-written replay frames
 * are read from the response buffer.
 *
 * <p>Verifies: only this kindergarten's events with {@code event_id > Last-Event-ID} are replayed,
 * ascending, with {@code id:} == event_id; a foreign kindergarten's event is never replayed
 * (tenant scope); missing / non-numeric header → no replay; a window exceeding {@code replay-max}
 * (= 5 in the test profile) replays only the most-recent max.
 */
@AutoConfigureMockMvc
class DetectionEventStreamReplayTest extends BaseIntegrationTest {

    private static final String AI_TOKEN = "Bearer test-ai-service-token-not-secret-2026";
    private static final String PW = "Test@Replay2026";
    private static final String ADMIN_LOGIN = "replay-admin";
    private static final String FOREIGN_ADMIN_LOGIN = "replay-foreign-admin";
    /** Mirrors detection.sse.replay-max in application-test.yml. */
    private static final int REPLAY_MAX = 5;

    private static final Pattern ID_LINE = Pattern.compile("^id:(\\d+)$", Pattern.MULTILINE);

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private ObjectMapper objectMapper;

    private long kindergartenId;
    private long streamId;
    private long modelId;
    private long foreignKindergartenId;

    @BeforeEach
    void setUp() {
        // A stream whose camera has an active room assignment (ingest requires it), plus its KG.
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
        foreignKindergartenId = jdbc.queryForObject(
                "SELECT kindergarten_id FROM kindergartens WHERE kindergarten_id <> ? ORDER BY kindergarten_id LIMIT 1",
                Long.class, kindergartenId);

        seedTenantUser(ADMIN_LOGIN, "replay-admin@test.local", "010-0000-6901", "KINDERGARTEN_ADMIN", kindergartenId);
        seedTenantUser(FOREIGN_ADMIN_LOGIN, "replay-foreign@test.local", "010-0000-6902", "KINDERGARTEN_ADMIN", foreignKindergartenId);
    }

    @Test
    void reconnect_replaysOnlyMissedEventsOfOwnKindergartenAscending() throws Exception {
        long sessionId = createSession();
        long e1 = ingestEvent(sessionId, "replay-asc-1");
        long e2 = ingestEvent(sessionId, "replay-asc-2");
        long e3 = ingestEvent(sessionId, "replay-asc-3");

        // Reconnect presenting Last-Event-ID = e1 → expect replay of e2, e3 (ascending), not e1.
        List<Long> replayed = replayedIds(login(ADMIN_LOGIN), String.valueOf(e1));

        assertThat(replayed).contains(e2, e3);
        assertThat(replayed).doesNotContain(e1);
        // ascending order: index of e2 before e3
        assertThat(replayed.indexOf(e2)).isLessThan(replayed.indexOf(e3));
    }

    @Test
    void reconnect_isTenantScoped_foreignEventNeverReplayed() throws Exception {
        long sessionId = createSession();
        long mine = ingestEvent(sessionId, "replay-tenant-mine");

        // Foreign admin reconnects with Last-Event-ID = 0 → must NOT see this kindergarten's event,
        // regardless of the numeric lower bound.
        List<Long> replayed = replayedIds(login(FOREIGN_ADMIN_LOGIN), "0");

        assertThat(replayed).doesNotContain(mine);
    }

    @Test
    void noHeader_replaysNothing_behavesAsBefore() throws Exception {
        long sessionId = createSession();
        ingestEvent(sessionId, "replay-none-1");

        MvcResult result = openStream(login(ADMIN_LOGIN), null);
        String body = awaitBody(result);

        assertThat(idsIn(body)).isEmpty();
        assertThat(body).contains("connected"); // pre-existing connect behaviour preserved
    }

    @Test
    void nonNumericHeader_replaysNothing() throws Exception {
        long sessionId = createSession();
        ingestEvent(sessionId, "replay-nonnum-1");

        MvcResult result = openStream(login(ADMIN_LOGIN), "not-a-number");
        String body = awaitBody(result);

        assertThat(idsIn(body)).isEmpty();
        assertThat(body).contains("connected");
    }

    @Test
    void windowExceedingMax_replaysOnlyMostRecentMax() throws Exception {
        long sessionId = createSession();
        List<Long> ids = new ArrayList<>();
        int total = REPLAY_MAX + 3; // 8 events; bound is 5
        for (int i = 0; i < total; i++) {
            ids.add(ingestEvent(sessionId, "replay-max-" + i));
        }
        long firstId = ids.get(0);

        // Reconnect with Last-Event-ID just below the first of this burst → 8 missed, bound 5.
        List<Long> replayed = replayedIds(login(ADMIN_LOGIN), String.valueOf(firstId - 1));

        // Among this burst, only the most-recent REPLAY_MAX should appear; the oldest 3 must not.
        List<Long> mostRecent = ids.subList(total - REPLAY_MAX, total);
        List<Long> oldest = ids.subList(0, total - REPLAY_MAX);
        assertThat(replayed).containsAll(mostRecent);
        assertThat(replayed).doesNotContainAnyElementsOf(oldest);
        // never more than the bound
        assertThat(replayed.size()).isLessThanOrEqualTo(REPLAY_MAX);
    }

    // ── replay-frame reading ─────────────────────────────────────────────────────

    private List<Long> replayedIds(Cookie cookie, String lastEventId) throws Exception {
        return idsIn(awaitBody(openStream(cookie, lastEventId)));
    }

    /** Open the SSE stream (async). Replay + connected frames are written in the request thread. */
    private MvcResult openStream(Cookie cookie, String lastEventId) throws Exception {
        MockHttpServletRequestBuilder req = get("/api/v1/detection-events/stream").cookie(cookie);
        if (lastEventId != null) {
            req = req.header("Last-Event-ID", lastEventId);
        }
        return mockMvc.perform(req).andExpect(request().asyncStarted()).andReturn();
    }

    /**
     * The emitter stays open (30-min timeout) so the async result never "completes"; the replay and
     * connected frames are flushed synchronously to the response buffer during the request thread,
     * so poll the buffered content until the terminal {@code connected} frame is visible.
     */
    private String awaitBody(MvcResult result) {
        await().atMost(Duration.ofSeconds(10)).until(() -> bodyOf(result).contains("connected"));
        return bodyOf(result);
    }

    private static String bodyOf(MvcResult result) {
        try {
            return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        } catch (java.io.UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    private List<Long> idsIn(String body) {
        List<Long> ids = new ArrayList<>();
        Matcher m = ID_LINE.matcher(body);
        while (m.find()) {
            ids.add(Long.parseLong(m.group(1)));
        }
        return ids;
    }

    // ── ingest helpers ───────────────────────────────────────────────────────────

    private long createSession() throws Exception {
        String body = mockMvc.perform(post("/api/v1/internal/detection-sessions")
                        .header("Authorization", AI_TOKEN).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("streamId", streamId, "modelId", modelId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").exists())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("sessionId").asLong();
    }

    private long ingestEvent(long sessionId, String dedupKey) throws Exception {
        Map<String, Object> payload = Map.of(
                "sessionId", sessionId,
                "eventType", "ASSAULT",
                "severity", 3,
                "confidence", 0.91,
                "startTime", OffsetDateTime.now().minusSeconds(30).toString(),
                "endTime", OffsetDateTime.now().toString(),
                "dedupKey", dedupKey,
                "status", "OPEN");
        String body = mockMvc.perform(post("/api/v1/internal/detection-events")
                        .header("Authorization", AI_TOKEN).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventId").exists())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("eventId").asLong();
    }

    // ── auth helpers (mirror DetectionEventReadApiTest) ──────────────────────────

    private Cookie login(String loginId) throws Exception {
        MvcResult login = mockMvc.perform(withCsrf(post("/api/v1/auth/login"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("identifier", loginId, "password", PW))))
                .andExpect(status().isOk())
                .andReturn();
        return login.getResponse().getCookie("AI_KIDS_CARE_SESSION");
    }

    private MockHttpServletRequestBuilder withCsrf(MockHttpServletRequestBuilder request) throws Exception {
        MvcResult csrf = mockMvc.perform(get("/api/v1/auth/csrf")).andExpect(status().isOk()).andReturn();
        String token = objectMapper.readTree(csrf.getResponse().getContentAsString()).get("token").asText();
        return request.cookie(csrf.getResponse().getCookie("XSRF-TOKEN")).header("X-XSRF-TOKEN", token);
    }

    private void seedTenantUser(String loginId, String email, String phone, String role, long kgId) {
        jdbc.update("""
                INSERT INTO users (login_id, email, phone, password_hash, status, created_at, updated_at)
                VALUES (?, ?, ?, ?, 'ACTIVE', NOW(), NOW())
                ON CONFLICT (login_id) DO UPDATE SET password_hash = EXCLUDED.password_hash, status = 'ACTIVE'
                """, loginId, email, phone, passwordEncoder.encode(PW));
        jdbc.update("DELETE FROM user_role_assignments WHERE user_id = (SELECT user_id FROM users WHERE login_id = ?)", loginId);
        jdbc.update("DELETE FROM user_kindergarten_memberships WHERE user_id = (SELECT user_id FROM users WHERE login_id = ?)", loginId);
        jdbc.update("""
                INSERT INTO user_role_assignments (user_id, role, scope_type, scope_id, status, granted_at)
                SELECT user_id, ?::user_role_enum, 'KINDERGARTEN', ?, 'ACTIVE', NOW() FROM users WHERE login_id = ?
                """, role, kgId, loginId);
        jdbc.update("""
                INSERT INTO user_kindergarten_memberships (user_id, kindergarten_id, status, joined_at, created_at, updated_at)
                SELECT user_id, ?, 'ACTIVE', NOW(), NOW(), NOW() FROM users WHERE login_id = ?
                """, kgId, loginId);
    }
}
