package com.ai_kids_care.v1.vo;

import java.io.Serializable;
import java.time.OffsetDateTime;

/**
 * VO for {@link com.ai_kids_care.v1.entity.UserKindergartenMembership}
 */
public record UserKindergartenMembershipVO(
        Long membershipId,
        Long userId,
        Long kindergartenId,
        String status,
        OffsetDateTime joinedAt,
        OffsetDateTime leftAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) implements Serializable {
}