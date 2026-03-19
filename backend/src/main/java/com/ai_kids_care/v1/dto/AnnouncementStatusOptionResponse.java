package com.ai_kids_care.v1.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class AnnouncementStatusOptionResponse {
    private String code;
    private String codeName;
    private int sortOrder;
}
