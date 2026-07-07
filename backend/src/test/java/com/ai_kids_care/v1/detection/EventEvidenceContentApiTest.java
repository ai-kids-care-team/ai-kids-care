package com.ai_kids_care.v1.detection;

import com.ai_kids_care.BaseIntegrationTest;
import com.ai_kids_care.v1.storage.EvidenceObjectStream;
import com.ai_kids_care.v1.storage.EvidenceStoragePort;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityNotFoundException;
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

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP contract tests for D-STORE's backend-proxied evidence content endpoint
 * ({@code GET /api/v1/event_evidence_files/{evidenceId}/content}). {@link EvidenceStoragePort} is
 * mocked — this suite exercises the controller/service contract (auth, tenant scoping, the
 * file://-unavailable and missing-object 404 branches, Range/206) against a fixed byte payload, not
 * the real MinIO wire protocol (covered separately by {@code MinioEvidenceStorageAdapterTest}).
 *
 * <p>All test rows live under a FRESH detection_event created in {@link #setUp()} (not the shared
 * seed event_id=1) so this suite never contends with {@code EventEvidenceListApiTest} over the same
 * event's evidence set.
 */
@AutoConfigureMockMvc
class EventEvidenceContentApiTest extends BaseIntegrationTest {

    private static final String PW = "Test@EvidContent2026";
    private static final String ADMIN_LOGIN = "evidcontent-admin";
    private static final String GUARDIAN_LOGIN = "evidcontent-guardian";
    private static final String FOREIGN_ADMIN_LOGIN = "evidcontent-foreign-admin";

    private static final byte[] HAPPY_BYTES = "hello evidence bytes".getBytes(StandardCharsets.UTF_8);
    private static final String HAPPY_URI = "s3://ai-kids-care/evidence/evidcontent-happy.jpg";
    private static final String FILE_URI = "file:///var/data/legacy/evidcontent-legacy.mp4";
    private static final String MISSING_URI = "s3://ai-kids-care/evidence/evidcontent-missing.jpg";

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private ObjectMapper objectMapper;
    @MockBean private EvidenceStoragePort storagePort;

    private long kindergartenId;
    private long foreignKindergartenId;
    private long evidenceIdHappy;
    private long evidenceIdFileUnavailable;
    private long evidenceIdMissingObject;

    @BeforeEach
    void setUp() {
        Map<String, Object> ev = jdbc.queryForMap("""
                SELECT kindergarten_id AS kg, camera_id AS cam, room_id AS room, session_id AS sess
                FROM detection_events ORDER BY event_id LIMIT 1
                """);
        kindergartenId = ((Number) ev.get("kg")).longValue();
        long cameraId = ((Number) ev.get("cam")).longValue();
        long roomId = ((Number) ev.get("room")).longValue();
        long sessionId = ((Number) ev.get("sess")).longValue();
        foreignKindergartenId = jdbc.queryForObject(
                "SELECT kindergarten_id FROM kindergartens WHERE kindergarten_id <> ? ORDER BY kindergarten_id LIMIT 1",
                Long.class, kindergartenId);

        long eventId = jdbc.queryForObject("""
                INSERT INTO detection_events
                    (kindergarten_id, camera_id, room_id, session_id, event_type, severity, confidence,
                     start_time, end_time, status, dedup_key)
                VALUES (?, ?, ?, ?, 'OTHER', 1, 0.5, NOW(), NOW(), 'OPEN', ?)
                RETURNING event_id
                """, Long.class, kindergartenId, cameraId, roomId, sessionId, "evidcontent-" + System.nanoTime());

        evidenceIdHappy = insertEvidence(eventId, kindergartenId, HAPPY_URI, "image/jpeg", "IMAGE", "happyhash123");
        evidenceIdFileUnavailable = insertEvidence(eventId, kindergartenId, FILE_URI, "video/mp4", "VIDEO", "filehash456");
        evidenceIdMissingObject = insertEvidence(eventId, kindergartenId, MISSING_URI, "image/png", "IMAGE", "missinghash789");

        // NOTE: 010-0000-69xx collides with DetectionEventStreamReplayTest's phone numbers on the
        // shared testcontainer (uq_user_account_phone is global, not per-test-class) — use 87xx instead.
        seedTenantUser(ADMIN_LOGIN, "evidcontent-admin@test.local", "010-0000-8701", "KINDERGARTEN_ADMIN", kindergartenId);
        seedTenantUser(GUARDIAN_LOGIN, "evidcontent-guardian@test.local", "010-0000-8702", "GUARDIAN", kindergartenId);
        seedTenantUser(FOREIGN_ADMIN_LOGIN, "evidcontent-foreign@test.local", "010-0000-8703", "KINDERGARTEN_ADMIN", foreignKindergartenId);

        when(storagePort.isAvailable(HAPPY_URI)).thenReturn(true);
        when(storagePort.isAvailable(FILE_URI)).thenReturn(false); // legacy file:// row
        when(storagePort.isAvailable(MISSING_URI)).thenReturn(true); // scheme resolves; object itself is gone

        when(storagePort.open(eq(HAPPY_URI), isNull())).thenAnswer(invocation -> new EvidenceObjectStream(
                new ByteArrayInputStream(HAPPY_BYTES), HAPPY_BYTES.length, false, 0, HAPPY_BYTES.length - 1, HAPPY_BYTES.length));
        when(storagePort.open(eq(MISSING_URI), isNull()))
                .thenThrow(new EntityNotFoundException("Evidence object not found in storage"));
    }

    private long insertEvidence(long eventId, long kgId, String uri, String mime, String type, String hash) {
        return jdbc.queryForObject("""
                INSERT INTO event_evidence_files (event_id, kindergarten_id, type, storage_uri, mime_type, hold, hash)
                VALUES (?, ?, ?::evidence_file_type_enum, ?, ?::mime_type_enum, false, ?)
                RETURNING evidence_id
                """, Long.class, eventId, kgId, type, uri, mime, hash);
    }

    @Test
    void content_admin_returns200WithBytesAndHeaders() throws Exception {
        Cookie admin = login(ADMIN_LOGIN);
        MvcResult started = mockMvc.perform(get("/api/v1/event_evidence_files/{id}/content", evidenceIdHappy).cookie(admin))
                .andExpect(request().asyncStarted())
                .andReturn();
        mockMvc.perform(asyncDispatch(started))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/jpeg"))
                .andExpect(header().string("ETag", "happyhash123"))
                .andExpect(header().string("Accept-Ranges", "bytes"))
                .andExpect(header().string("Content-Length", String.valueOf(HAPPY_BYTES.length)))
                .andExpect(content().bytes(HAPPY_BYTES));
    }

    @Test
    void content_rangeRequest_returns206PartialContent() throws Exception {
        byte[] partial = new byte[]{HAPPY_BYTES[0], HAPPY_BYTES[1], HAPPY_BYTES[2]};
        when(storagePort.open(eq(HAPPY_URI), eq("bytes=0-2"))).thenAnswer(invocation -> new EvidenceObjectStream(
                new ByteArrayInputStream(partial), partial.length, true, 0, 2, HAPPY_BYTES.length));

        Cookie admin = login(ADMIN_LOGIN);
        MvcResult started = mockMvc.perform(get("/api/v1/event_evidence_files/{id}/content", evidenceIdHappy)
                        .header("Range", "bytes=0-2")
                        .cookie(admin))
                .andExpect(request().asyncStarted())
                .andReturn();
        mockMvc.perform(asyncDispatch(started))
                .andExpect(status().isPartialContent())
                .andExpect(header().string("Content-Range", "bytes 0-2/" + HAPPY_BYTES.length))
                .andExpect(header().string("Content-Length", "3"))
                .andExpect(content().bytes(partial));
    }

    @Test
    void content_legacyFileUri_returns404() throws Exception {
        Cookie admin = login(ADMIN_LOGIN);
        mockMvc.perform(get("/api/v1/event_evidence_files/{id}/content", evidenceIdFileUnavailable).cookie(admin))
                .andExpect(status().isNotFound());
    }

    @Test
    void content_missingObject_returns404() throws Exception {
        Cookie admin = login(ADMIN_LOGIN);
        mockMvc.perform(get("/api/v1/event_evidence_files/{id}/content", evidenceIdMissingObject).cookie(admin))
                .andExpect(status().isNotFound());
    }

    @Test
    void content_guardian_returns403() throws Exception {
        Cookie guardian = login(GUARDIAN_LOGIN);
        mockMvc.perform(get("/api/v1/event_evidence_files/{id}/content", evidenceIdHappy).cookie(guardian))
                .andExpect(status().isForbidden());
    }

    @Test
    void content_anonymous_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/event_evidence_files/{id}/content", evidenceIdHappy))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void content_crossTenant_returnsHidden404() throws Exception {
        Cookie foreignAdmin = login(FOREIGN_ADMIN_LOGIN);
        mockMvc.perform(get("/api/v1/event_evidence_files/{id}/content", evidenceIdHappy).cookie(foreignAdmin))
                .andExpect(status().isNotFound());
    }

    @Test
    void content_nonexistentEvidence_returnsHidden404() throws Exception {
        Cookie admin = login(ADMIN_LOGIN);
        mockMvc.perform(get("/api/v1/event_evidence_files/{id}/content", 9_999_999L).cookie(admin))
                .andExpect(status().isNotFound());
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
