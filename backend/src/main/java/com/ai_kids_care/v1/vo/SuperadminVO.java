package com.ai_kids_care.v1.vo;

import java.io.Serializable;
import java.time.OffsetDateTime;

/**
 * VO for {@link com.ai_kids_care.v1.entity.Superadmin}
 */
public record SuperadminVO(
        Long superadminId,
        Long userId,
        String name,
        String department,
        String status,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) implements Serializable {
}