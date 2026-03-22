package com.ai_kids_care.v1.vo;

import java.io.Serializable;
import java.time.OffsetDateTime;

/**
 * VO for {@link com.ai_kids_care.v1.entity.AuditLog}
 */
public record AuditLogVO(
        Long auditId,
        Long kindergartenId,
        Long userId,
        String action,
        String resourceType,
        Long resourceId,
        String ip,
        String userAgent,
        OffsetDateTime createdAt
) implements Serializable {
}