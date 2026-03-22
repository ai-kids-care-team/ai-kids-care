package com.ai_kids_care.v1.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.OffsetDateTime;

/**
 * DTO for {@link com.ai_kids_care.v1.entity.EventEvidenceFile}
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class EventEvidenceFileCreateDTO implements Serializable {
    private Long evidenceId;
    private Long eventId;
    private Long kindergartenId;
    private String type;
    private String storageUri;
    private String mimeType;
    private OffsetDateTime createdAt;
    private OffsetDateTime retentionUntil;
    private Boolean hold;
    private String hash;
}