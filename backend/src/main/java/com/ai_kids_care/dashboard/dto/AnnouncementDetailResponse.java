package com.ai_kids_care.dashboard.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;

@Getter
@AllArgsConstructor
public class AnnouncementDetailResponse {
    private Long id;
    private String title;
    private String body;
    private Long viewCount;
    private Instant publishedAt;
    private Instant createdAt;
}
