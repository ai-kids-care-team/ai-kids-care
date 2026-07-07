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
        String mimeType,
        OffsetDateTime createdAt,
        OffsetDateTime retentionUntil,
        Boolean hold,
        String hash,
        // D-STORE: computed by EventEvidenceFileService (not derivable from the entity alone) —
        // relative backend content-endpoint path when readable, else null (e.g. legacy file:// rows).
        String contentPath,
        // D-STORE: whether the backend can currently read this evidence from object storage.
        Boolean available
) implements Serializable {
}
