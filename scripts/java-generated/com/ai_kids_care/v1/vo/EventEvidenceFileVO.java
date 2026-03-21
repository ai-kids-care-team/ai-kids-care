package com.ai_kids_care.v1.vo;

import java.io.Serializable;
import java.time.OffsetDateTime;

/**
 * VO for {@link com.ai_kids_care.v1.entity.EventEvidenceFile}
 */
public record EventEvidenceFileVO(
        Long evidenceId,
        Long eventId,
        Long kindergartenId,
        String type,
        String storageUri,
        String mimeType,
        OffsetDateTime createdAt,
        OffsetDateTime retentionUntil,
        Boolean hold,
        String hash
) implements Serializable {
}