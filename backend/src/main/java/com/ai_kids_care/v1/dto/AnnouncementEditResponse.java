package com.ai_kids_care.v1.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;

@Getter
@AllArgsConstructor
public class AnnouncementEditResponse {
    private Long id;
    private String title;
    private String body;
    private Boolean pinned;
    private Instant pinnedUntil;
    private String status;
    private Instant publishedAt;
    private Instant startsAt;
    private Instant endsAt;
}
