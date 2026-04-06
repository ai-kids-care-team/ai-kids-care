package com.ai_kids_care.v1.vo;

import java.io.Serializable;
import java.time.OffsetDateTime;

/**
 * VO for {@link com.ai_kids_care.v1.entity.CameraStream}
 */
public record CameraStreamVO(
        Long streamId,
        Long kindergartenId,
        Long cameraId,
        String streamType,
        String sourceUrl,
        String streamUser,
        Boolean hasPassword,
        String sourceProtocol,
        String playbackUrl,
        String playbackProtocol,
        Integer fps,
        String resolution,
        Boolean isPrimary,
        Boolean enabled,
        String status,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) implements Serializable {
}
