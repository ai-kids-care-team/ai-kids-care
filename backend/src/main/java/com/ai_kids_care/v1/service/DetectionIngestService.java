package com.ai_kids_care.v1.service;

import com.ai_kids_care.v1.internal.DetectionEventIngestRequest;
import com.ai_kids_care.v1.internal.DetectionEventIngestResponse;
import com.ai_kids_care.v1.internal.DetectionSessionIngestRequest;
import com.ai_kids_care.v1.internal.DetectionSessionIngestResponse;
import com.ai_kids_care.v1.type.EventStatusEnum;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Backend-owned ingestion of AI detection results. The backend is the sole writer of
 * detection_sessions / detection_events. Rows are written via JdbcTemplate (the read-only JPA
 * entities don't map kindergarten_id/camera_id and refactoring them risks the read path +
 * ddl-auto=validate). kindergarten_id/camera_id are derived from the stream; room_id from the
 * camera's active room assignment. Idempotent on (kindergarten_id, dedup_key). On a fresh event,
 * an immediate staff alert is fired asynchronously.
 *
 * Not @Transactional: each insert autocommits so the @Async staff alert sees the committed event.
 */
@Service
@RequiredArgsConstructor
public class DetectionIngestService {

    private final JdbcTemplate jdbc;
    private final StaffAlertService staffAlertService;

    public DetectionSessionIngestResponse createSession(DetectionSessionIngestRequest req) {
        long[] kgCam = resolveStreamTenant(req.streamId());
        Long sessionId = jdbc.queryForObject("""
                INSERT INTO detection_sessions (kindergarten_id, camera_id, stream_id, model_id, status)
                VALUES (?, ?, ?, ?, 'ACTIVE'::status_enum)
                RETURNING session_id
                """, Long.class, kgCam[0], kgCam[1], req.streamId(), req.modelId());
        return new DetectionSessionIngestResponse(sessionId);
    }

    public DetectionEventIngestResponse ingestEvent(DetectionEventIngestRequest req) {
        long[] kgCam = resolveSessionTenant(req.sessionId());
        long kindergartenId = kgCam[0];
        long cameraId = kgCam[1];
        long roomId = resolveActiveRoom(kindergartenId, cameraId);

        Long existing = findByDedup(kindergartenId, req.dedupKey());
        if (existing != null) {
            return new DetectionEventIngestResponse(existing, true); // idempotent, no re-alert
        }

        EventStatusEnum status = req.status() != null ? req.status() : EventStatusEnum.OPEN;
        Long eventId;
        try {
            eventId = jdbc.queryForObject("""
                    INSERT INTO detection_events
                        (kindergarten_id, camera_id, room_id, session_id, event_type, severity,
                         confidence, start_time, end_time, status, dedup_key)
                    VALUES (?, ?, ?, ?, ?::event_type_enum, ?, ?, ?, ?, ?::event_status_enum, ?)
                    RETURNING event_id
                    """, Long.class,
                    kindergartenId, cameraId, roomId, req.sessionId(), req.eventType().name(),
                    req.severity(), req.confidence(), req.startTime(), req.endTime(),
                    status.name(), req.dedupKey());
        } catch (DuplicateKeyException race) {
            Long raced = findByDedup(kindergartenId, req.dedupKey());
            if (raced == null) {
                throw new IllegalStateException(
                        "dedup uniqueness violated but no existing event found for key: " + req.dedupKey(), race);
            }
            return new DetectionEventIngestResponse(raced, true);
        }

        // event is committed (no surrounding tx) → async staff alert can read it
        staffAlertService.alertForEvent(eventId, kindergartenId, req.eventType().name());
        return new DetectionEventIngestResponse(eventId, false);
    }

    /** stream_id → (kindergarten_id, camera_id). */
    private long[] resolveStreamTenant(Long streamId) {
        try {
            return jdbc.queryForObject(
                    "SELECT kindergarten_id, camera_id FROM camera_streams WHERE stream_id = ?",
                    (rs, n) -> new long[]{rs.getLong(1), rs.getLong(2)}, streamId);
        } catch (EmptyResultDataAccessException e) {
            throw new EntityNotFoundException("camera stream not found: " + streamId);
        }
    }

    /** session_id → (kindergarten_id, camera_id). */
    private long[] resolveSessionTenant(Long sessionId) {
        try {
            return jdbc.queryForObject(
                    "SELECT kindergarten_id, camera_id FROM detection_sessions WHERE session_id = ?",
                    (rs, n) -> new long[]{rs.getLong(1), rs.getLong(2)}, sessionId);
        } catch (EmptyResultDataAccessException e) {
            throw new EntityNotFoundException("detection session not found: " + sessionId);
        }
    }

    /** camera → currently-active room assignment. */
    private long resolveActiveRoom(long kindergartenId, long cameraId) {
        try {
            return jdbc.queryForObject("""
                    SELECT room_id FROM room_camera_assignments
                    WHERE kindergarten_id = ? AND camera_id = ? AND (end_at IS NULL OR end_at > now())
                    ORDER BY start_at DESC
                    LIMIT 1
                    """, Long.class, kindergartenId, cameraId);
        } catch (EmptyResultDataAccessException e) {
            throw new EntityNotFoundException("no active room assignment for camera: " + cameraId);
        }
    }

    private Long findByDedup(long kindergartenId, String dedupKey) {
        return jdbc.query(
                "SELECT event_id FROM detection_events WHERE kindergarten_id = ? AND dedup_key = ?",
                (rs, n) -> rs.getLong(1), kindergartenId, dedupKey)
                .stream().findFirst().orElse(null);
    }
}
