package com.ai_kids_care.v1.vo;

import java.io.Serializable;
import java.time.OffsetDateTime;

/**
 * VO for {@link com.ai_kids_care.v1.entity.DetectionEvent}
 */
public record DetectionEventVO(
        Long eventId,
        Long kindergartenId,
        Long cameraId,
        Long roomId,
        Long sessionId,
        String eventType,
        Integer severity,
        Double confidence,
        OffsetDateTime detectedAt,
        OffsetDateTime startTime,
        OffsetDateTime endTime,
        String status,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) implements Serializable {
}