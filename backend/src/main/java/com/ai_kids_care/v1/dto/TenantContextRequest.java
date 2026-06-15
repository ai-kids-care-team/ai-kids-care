package com.ai_kids_care.v1.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record TenantContextRequest(
        @NotNull @Positive Long kindergartenId
) {
}
