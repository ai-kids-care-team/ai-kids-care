package com.ai_kids_care.v1.vo;

import java.io.Serializable;
import java.time.OffsetDateTime;

/**
 * VO for {@link com.ai_kids_care.v1.entity.ChildGuardianRelationship}
 */
public record ChildGuardianRelationshipVO(
        Long kindergartenId,
        Long childId,
        Long guardianId,
        String relationship,
        Boolean isPrimary,
        Integer priority,
        LocalDate startDate,
        LocalDate endDate,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) implements Serializable {
}