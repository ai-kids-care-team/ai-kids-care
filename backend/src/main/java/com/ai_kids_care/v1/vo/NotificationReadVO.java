package com.ai_kids_care.v1.vo;

import java.io.Serializable;

/**
 * 通知读取最小 VO（SPEC-0001 / ADR-0018 A3d）。
 *
 * 仅返回业务必要的最小字段。不含内部/S0 字段：
 * channel / dedupeKey / sentAt / failReason / retryCount / recipientUserId / kindergartenId。
 * 受体读自己的通知；KINDERGARTEN_ADMIN 读其园的通知（细粒度作用域由 NotificationRepository SQL 强制）。
 */
public record NotificationReadVO(
        Long notificationId,
        String title,
        String body,
        String status,
        java.time.OffsetDateTime createdAt
) implements Serializable {
}
