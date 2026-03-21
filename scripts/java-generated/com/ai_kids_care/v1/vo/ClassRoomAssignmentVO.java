package com.ai_kids_care.v1.vo;

import java.io.Serializable;
import java.time.OffsetDateTime;

/**
 * VO for {@link com.ai_kids_care.v1.entity.ClassRoomAssignment}
 */
public record ClassRoomAssignmentVO(
        Long assignmentId,
        Long kindergartenId,
        Long classId,
        Long roomId,
        OffsetDateTime startAt,
        OffsetDateTime endAt,
        String purpose,
        String note,
        String status,
        Long createdByUserId,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) implements Serializable {
}