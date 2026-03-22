package com.ai_kids_care.v1.vo;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * VO for {@link com.ai_kids_care.v1.entity.Teacher}
 */
public record TeacherVO(
        Long teacherId,
        Long kindergartenId,
        Long userId,
        String staffNo,
        String name,
        String gender,
        String emergencyContactName,
        String emergencyContactPhone,
        String rrnEncrypted,
        String rrnFirst6,
        String level,
        LocalDate startDate,
        LocalDate endDate,
        String status,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) implements Serializable {
}