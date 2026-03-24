package com.ai_kids_care.v1.vo;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;

@Getter
@AllArgsConstructor
public class AnnouncementSummaryVO {
    private Long id;
    private String title;
    private String body;
    private boolean isPinned;
    private Long viewCount;
    private Instant publishedAt;
    private Instant createdAt;
    /** 목록 조회에서는 null일 수 있음 */
    private String status;
    private Instant pinnedUntil;
    private Instant startsAt;
    private Instant endsAt;
}

