package com.ai_kids_care.v1.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.OffsetDateTime;

/**
 * DTO for {@link com.ai_kids_care.v1.entity.Announcement}
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class AnnouncementCreateDTO implements Serializable {
    private String title;
    private String body;
    private Boolean isPinned;
    private OffsetDateTime pinnedUntil;
    private String status;
    private OffsetDateTime publishedAt;
    private OffsetDateTime startsAt;
    private OffsetDateTime endsAt;
}
