package com.ai_kids_care.v1.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.OffsetDateTime;

/**
 * DTO for {@link com.ai_kids_care.v1.entity.AuditLog}
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class AuditLogUpdateDTO implements Serializable {
    private Long auditId;
    private Long kindergartenId;
    private Long userId;
    private String action;
    private String resourceType;
    private Long resourceId;
    private String ip;
    private String userAgent;
    private OffsetDateTime createdAt;
}