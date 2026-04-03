package com.ai_kids_care.v1.type;

/**
 * DB {@code notification_channel_enum} 과 동일한 라벨
 */
public enum NotificationChannelEnum {
    PUSH,
    SMS,
    EMAIL;

    public static NotificationChannelEnum from(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("channel can not be null or empty");
        }

        try {
            return NotificationChannelEnum.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("No supported channel: " + value, e);
        }
    }
}
