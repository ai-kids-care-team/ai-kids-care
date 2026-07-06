package com.ai_kids_care.v1.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.OffsetDateTime;

/**
 * Response of {@code POST /api/v1/auth/password-reset/verify} (UX-07). Field name is
 * deliberately {@code resetToken} (not the orphan {@code VerifyVerificationCodeRequest} sibling
 * VO's {@code verificationToken}) — the frozen api-contract.md is the single source of truth here.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PasswordResetVerifyVO {

    @lombok.NonNull
    @Schema(name = "resetToken", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonProperty("resetToken")
    private String resetToken;

    @lombok.NonNull
    @Schema(name = "expiresAt", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonProperty("expiresAt")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private OffsetDateTime expiresAt;
}
