package com.ai_kids_care.v1.vo;

import java.io.Serializable;
import java.time.OffsetDateTime;

/**
 * VO for {@link com.ai_kids_care.v1.entity.UserRoleAssignment}
 */
public record UserRoleAssignmentVO(
        Long roleAssignmentId,
        Long userId,
        String role,
        String scopeType,
        Long scopeId,
        String status,
        OffsetDateTime grantedAt,
        Long grantedByUserId,
        OffsetDateTime revokedAt,
        Long revokedByUserId
) implements Serializable {
}