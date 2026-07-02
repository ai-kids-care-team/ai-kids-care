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

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration test for detection-event-keyword-search: {@code GET /api/v1/detection-events?keyword=}
 * must actually filter (tenant-scoped, case-insensitive), matching the event-type enum value (camera
 * / room name are also matched per the repository query, but the event-type field is used here because
 * it is fully controlled by the ingest payload — unlike seeded camera/room names — which keeps the
 * cross-tenant assertion independent of seed data content).
 *
 * <p>Two kindergartens (1 and 2, each with a seeded stream/camera/room per {@code db/initdb}) each get
 * a distinctive-eventType event ingested through the real internal ingest API, plus a same-tenant
 * "noise" event of a different type, so the test can assert: (a) the keyword filters within the
 * caller's own tenant, (b) blank keyword preserves the full unfiltered tenant list, and (c) a keyword
 * that matches ONLY the other tenant's data returns nothing for a tenant-A caller (proving the keyword
 * OR-group cannot widen tenant scope — a real escape here would leak kindergarten 2's row into
 * kindergarten 1's result set).
 */
@AutoConfigureMockMvc
class DetectionEventKeywordSearchTest extends BaseIntegrationTest {

    private static final String AI_TOKEN = "Bearer test-ai-service-token-not-secret-2026";
    private static final String PW = "Test@KwSearch2026";
    private static final String ADMIN_A_LOGIN = "kwsearch-admin-a";

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private ObjectMapper objectMapper;

    private long kindergartenAId;
    private long streamAId;
    private long kindergartenBId;
    private long streamBId;
    private long modelId;

    @BeforeEach
    void setUp() {
        List<Map<String, Object>> streams = jdbc.queryForList("""
                SELECT DISTINCT ON (cs.kindergarten_id) cs.stream_id AS sid, cs.kindergarten_id AS kg
                FROM camera_streams cs
                JOIN room_camera_assignments rca
                  ON rca.camera_id = cs.camera_id AND rca.kindergarten_id = cs.kindergarten_id
                 AND (rca.end_at IS NULL OR rca.end_at > now())
                ORDER BY cs.kindergarten_id, cs.stream_id
                LIMIT 2
                """);
        assertThat(streams).hasSizeGreaterThanOrEqualTo(2);
        kindergartenAId = ((Number) streams.get(0).get("kg")).longValue();
        streamAId = ((Number) streams.get(0).get("sid")).longValue();
        kindergartenBId = ((Number) streams.get(1).get("kg")).longValue();
        streamBId = ((Number) streams.get(1).get("sid")).longValue();
        modelId = jdbc.queryForObject("SELECT model_id FROM ai_models LIMIT 1", Long.class);

        seedTenantUser(ADMIN_A_LOGIN, "kwsearch-admin-a@test.local", "010-0000-7001", "KINDERGARTEN_ADMIN", kindergartenAId);
    }

    @Test
    void keyword_filtersWithinTenant_caseInsensitive() throws Exception {
        long sessionA = createSession(streamAId);
        long noiseId = ingestEvent(sessionA, "ASSAULT", "kw-a-noise");
        long targetId = ingestEvent(sessionA, "SWOON", "kw-a-target");

        Cookie adminA = login(ADMIN_A_LOGIN);
        // mixed-case keyword against the DB enum value "SWOON" proves case-insensitivity.
        MvcResult result = mockMvc.perform(get("/api/v1/detection-events")
                        .param("keyword", "swO").param("size", "1000").cookie(adminA))
                .andExpect(status().isOk())
                .andReturn();

        List<Long> ids = extractEventIds(result);
        assertThat(ids).contains(targetId);
        // the ASSAULT noise event must NOT appear for a keyword that only matches SWOON.
        assertThat(ids).doesNotContain(noiseId);
    }

    @Test
    void blankKeyword_returnsFullUnfilteredTenantList() throws Exception {
        long sessionA = createSession(streamAId);
        long noiseId = ingestEvent(sessionA, "ASSAULT", "kw-blank-noise");
        long targetId = ingestEvent(sessionA, "SWOON", "kw-blank-target");

        Cookie adminA = login(ADMIN_A_LOGIN);

        MvcResult withoutKeyword = mockMvc.perform(get("/api/v1/detection-events")
                        .param("size", "1000").cookie(adminA))
                .andExpect(status().isOk()).andReturn();
        MvcResult withBlankKeyword = mockMvc.perform(get("/api/v1/detection-events")
                        .param("keyword", "   ").param("size", "1000").cookie(adminA))
                .andExpect(status().isOk()).andReturn();

        List<Long> idsNoParam = extractEventIds(withoutKeyword);
        List<Long> idsBlank = extractEventIds(withBlankKeyword);

        // Both must contain the same events (no filtering), including both the noise and target rows.
        assertThat(idsNoParam).contains(noiseId, targetId);
        assertThat(idsBlank).containsExactlyInAnyOrderElementsOf(idsNoParam);
    }

    @Test
    void keyword_matchingOnlyOtherTenant_returnsNothingForCaller() throws Exception {
        long sessionA = createSession(streamAId);
        ingestEvent(sessionA, "ASSAULT", "kw-cross-a-noise");

        long sessionB = createSession(streamBId);
        // Distinctive event type that exists ONLY in kindergarten B's data, never in A's.
        ingestEvent(sessionB, "KIDNAP", "kw-cross-b-target");

        Cookie adminA = login(ADMIN_A_LOGIN);
        MvcResult result = mockMvc.perform(get("/api/v1/detection-events")
                        .param("keyword", "kidnap").param("size", "1000").cookie(adminA))
                .andExpect(status().isOk())
                .andReturn();

        // A term that matches only kindergarten B's row must return an EMPTY set for a tenant-A
        // caller -- not merely "excludes B's row", but zero matches, proving the tenant predicate is
        // AND-ed outside the keyword OR-group rather than only filtering after a wider match.
        assertThat(extractEventIds(result)).isEmpty();
    }

    // ── response parsing ─────────────────────────────────────────────────────────

    private List<Long> extractEventIds(MvcResult result) throws Exception {
        var tree = objectMapper.readTree(result.getResponse().getContentAsString());
        List<Long> ids = new java.util.ArrayList<>();
        for (var node : tree.get("content")) {
            ids.add(node.get("eventId").asLong());
        }
        return ids;
    }

    // ── ingest helpers (mirror DetectionEventStreamReplayTest) ───────────────────

    private long createSession(long streamId) throws Exception {
        String body = mockMvc.perform(post("/api/v1/internal/detection-sessions")
                        .header("Authorization", AI_TOKEN).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("streamId", streamId, "modelId", modelId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").exists())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("sessionId").asLong();
    }

    private long ingestEvent(long sessionId, String eventType, String dedupKey) throws Exception {
        Map<String, Object> payload = Map.of(
                "sessionId", sessionId,
                "eventType", eventType,
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
