package com.ai_kids_care.v1.vo;

import java.io.Serializable;

public record KindergartenLookupResponse(
        Long kindergartenId,
        String name,
        String regionCode,
        String code,
        String status
) implements Serializable {
}
