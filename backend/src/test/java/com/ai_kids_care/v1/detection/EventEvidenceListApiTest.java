package com.ai_kids_care.v1.detection;

import com.ai_kids_care.BaseIntegrationTest;
import com.ai_kids_care.v1.storage.EvidenceStoragePort;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.Map;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP contract tests for D-STORE's evidence-list endpoint
 * ({@code GET /api/v1/detection-events/{eventId}/evidence}). Mirrors
 * {@link DetectionEventReadApiTest}'s staff+tenant policy fixture shape. {@link EvidenceStoragePort}
 * is mocked (no real MinIO needed) — this suite only exercises auth/tenant scoping and the VO shape
 * (contentPath/available), not the real object-storage round trip (covered by
 * {@code MinioEvidenceStorageAdapterTest} and {@code EventEvidenceContentApiTest}).
 */
@AutoConfigureMockMvc
class EventEvidenceListApiTest extends BaseIntegrationTest {

    private static final String PW = "Test@EvidList2026";
    private static final String ADMIN_LOGIN = "evidlist-admin";
    private static final String TEACHER_LOGIN = "evidlist-teacher";
    private static final String GUARDIAN_LOGIN = "evidlist-guardian";
    private static final String FOREIGN_ADMIN_LOGIN = "evidlist-foreign-admin";

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private ObjectMapper objectMapper;
    @MockBean private EvidenceStoragePort storagePort;

    private long eventIdWithEvidence;
    private long eventIdNoEvidence;
    private long kindergartenId;
    private long foreignKindergartenId;
    private long seededEvidenceId;
    private String seededStorageUri;

    @BeforeEach
    void setUp() {
        Map<String, Object> ev = jdbc.queryForMap("""
                SELECT event_id AS eid, kindergarten_id AS kg, camera_id AS cam, room_id AS room, session_id AS sess
                FROM detection_events ORDER BY event_id LIMIT 1
                """);
        eventIdWithEvidence = ((Number) ev.get("eid")).longValue();
        kindergartenId = ((Number) ev.get("kg")).longValue();
        long cameraId = ((Number) ev.get("cam")).longValue();
        long roomId = ((Number) ev.get("room")).longValue();
        long sessionId = ((Number) ev.get("sess")).longValue();
        foreignKindergartenId = jdbc.queryForObject(
                "SELECT kindergarten_id FROM kindergartens WHERE kindergarten_id <> ? ORDER BY kindergarten_id LIMIT 1",
                Long.class, kindergartenId);

        Map<String, Object> evidence = jdbc.queryForMap(
                "SELECT evidence_id AS eid, storage_uri AS uri FROM event_evidence_files WHERE event_id = ? ORDER BY evidence_id LIMIT 1",
                eventIdWithEvidence);
        seededEvidenceId = ((Number) evidence.get("eid")).longValue();
        seededStorageUri = (String) evidence.get("uri");

        // A second event in the SAME tenant with zero evidence rows: proves the hidden-404 gate is
        // keyed on event ownership, not "has evidence" — a legit empty-evidence event must 200 + [],
        // not 404 (which would let a caller distinguish "not mine" from "mine, no evidence yet").
        eventIdNoEvidence = jdbc.queryForObject("""
                INSERT INTO detection_events
                    (kindergarten_id, camera_id, room_id, session_id, event_type, severity, confidence,
                     start_time, end_time, status, dedup_key)
                VALUES (?, ?, ?, ?, 'OTHER', 1, 0.5, NOW(), NOW(), 'OPEN', ?)
                RETURNING event_id
                """, Long.class, kindergartenId, cameraId, roomId, sessionId, "evidlist-empty-" + System.nanoTime());

        seedTenantUser(ADMIN_LOGIN, "evidlist-admin@test.local", "010-0000-7101", "KINDERGARTEN_ADMIN", kindergartenId);
        seedTenantUser(TEACHER_LOGIN, "evidlist-teacher@test.local", "010-0000-7102", "TEACHER", kindergartenId);
        seedTenantUser(GUARDIAN_LOGIN, "evidlist-guardian@test.local", "010-0000-7103", "GUARDIAN", kindergartenId);
        seedTenantUser(FOREIGN_ADMIN_LOGIN, "evidlist-foreign@test.local", "010-0000-7104", "KINDERGARTEN_ADMIN", foreignKindergartenId);

        // Default: every storage_uri is "available" unless a test overrides it — the seeded evidence
        // row's storage_uri is s3://ai-kids-care/evidence/seed-event-001.jpg (scheme-resolvable).
        when(storagePort.isAvailable(anyString())).thenReturn(true);
    }

