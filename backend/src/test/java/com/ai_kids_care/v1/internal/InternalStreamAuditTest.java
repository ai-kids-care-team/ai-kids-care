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

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SEC-05 (harden-claim-lease-internal C2): a successful internal credential read and a
 * newly-claimed stream lease each write exactly one {@code S1_EVIDENCE_READ} audit record —
 * forensic visibility for the accepted shared-{@code AI_SERVICE_TOKEN} blast-radius risk
 * (OQ-3=B). Renewing an already-held lease is not a new evidence-read event, so steady-state
 * polling writes nothing. The audit payload never carries the plaintext credential or the bearer
 * token (invariant #5).
 */
@AutoConfigureMockMvc
class InternalStreamAuditTest extends BaseIntegrationTest {

    private static final String TOKEN = "Bearer test-ai-service-token-not-secret-2026";
    private static final String CLAIM_PATH = "/api/v1/internal/streams/claim";
    private static final String RAW_TOKEN = "test-ai-service-token-not-secret-2026";

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private StringRedisTemplate redisTemplate;

    private List<Long> allStreamIds;

    @BeforeEach
    void setUp() {
        allStreamIds = jdbc.queryForList("SELECT stream_id FROM camera_streams ORDER BY stream_id", Long.class);
        assertThat(allStreamIds).as("seed has at least one camera stream").isNotEmpty();
        jdbc.update("UPDATE camera_streams SET enabled = true");
        clearLeasesAndAudit();
    }

    @AfterEach
    void tearDown() {
        jdbc.update("UPDATE camera_streams SET enabled = true");
        clearLeasesAndAudit();
    }

    private void clearLeasesAndAudit() {
        for (Long id : allStreamIds) {
            redisTemplate.delete("stream_lease:" + id);
        }
        jdbc.update("DELETE FROM audit_logs WHERE action = 'S1_EVIDENCE_READ'");
    }

    private long claimOneStream(String deploymentId) throws Exception {
        String body = mockMvc.perform(post(CLAIM_PATH)
                        .header("Authorization", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new StreamClaimRequest(deploymentId, 1, List.of()))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("assigned").get(0).get("streamId").asLong();
    }

    private List<Map<String, Object>> evidenceReadRows(String resourceType, long resourceId) {
        return jdbc.queryForList(
                "SELECT * FROM audit_logs WHERE action = 'S1_EVIDENCE_READ' "
                        + "AND resource_type = ? AND resource_id = ?",
                resourceType, resourceId);
    }

    @Test
    void claim_newlyClaimedStream_writesExactlyOneAuditRecord() throws Exception {
        long streamId = claimOneStream("dep-audit-claim");

        List<Map<String, Object>> rows = evidenceReadRows("CAMERA_STREAM_CLAIM", streamId);
        assertThat(rows).as("newly-claimed stream writes exactly one S1_EVIDENCE_READ record").hasSize(1);
        assertThat(rows.get(0).get("result")).isEqualTo("SUCCESS");
        assertThat(rows.get(0).get("effective_role")).isEqualTo("dep-audit-claim");
    }

    @Test
    void claim_renewalOfAlreadyHeldLease_writesNoNewAuditRecord() throws Exception {
        long streamId = claimOneStream("dep-audit-renew");
        assertThat(evidenceReadRows("CAMERA_STREAM_CLAIM", streamId)).hasSize(1);

        // Second poll: same deployment reports the stream as running (renewal, not a new claim).
        mockMvc.perform(post(CLAIM_PATH)
                        .header("Authorization", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new StreamClaimRequest("dep-audit-renew", 1, List.of(streamId)))))
                .andExpect(status().isOk());

        assertThat(evidenceReadRows("CAMERA_STREAM_CLAIM", streamId))
                .as("renewing an already-held lease is not a new evidence-read event")
                .hasSize(1);
    }

    @Test
    void credentials_successfulRead_writesExactlyOneAuditRecordWithoutPlaintextCredentialOrToken() throws Exception {
        long streamId = claimOneStream("dep-audit-cred");

        String body = mockMvc.perform(get("/api/v1/internal/streams/{id}/credentials", streamId)
                        .header("Authorization", TOKEN)
                        .header("X-Deployment-Id", "dep-audit-cred"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode node = objectMapper.readTree(body);
        String plaintextPassword = node.has("streamPassword") && !node.get("streamPassword").isNull()
                ? node.get("streamPassword").asText()
                : null;

        List<Map<String, Object>> rows = evidenceReadRows("CAMERA_STREAM_CREDENTIAL", streamId);
        assertThat(rows).as("successful credential read writes exactly one S1_EVIDENCE_READ record").hasSize(1);
        Map<String, Object> row = rows.get(0);
        assertThat(row.get("result")).isEqualTo("SUCCESS");
        assertThat(row.get("effective_role")).isEqualTo("dep-audit-cred");

        // No column of the audit row carries the plaintext credential or the shared bearer token.
        if (plaintextPassword != null && !plaintextPassword.isBlank()) {
            for (Object value : row.values()) {
                if (value != null) {
                    assertThat(value.toString()).doesNotContain(plaintextPassword);
                }
            }
        }
        for (Object value : row.values()) {
            if (value != null) {
                assertThat(value.toString()).doesNotContain(RAW_TOKEN);
            }
        }
    }

    @Test
    void credentials_deniedRead_writesNoAuditRecord() throws Exception {
        // Lease-ownership mismatch (SEC-05 is scoped to *successful* reads per the frozen spec —
        // this asserts the denied path stays silent, not that it also gets audited).
        long streamId = claimOneStream("dep-audit-owner");

        mockMvc.perform(get("/api/v1/internal/streams/{id}/credentials", streamId)
                        .header("Authorization", TOKEN)
                        .header("X-Deployment-Id", "dep-audit-imposter"))
                .andExpect(status().isNotFound());

        assertThat(evidenceReadRows("CAMERA_STREAM_CREDENTIAL", streamId)).isEmpty();
    }
}
