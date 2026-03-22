package com.ai_kids_care.v1.vo;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * VO for {@link com.ai_kids_care.v1.entity.Child}
 */
public record ChildVO(
        Long childId,
        Long kindergartenId,
        String name,
        String childNo,
        String rrnEncrypted,
        String rrnFirst6,
        LocalDate birthDate,
        String gender,
        String address,
        LocalDate enrollDate,
        LocalDate leaveDate,
        String status,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) implements Serializable {
}