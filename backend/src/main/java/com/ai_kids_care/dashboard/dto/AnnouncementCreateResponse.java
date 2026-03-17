package com.ai_kids_care.dashboard.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class AnnouncementCreateResponse {
    private Long id;
    private String message;
}
