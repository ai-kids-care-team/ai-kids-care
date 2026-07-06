package com.ai_kids_care.v1.dto;

import com.ai_kids_care.v1.security.validation.ValidPassword;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * Step 3 of the SMS password reset flow (UX-07): consume the single-use {@code resetToken} and
 * persist the new password.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PasswordResetConfirmDTO {

    @ToString.Exclude
    @NotBlank(message = "resetToken이 필요합니다.")
    @Schema(name = "resetToken", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonProperty("resetToken")
    private String resetToken;

    @ToString.Exclude
    @NotBlank(message = "새 비밀번호를 입력해주세요.")
    @ValidPassword
    @Schema(name = "newPassword", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonProperty("newPassword")
    private String newPassword;
}
