package com.ai_kids_care.v1.vo;

import java.io.Serializable;
import java.time.OffsetDateTime;

/**
 * VO for {@link com.ai_kids_care.v1.entity.User}
 */
public record UserVO(
        Long userId,
        String loginId,
        String email,
        String phone,
        String passwordHash,
        String status,
        OffsetDateTime lastLoginAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) implements Serializable {
}