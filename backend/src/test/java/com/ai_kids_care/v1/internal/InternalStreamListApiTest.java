package com.ai_kids_care.v1.internal;

import com.ai_kids_care.BaseIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP contract tests for the internal active-stream registry endpoint (Scheme A):
 * {@code GET /api/v1/internal/streams}. Auth is ROLE_AI_SERVICE (Bearer AI_SERVICE_TOKEN), enforced
 * at the HTTP layer by the existing {@code hasRole("AI_SERVICE")} rule on {@code /api/v1/internal/**}
 * (no SecurityConfig change). Asserts: the AI token can read; only {@code enabled = true} streams are
 * returned (active predicate is server-side, in the JPQL — never client-supplied); each element
 * carries {@code streamId/modelId/kindergartenId} and NO credentials; missing/wrong token → 401/403.
 */
@AutoConfigureMockMvc
class InternalStreamListApiTest extends BaseIntegrationTest {

    private static final String TOKEN = "Bearer test-ai-service-token-not-secret-2026";

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper objectMapper;

    private long streamId;
    private long kindergartenId;

    @BeforeEach
    void setUp() {
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT stream_id AS sid, kindergarten_id AS kg FROM camera_streams ORDER BY stream_id LIMIT 1");
        streamId = ((Number) row.get("sid")).longValue();
        kindergartenId = ((Number) row.get("kg")).longValue();
        // Ensure the probe stream is active for the active-only assertions (idempotent).
        jdbc.update("UPDATE camera_streams SET enabled = true WHERE stream_id = ?", streamId);
    }

    @AfterEach
    void restoreSeed() {
        // Re-enable the probe stream so the disabled-exclusion test does not leak into sibling suites
        // that share this testcontainer's seed.
        jdbc.update("UPDATE camera_streams SET enabled = true WHERE stream_id = ?", streamId);
    }

    private List<JsonNode> getStreams() throws Exception {
        String body = mockMvc.perform(get("/api/v1/internal/streams").header("Authorization", TOKEN))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode arr = objectMapper.readTree(body);
        List<JsonNode> out = new ArrayList<>();
        arr.forEach(out::add);
        return out;
    }

    @Test
    void listActiveStreams_withAiToken_returnsEnabledStreamWithTenantAndModel() throws Exception {
        List<JsonNode> streams = getStreams();
        assertThat(streams).as("seed has at least one enabled camera stream").isNotEmpty();

        JsonNode mine = streams.stream()
                .filter(n -> n.get("streamId").asLong() == streamId)
                .findFirst().orElseThrow();
        // Tenant attribution is derived server-side (projected from the camera→kindergarten relation).
        assertThat(mine.get("kindergartenId").asLong()).isEqualTo(kindergartenId);
        // modelId is present (may be null when no ACTIVE ai_model exists); never a credential field.
        assertThat(mine.has("modelId")).isTrue();
        assertThat(mine.has("streamPassword")).isFalse();
        assertThat(mine.has("sourceUrl")).isFalse();

        // Active predicate is in the query: every returned stream is enabled = true in the DB.
        for (JsonNode n : streams) {
            Boolean enabled = jdbc.queryForObject(
                    "SELECT enabled FROM camera_streams WHERE stream_id = ?", Boolean.class,
                    n.get("streamId").asLong());
            assertThat(enabled).as("only enabled streams are listed").isTrue();
        }
    }

    @Test
    void listActiveStreams_excludesDisabledStreams() throws Exception {
        jdbc.update("UPDATE camera_streams SET enabled = false WHERE stream_id = ?", streamId);
        List<JsonNode> streams = getStreams();
        assertThat(streams.stream().anyMatch(n -> n.get("streamId").asLong() == streamId))
                .as("a disabled stream is excluded from the active list").isFalse();
    }

    @Test
    void listActiveStreams_withoutToken_isRejected() throws Exception {
        mockMvc.perform(get("/api/v1/internal/streams"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void listActiveStreams_withWrongToken_isRejected() throws Exception {
        mockMvc.perform(get("/api/v1/internal/streams").header("Authorization", "Bearer not-the-token"))
                .andExpect(status().is4xxClientError());
    }
}
