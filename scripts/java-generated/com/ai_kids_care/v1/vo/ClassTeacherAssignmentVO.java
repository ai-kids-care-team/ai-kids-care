package com.ai_kids_care.v1.vo;

import java.io.Serializable;
import java.time.OffsetDateTime;

/**
 * VO for {@link com.ai_kids_care.v1.entity.ClassTeacherAssignment}
 */
public record ClassTeacherAssignmentVO(
        Long assignmentId,
        Long kindergartenId,
        Long classId,
        Long teacherId,
        String role,
        LocalDate startDate,
        LocalDate endDate,
        String reason,
        String note,
        String status,
        Long createdByUserId,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) implements Serializable {
}