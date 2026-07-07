package com.ai_kids_care.v1.vo;

import java.io.Serializable;

/**
 * 通知读取最小 VO（SPEC-0001 / ADR-0018 A3d；readAt 见 wire-notification-read-state / D3）。
 *
 * 仅返回业务必要的最小字段。不含内部/S0 字段：
 * channel / dedupeKey / sentAt / failReason / retryCount / recipientUserId / kindergartenId。
 * 受体读自己的通知；KINDERGARTEN_ADMIN 读其园的通知（细粒度作用域由 NotificationRepository SQL 强制）。
 *
 * {@code readAt}（可空）= 该收件人本人的站内阅读时刻，NULL=未读；与投递 {@code status} 正交
 * （回答"发出去没"vs"本人读过没"），KINDERGARTEN_ADMIN 全园视图下他人通知的 readAt 是该收件人的读时刻。
 */
public record NotificationReadVO(
        Long notificationId,
        String title,
        String body,
        String status,
        java.time.OffsetDateTime readAt,
        java.time.OffsetDateTime createdAt
) implements Serializable {
}
