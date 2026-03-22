package com.ai_kids_care.v1.vo;

import java.io.Serializable;
import java.time.OffsetDateTime;

/**
 * VO for {@link com.ai_kids_care.v1.entity.Kindergarten}
 */
public record KindergartenVO(
        Long kindergartenId,
        String name,
        String address,
        String regionCode,
        String code,
        String businessRegistrationNo,
        String contactName,
        String contactPhone,
        String contactEmail,
        String status,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) implements Serializable {
}