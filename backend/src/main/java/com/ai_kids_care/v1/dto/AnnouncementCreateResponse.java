package com.ai_kids_care.v1.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class AnnouncementCreateResponse {
    private Long id;
    private String message;
}
