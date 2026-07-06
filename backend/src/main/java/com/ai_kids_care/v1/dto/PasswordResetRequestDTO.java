package com.ai_kids_care.v1.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Step 1 of the enumeration-safe SMS password reset flow (UX-07): the caller only supplies
 * {@code loginId} (never email/phone, to minimize the enumeration surface).
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PasswordResetRequestDTO {

    @NotBlank(message = "로그인 ID를 입력해주세요.")
    @Schema(name = "loginId", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonProperty("loginId")
    private String loginId;
}
