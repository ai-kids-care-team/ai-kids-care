package com.dashboard.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class CommonCodeResponse {
    private String codeGroup;
    private String parentCode;
    private String code;
    private String codeName;
    private int sortOrder;
}
