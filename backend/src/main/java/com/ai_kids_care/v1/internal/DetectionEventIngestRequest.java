package com.ai_kids_care.v1.internal;

import com.ai_kids_care.v1.type.EventStatusEnum;
import com.ai_kids_care.v1.type.EventTypeEnum;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;

/**
 * AI → backend detection-event ingest (on alarm_on). References an existing session; the backend
 * derives kindergarten_id/camera_id from the session and room_id from the camera's active room
 * assignment. {@code dedupKey} is AI-generated; the backend enforces (kindergarten_id, dedup_key)
 * uniqueness idempotently. {@code eventType} is the already-mapped enum (unknown value → 400).
 * {@code status} optional (defaults to OPEN).
 */
public record DetectionEventIngestRequest(
        @NotNull Long sessionId,
        @NotNull EventTypeEnum eventType,
        @NotNull Integer severity,
        @NotNull Double confidence,
        @NotNull OffsetDateTime startTime,
        @NotNull OffsetDateTime endTime,
        @NotBlank String dedupKey,
        EventStatusEnum status
) {
}
