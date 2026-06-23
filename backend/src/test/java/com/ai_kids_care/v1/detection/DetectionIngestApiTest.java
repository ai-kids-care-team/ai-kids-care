package com.ai_kids_care.v1.detection;

import com.ai_kids_care.BaseIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP contract tests for the AI → backend detection ingest endpoints + immediate staff alert.
 * Auth is ROLE_AI_SERVICE (Bearer AI_SERVICE_TOKEN). Stream/camera/room/model are discovered from
 * seed data; a known KINDERGARTEN_ADMIN is seeded into the resolved kindergarten to assert the
 * (async) staff alert.
 */
@AutoConfigureMockMvc
class DetectionIngestApiTest extends BaseIntegrationTest {

    private static final String TOKEN = "Bearer test-ai-service-token-not-secret-2026";
    private static final String STAFF_LOGIN = "detect-staff-admin";
    private static final String STAFF_PHONE = "010-0000-7701";

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper objectMapper;

    private long streamId;
    private long modelId;
    private long kindergartenId;
    private long staffUserId;

    @BeforeEach
    void setUp() {
        // Discover a usable stream whose camera has an active room assignment.
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

        // Seed a known ACTIVE KINDERGARTEN_ADMIN in the resolved kindergarten (idempotent).
        jdbc.update("DELETE FROM notifications WHERE recipient_user_id = "
                + "(SELECT user_id FROM users WHERE login_id = ?)", STAFF_LOGIN);
        jdbc.update("DELETE FROM user_role_assignments WHERE user_id = "
                + "(SELECT user_id FROM users WHERE login_id = ?)", STAFF_LOGIN);
        jdbc.update("DELETE FROM users WHERE login_id = ? OR phone = ?", STAFF_LOGIN, STAFF_PHONE);
        jdbc.update("""
                INSERT INTO users (login_id, email, phone, password_hash, status, created_at, updated_at)
                VALUES (?, ?, ?, '$2a$10$x', 'ACTIVE', NOW(), NOW())
                """, STAFF_LOGIN, "detect-staff@test.local", STAFF_PHONE);
        staffUserId = jdbc.queryForObject("SELECT user_id FROM users WHERE login_id = ?", Long.class, STAFF_LOGIN);
        jdbc.update("""
                INSERT INTO user_role_assignments (user_id, role, scope_type, scope_id, status, granted_at)
                VALUES (?, 'KINDERGARTEN_ADMIN', 'KINDERGARTEN', ?, 'ACTIVE', NOW())
                """, staffUserId, kindergartenId);
    }

    private MockHttpServletRequestBuilder ai(MockHttpServletRequestBuilder b) {
        return b.header("Authorization", TOKEN).contentType(MediaType.APPLICATION_JSON);
    }

    private long createSession() throws Exception {
        String body = mockMvc.perform(ai(post("/api/v1/internal/detection-sessions"))
                        .content(objectMapper.writeValueAsString(Map.of("streamId", streamId, "modelId", modelId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").exists())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("sessionId").asLong();
    }

    private Map<String, Object> eventPayload(long sessionId, String dedupKey) {
        return Map.of(
                "sessionId", sessionId,
                "eventType", "ASSAULT",
                "severity", 3,
                "confidence", 0.91,
                "startTime", OffsetDateTime.now().minusSeconds(30).toString(),
                "endTime", OffsetDateTime.now().toString(),
                "dedupKey", dedupKey,
                "status", "OPEN");
    }

    @Test
    void sessionAndEventIngest_persistsAndReturnsIds() throws Exception {
        long sessionId = createSession();
        mockMvc.perform(ai(post("/api/v1/internal/detection-events"))
                        .content(objectMapper.writeValueAsString(eventPayload(sessionId, "dedup-ok-1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventId").exists())
                .andExpect(jsonPath("$.duplicate").value(false));
    }

    @Test
    void duplicateDedupKey_isIdempotent() throws Exception {
        long sessionId = createSession();
        String first = mockMvc.perform(ai(post("/api/v1/internal/detection-events"))
                        .content(objectMapper.writeValueAsString(eventPayload(sessionId, "dedup-same"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        long firstId = objectMapper.readTree(first).get("eventId").asLong();

        mockMvc.perform(ai(post("/api/v1/internal/detection-events"))
                        .content(objectMapper.writeValueAsString(eventPayload(sessionId, "dedup-same"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.duplicate").value(true))
                .andExpect(jsonPath("$.eventId").value(firstId));
    }

    @Test
    void unauthenticatedIngest_isRejected() throws Exception {
        mockMvc.perform(post("/api/v1/internal/detection-sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("streamId", streamId, "modelId", modelId))))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void invalidEventType_returns400() throws Exception {
        long sessionId = createSession();
        mockMvc.perform(ai(post("/api/v1/internal/detection-events"))
                        .content(objectMapper.writeValueAsString(Map.of(
                                "sessionId", sessionId, "eventType", "NOT_A_TYPE", "severity", 1,
                                "confidence", 0.5, "startTime", OffsetDateTime.now().toString(),
                                "endTime", OffsetDateTime.now().toString(), "dedupKey", "dedup-badtype"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void eventIngest_firesStaffAlert() throws Exception {
        long sessionId = createSession();
        String body = mockMvc.perform(ai(post("/api/v1/internal/detection-events"))
                        .content(objectMapper.writeValueAsString(eventPayload(sessionId, "dedup-alert"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        long eventId = objectMapper.readTree(body).get("eventId").asLong();

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            Integer cnt = jdbc.queryForObject(
                    "SELECT count(*) FROM notifications WHERE recipient_user_id = ? AND event_id = ?",
                    Integer.class, staffUserId, eventId);
            assertThat(cnt).as("seeded staff admin gets an in-app staff alert for the event").isGreaterThanOrEqualTo(1);
        });
    }
}
