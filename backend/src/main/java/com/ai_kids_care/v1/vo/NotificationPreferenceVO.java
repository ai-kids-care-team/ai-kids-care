package com.ai_kids_care.v1.vo;

import java.io.Serializable;

/**
 * UX-08: the caller's own notification preference (canonical {@code notification_rules} row,
 * {@code target_type=KINDERGARTEN}). {@code enabled=true} + null quiet hours is the implicit
 * default when no canonical row exists yet (never 404 — see api-contract.md).
 */
public record NotificationPreferenceVO(
        boolean enabled,
        String quietHoursStart,
        String quietHoursEnd
) implements Serializable {
}
