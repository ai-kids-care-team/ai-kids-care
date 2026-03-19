package com.ai_kids_care.v1.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
public class AnnouncementCreateRequest {
    private String title;
    private String body;
    private Boolean pinned;
    private Instant pinnedUntil;
    private String status;
    private Instant publishedAt;
    private Instant startsAt;
    private Instant endsAt;
}
