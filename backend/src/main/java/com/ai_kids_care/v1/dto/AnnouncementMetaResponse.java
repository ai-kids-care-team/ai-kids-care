package com.ai_kids_care.v1.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class AnnouncementMetaResponse {
    private boolean canWrite;
    private List<AnnouncementStatusOptionResponse> statusOptions;
}
