package com.ai_kids_care.v1.controller;

import com.ai_kids_care.v1.dto.NotificationPreferenceUpdateDTO;
import com.ai_kids_care.v1.service.NotificationPreferenceService;
import com.ai_kids_care.v1.vo.NotificationPreferenceVO;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * UX-08: {@code /me} self-service notification preference endpoints. Routing only — all business
 * logic (auth scoping, upsert semantics) lives in {@link NotificationPreferenceService}. The
 * pre-existing {@code denyAll()} list/get endpoints (admin-facing rule management placeholder)
 * are untouched by this slice.
 */
@Tag(name="NotificationRule")
@RestController
@RequestMapping("/api/v1/notification_rules")
@RequiredArgsConstructor
public class NotificationRuleController {

    private final NotificationPreferenceService notificationPreferenceService;

    @GetMapping("/me")
    public NotificationPreferenceVO getMyNotificationPreference() {
        return notificationPreferenceService.getMine();
    }

    @PutMapping("/me")
    public NotificationPreferenceVO updateMyNotificationPreference(
            @Valid @RequestBody NotificationPreferenceUpdateDTO dto) {
        return notificationPreferenceService.upsertMine(dto);
    }
}
