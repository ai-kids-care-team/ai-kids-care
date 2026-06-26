package com.ai_kids_care.v1.event;

/**
 * Published by {@code DetectionIngestService.ingestEvent} after a fresh (non-duplicate) detection
 * event is committed. Carries only the event id + kindergarten id; the SSE listener re-reads the
 * full VO. Ingest is not transactional (autocommit), so this is a plain {@code ApplicationEvent}
 * consumed by an {@code @Async @EventListener} (not {@code @TransactionalEventListener}). ⑥ realtime
 * dashboard trigger.
 */
public record DetectionEventIngestedEvent(
        Long eventId,
        Long kindergartenId) {
}
