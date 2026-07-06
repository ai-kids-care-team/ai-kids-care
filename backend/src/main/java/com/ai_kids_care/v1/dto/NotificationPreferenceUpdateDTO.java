package com.ai_kids_care.v1.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

/**
 * UX-08: upsert request for the caller's own notification preference.
 *
 * {@code quietHoursStart}/{@code quietHoursEnd} must both be null (clear quiet hours) or both be
 * set (define a window) — the single-sided case is rejected as 400 by
 * {@link com.ai_kids_care.v1.service.NotificationPreferenceService}; bean validation only checks
 * the {@code HH:mm} format when present (null passes {@code @Pattern} by design).
 */
@Getter
@Setter
@Schema(description = "통지 환경설정 upsert 요청")
public class NotificationPreferenceUpdateDTO {

    @NotNull
    @Schema(description = "알림 총 스위치", requiredMode = Schema.RequiredMode.REQUIRED)
    private Boolean enabled;

    @Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d$", message = "quietHoursStart must be HH:mm")
    @Schema(description = "무음 시간대 시작 (HH:mm, Asia/Seoul); start/end 동시에 null = 무음 해제")
    private String quietHoursStart;

    @Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d$", message = "quietHoursEnd must be HH:mm")
    @Schema(description = "무음 시간대 종료 (HH:mm, Asia/Seoul)")
    private String quietHoursEnd;
}
