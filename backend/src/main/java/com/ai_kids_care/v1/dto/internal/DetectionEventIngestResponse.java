package com.ai_kids_care.v1.dto.internal;

/**
 * Response for a detection-event ingest: the event id, and whether this was a duplicate
 * (idempotent hit on an existing (kindergarten_id, dedup_key)).
 */
public record DetectionEventIngestResponse(Long eventId, boolean duplicate) {
}
