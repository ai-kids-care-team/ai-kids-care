package com.ai_kids_care.v1.vo;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * VO for {@link com.ai_kids_care.v1.entity.Class}
 */
public record ClassVO(
        Long classId,
        Long kindergartenId,
        String name,
        String grade,
        Long academicYear,
        LocalDate startDate,
        LocalDate endDate,
        String status,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) implements Serializable {
}