package com.ai_kids_care.v1.event;

import com.ai_kids_care.v1.type.EventStatusEnum;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Published by EventReviewService.confirm within the review transaction. Carries the event's
 * room + detected_at (resolved while the entity is still managed) so the AFTER_COMMIT guardian-
 * notification listener never touches a lazy association. Step ③ closed-loop trigger.
 */
public record EventReviewedEvent(
        Long eventId,
        Long kindergartenId,
        EventStatusEnum resultStatus,
        Long roomId,
        OffsetDateTime detectedAt,
        List<Long> affectedChildIds,
        Boolean notifyGuardians) {
}
