package com.ai_kids_care.v1.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record GuardianChildVerificationRequest(
        @NotBlank(message = "Child RRN is required")
        @Pattern(regexp = "\\d{6}", message = "Child RRN first part must be 6 digits")
        String childRrnFirst6,
        @NotBlank(message = "Child RRN is required")
        @Pattern(regexp = "\\d{7}", message = "Child RRN last part must be 7 digits")
        String childRrnBack7
) {
}
