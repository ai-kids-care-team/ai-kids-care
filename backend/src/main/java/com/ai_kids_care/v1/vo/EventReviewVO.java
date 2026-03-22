package com.ai_kids_care.v1.vo;

import java.io.Serializable;
import java.time.OffsetDateTime;

/**
 * VO for {@link com.ai_kids_care.v1.entity.EventReview}
 */
public record EventReviewVO(
        Long reviewId,
        Long eventId,
        Long kindergartenId,
        Long userId,
        String fromStatus,
        String resultStatus,
        String comment,
        OffsetDateTime createdAt
) implements Serializable {
}