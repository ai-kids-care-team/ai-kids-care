package com.ai_kids_care.v1.vo;

import java.io.Serializable;
import java.time.OffsetDateTime;

/**
 * VO for {@link com.ai_kids_care.v1.entity.AppreciationLetter}
 */
public record AppreciationLetterVO(
        Long letterId,
        Long kindergartenId,
        Long senderUserId,
        String targetType,
        Long targetId,
        String title,
        String content,
        Boolean isPublic,
        String status,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) implements Serializable {
}