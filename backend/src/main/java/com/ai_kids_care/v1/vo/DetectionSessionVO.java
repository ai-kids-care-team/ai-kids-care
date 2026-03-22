package com.ai_kids_care.v1.vo;

import java.io.Serializable;
import java.time.OffsetDateTime;

/**
 * VO for {@link com.ai_kids_care.v1.entity.DetectionSession}
 */
public record DetectionSessionVO(
        Long sessionId,
        Long kindergartenId,
        Long cameraId,
        Long streamId,
        Long modelId,
        OffsetDateTime startedAt,
        OffsetDateTime endedAt,
        String status,
        Integer avgLatencyMs,
        Double inferenceFps
) implements Serializable {
}