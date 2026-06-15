package com.ai_kids_care.v1.vo;

import java.io.Serializable;
import java.time.OffsetDateTime;

/**
 * VO for {@link com.ai_kids_care.v1.entity.CommonCode}
 */
public record CommonCodeVO(
        Long codeId,
        String parentCode,
        String codeGroup,
        String code,
        String codeName,
        Integer sortOrder,
        Boolean isActive,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) implements Serializable {
}