    @Test
    void list_admin_returnsSeededEvidenceWithContentPathAndAvailable() throws Exception {
        Cookie admin = login(ADMIN_LOGIN);
        // Index [0]: this event's evidence set is exactly the one seed row — no other test class
        // writes evidence under THIS event (the content-endpoint suite uses its own freshly-created
        // event), so a definite index is safe here (unlike a filter+field jsonpath, whose evaluated
        // type for an indefinite match is a List and doesn't compare cleanly against a scalar).
        mockMvc.perform(get("/api/v1/detection-events/{id}/evidence", eventIdWithEvidence).cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].evidenceId").value((int) seededEvidenceId))
                .andExpect(jsonPath("$[0].eventId").value((int) eventIdWithEvidence))
                .andExpect(jsonPath("$[0].type").value("IMAGE"))
                .andExpect(jsonPath("$[0].mimeType").value("image/jpeg"))
                .andExpect(jsonPath("$[0].hash").value("f1dae30a6a02baa4e97835b19fb01e2d"))
                .andExpect(jsonPath("$[0].available").value(true))
                .andExpect(jsonPath("$[0].contentPath")
                        .value("/api/v1/event_evidence_files/" + seededEvidenceId + "/content"));
    }

    @Test
    void list_unavailableEvidence_hasNullContentPath() throws Exception {
        // A legacy file:// row (or any dangling object) -> isAvailable() false -> contentPath null,
        // even though the row itself is still listed (metadata, not gated on readability).
        when(storagePort.isAvailable(seededStorageUri)).thenReturn(false);
        Cookie admin = login(ADMIN_LOGIN);
        mockMvc.perform(get("/api/v1/detection-events/{id}/evidence", eventIdWithEvidence).cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].available").value(false))
                .andExpect(jsonPath("$[0].contentPath").doesNotExist());
    }

    @Test
    void list_teacher_returns200() throws Exception {
        Cookie teacher = login(TEACHER_LOGIN);
        mockMvc.perform(get("/api/v1/detection-events/{id}/evidence", eventIdWithEvidence).cookie(teacher))
                .andExpect(status().isOk());
    }

    @Test
    void list_guardian_returns403() throws Exception {
        Cookie guardian = login(GUARDIAN_LOGIN);
        mockMvc.perform(get("/api/v1/detection-events/{id}/evidence", eventIdWithEvidence).cookie(guardian))
                .andExpect(status().isForbidden());
    }

    @Test
    void list_anonymous_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/detection-events/{id}/evidence", eventIdWithEvidence))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void list_crossTenant_returnsHidden404() throws Exception {
        Cookie foreignAdmin = login(FOREIGN_ADMIN_LOGIN);
        mockMvc.perform(get("/api/v1/detection-events/{id}/evidence", eventIdWithEvidence).cookie(foreignAdmin))
                .andExpect(status().isNotFound());
    }

    @Test
    void list_nonexistentEvent_returnsHidden404() throws Exception {
        Cookie admin = login(ADMIN_LOGIN);
        mockMvc.perform(get("/api/v1/detection-events/{id}/evidence", 9_999_999L).cookie(admin))
                .andExpect(status().isNotFound());
    }

    @Test
    void list_eventWithNoEvidence_returns200EmptyArray() throws Exception {
        Cookie admin = login(ADMIN_LOGIN);
        mockMvc.perform(get("/api/v1/detection-events/{id}/evidence", eventIdNoEvidence).cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }

    // ── helpers (mirror DetectionEventReadApiTest) ──────────────────────────────

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
