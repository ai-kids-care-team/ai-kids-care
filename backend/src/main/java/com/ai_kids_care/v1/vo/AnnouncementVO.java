package com.ai_kids_care.v1.vo;

import java.io.Serializable;
import java.time.OffsetDateTime;

/**
 * VO for {@link com.ai_kids_care.v1.entity.Announcement}
 */
public record AnnouncementVO(
        Long id,
        Long authorId,
        String title,
        String body,
        Boolean isPinned,
        OffsetDateTime pinnedUntil,
        String status,
        OffsetDateTime publishedAt,
        OffsetDateTime startsAt,
        OffsetDateTime endsAt,
        Long viewCount,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        OffsetDateTime deletedAt
) implements Serializable {
}