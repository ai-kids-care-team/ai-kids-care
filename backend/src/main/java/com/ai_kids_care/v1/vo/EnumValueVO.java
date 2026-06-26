package com.ai_kids_care.v1.vo;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * ADR-0013 enum 메타데이터 항목.
 *
 * <p>{@code code} 는 백엔드 {@code type.*} enum 의 상수명, {@code sortOrder} 는 선언 순서.
 * 화면 표시 라벨(label)은 ADR-0013 의 i18n 계층(프론트엔드) 책임이라 의도적으로 제외한다.
 */
@Getter
@AllArgsConstructor
public class EnumValueVO {
    private final String code;
    private final int sortOrder;
}
