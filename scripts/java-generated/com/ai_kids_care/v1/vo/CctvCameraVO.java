package com.ai_kids_care.v1.vo;

import java.io.Serializable;
import java.time.OffsetDateTime;

/**
 * VO for {@link com.ai_kids_care.v1.entity.CctvCamera}
 */
public record CctvCameraVO(
        Long cameraId,
        Long kindergartenId,
        String serialNo,
        String cameraName,
        String model,
        Long createdByUserId,
        String status,
        OffsetDateTime lastSeenAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) implements Serializable {
}