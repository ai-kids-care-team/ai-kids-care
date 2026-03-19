package com.ai_kids_care.v1.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;

@Getter
@AllArgsConstructor
public class AnnouncementSummaryResponse {
    private Long id;
    private String title;
    private boolean pinned;
    private Long viewCount;
    private Instant publishedAt;
    private Instant createdAt;
}
