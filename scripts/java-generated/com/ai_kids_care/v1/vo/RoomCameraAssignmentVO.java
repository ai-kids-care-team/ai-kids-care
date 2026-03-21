package com.ai_kids_care.v1.vo;

import java.io.Serializable;
import java.time.OffsetDateTime;

/**
 * VO for {@link com.ai_kids_care.v1.entity.RoomCameraAssignment}
 */
public record RoomCameraAssignmentVO(
        Long assignmentId,
        Long kindergartenId,
        Long cameraId,
        Long roomId,
        OffsetDateTime startAt,
        OffsetDateTime endAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) implements Serializable {
}