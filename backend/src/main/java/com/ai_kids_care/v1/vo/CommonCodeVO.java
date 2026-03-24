package com.ai_kids_care.v1.vo;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class CommonCodeVO {
    private String codeGroup;
    private String parentCode;
    private String code;
    private String codeName;
    private int sortOrder;
}
