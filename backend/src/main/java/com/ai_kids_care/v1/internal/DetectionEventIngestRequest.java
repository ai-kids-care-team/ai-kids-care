package com.ai_kids_care.v1.internal;

import com.ai_kids_care.v1.type.EventStatusEnum;
import com.ai_kids_care.v1.type.EventTypeEnum;
import com.ai_kids_care.v1.type.EvidenceFileTypeEnum;
import com.ai_kids_care.v1.type.MimeTypeEnum;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;

/**
 * AI → backend detection-event ingest (on alarm_on). References an existing session; the backend
 * derives kindergarten_id/camera_id from the session and room_id from the camera's active room
 * assignment. {@code dedupKey} is AI-generated; the backend enforces (kindergarten_id, dedup_key)
 * uniqueness idempotently. {@code eventType} is the already-mapped enum (unknown value → 400).
 * {@code status} optional (defaults to OPEN).
 *
 * <p>{@code evidence} is optional: the AI does not yet send it, so a request without it persists the
 * event exactly as before with no evidence row. When supplied it is all-or-nothing — {@code @Valid}
 * cascades into {@link EvidenceFile} so all inner fields are required, otherwise the request is 400.
 */
public record DetectionEventIngestRequest(
        @NotNull Long sessionId,
        @NotNull EventTypeEnum eventType,
        @NotNull Integer severity,
        @NotNull Double confidence,
        @NotNull OffsetDateTime startTime,
        @NotNull OffsetDateTime endTime,
        @NotBlank String dedupKey,
        EventStatusEnum status,
        @Valid EvidenceFile evidence
) {

    /**
     * Optional single evidence descriptor for the detection event. The backend records it as one
     * {@code event_evidence_files} row (storage_uri = uri, mime_type = mimeType). All fields are
     * required when the descriptor is present (all-or-nothing). Unknown {@code type}/{@code mimeType}
     * enum values are rejected with 400 during Jackson deserialization.
     */
    public record EvidenceFile(
            @NotBlank String uri,
            @NotBlank String hash,
            @NotNull EvidenceFileTypeEnum type,
            @NotNull MimeTypeEnum mimeType
    ) {
    }
}
