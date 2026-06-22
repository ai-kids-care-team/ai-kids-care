package com.ai_kids_care.v1.vo;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AuthSessionVO(
        Long userId,
        String loginId,
        String effectiveRole,
        String scopeType,
        Long scopeId,
        String name
) {
}
