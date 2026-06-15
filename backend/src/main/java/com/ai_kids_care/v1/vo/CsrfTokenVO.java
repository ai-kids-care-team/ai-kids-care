package com.ai_kids_care.v1.vo;

public record CsrfTokenVO(
        String token,
        String headerName,
        String parameterName
) {
}
