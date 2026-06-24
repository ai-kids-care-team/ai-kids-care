package com.ai_kids_care.v1.detection;

import com.ai_kids_care.BaseIntegrationTest;
import com.ai_kids_care.v1.event.DetectionEventIngestedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.OffsetDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the ⑥ ingest → SSE trigger: a freshly persisted detection event publishes a
 * {@link DetectionEventIngestedEvent}; a duplicate (idempotent) ingest publishes nothing.
 * Uses {@code @RecordApplicationEvents} to assert the published event without depending on the
 * {@code @Async} SSE listener executing.
 */
@AutoConfigureMockMvc
@RecordApplicationEvents
class DetectionIngestPublishTest extends BaseIntegrationTest {

    private static final String TOKEN = "Bearer test-ai-service-token-not-secret-2026";

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private ApplicationEvents applicationEvents;

    private long streamId;
    private long modelId;

    @BeforeEach
    void setUp() {
        Map<String, Object> row = jdbc.queryForMap("""
                SELECT cs.stream_id AS sid
                FROM camera_streams cs
                JOIN room_camera_assignments rca
                  ON rca.camera_id = cs.camera_id AND rca.kindergarten_id = cs.kindergarten_id
                 AND (rca.end_at IS NULL OR rca.end_at > now())
                LIMIT 1
                """);
        streamId = ((Number) row.get("sid")).longValue();
        modelId = jdbc.queryForObject("SELECT model_id FROM ai_models LIMIT 1", Long.class);
    }

    @Test
    void freshEvent_publishesDetectionEventIngestedEvent() throws Exception {
        long sessionId = createSession();
        long eventId = ingest(sessionId, "dedup-pub-fresh");

        assertThat(applicationEvents.stream(DetectionEventIngestedEvent.class))
                .anyMatch(e -> e.eventId().equals(eventId));
    }

    @Test
    void duplicateEvent_doesNotPublish() throws Exception {
        long sessionId = createSession();
        ingest(sessionId, "dedup-pub-same");      // fresh → publishes once
        ingest(sessionId, "dedup-pub-same");      // duplicate → no publish

        assertThat(applicationEvents.stream(DetectionEventIngestedEvent.class).count())
                .isEqualTo(1);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private MockHttpServletRequestBuilder ai(MockHttpServletRequestBuilder b) {
        return b.header("Authorization", TOKEN).contentType(MediaType.APPLICATION_JSON);
    }

    private long createSession() throws Exception {
        String body = mockMvc.perform(ai(post("/api/v1/internal/detection-sessions"))
                        .content(objectMapper.writeValueAsString(Map.of("streamId", streamId, "modelId", modelId))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("sessionId").asLong();
    }

    private long ingest(long sessionId, String dedupKey) throws Exception {
        String body = mockMvc.perform(ai(post("/api/v1/internal/detection-events"))
                        .content(objectMapper.writeValueAsString(Map.of(
                                "sessionId", sessionId, "eventType", "ASSAULT", "severity", 3,
                                "confidence", 0.91,
                                "startTime", OffsetDateTime.now().minusSeconds(30).toString(),
                                "endTime", OffsetDateTime.now().toString(),
                                "dedupKey", dedupKey, "status", "OPEN"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("eventId").asLong();
    }
}
