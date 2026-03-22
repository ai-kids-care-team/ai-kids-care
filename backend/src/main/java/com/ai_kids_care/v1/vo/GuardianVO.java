package com.ai_kids_care.v1.vo;

import java.io.Serializable;
import java.time.OffsetDateTime;

/**
 * VO for {@link com.ai_kids_care.v1.entity.Guardian}
 */
public record GuardianVO(
        Long guardianId,
        Long kindergartenId,
        Long userId,
        String name,
        String rrnEncrypted,
        String rrnFirst6,
        String gender,
        String address,
        String status,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) implements Serializable {
}