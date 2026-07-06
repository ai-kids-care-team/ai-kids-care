package com.ai_kids_care.v1.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * Step 2 of the SMS password reset flow (UX-07): exchange the SMS code for a short-lived,
 * single-use {@code resetToken}.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PasswordResetVerifyDTO {

    @NotBlank(message = "challengeId를 입력해주세요.")
    @Schema(name = "challengeId", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonProperty("challengeId")
    private String challengeId;

    @ToString.Exclude
    @NotBlank(message = "인증번호를 입력해주세요.")
    @Schema(name = "code", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonProperty("code")
    private String code;
}
