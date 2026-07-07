package com.ai_kids_care.v1.entity;

import com.ai_kids_care.v1.type.NotificationChannelEnum;
import com.ai_kids_care.v1.type.NotificationStatusEnum;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;

import java.time.OffsetDateTime;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Entity
@Table(name = "notifications", indexes = {
        @Index(name = "uq_notifications_dedupe", columnList = "kindergarten_id, dedupe_key", unique = true),
        @Index(name = "idx_notif_event", columnList = "kindergarten_id, event_id"),
        @Index(name = "idx_notif_recipient_time", columnList = "kindergarten_id, recipient_user_id, created_at"),
        @Index(name = "idx_notif_status_time", columnList = "kindergarten_id, status, created_at")
})
public class Notification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "notification_id", nullable = false)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "kindergarten_id", nullable = false)
    private Kindergarten kindergarten;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    private DetectionEvent detectionEvents;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipient_user_id", nullable = false)
    private User recipientUser;

    @NotNull
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "channel", columnDefinition = "notification_channel_enum")
    private NotificationChannelEnum channel;

    @NotNull
    @Column(name = "title", nullable = false, length = Integer.MAX_VALUE)
    private String title;

    @NotNull
    @Column(name = "body", nullable = false, length = Integer.MAX_VALUE)
    private String body;

    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "status", columnDefinition = "notification_status_enum")
    private NotificationStatusEnum status;

    @NotNull
    @Column(name = "dedupe_key", nullable = false, length = Integer.MAX_VALUE)
    private String dedupeKey;

    // OQ-DATA-3 / ADR-0018：待发态在发送前为 NULL（见 V3 迁移）。
    @Column(name = "sent_at")
    private OffsetDateTime sentAt;

    // OQ-DATA-3 / ADR-0018：无失败时为 NULL。
    @Column(name = "fail_reason", length = Integer.MAX_VALUE)
    private String failReason;

    // OQ-DATA-3 / ADR-0018：新建通知默认 0（保留 NOT NULL；DB DEFAULT 0 见 V3 迁移）。
    @NotNull
    @ColumnDefault("0")
    @Column(name = "retry_count", nullable = false)
    @Builder.Default
    private Integer retryCount = 0;

    // ③b: 정숙시간(quiet hours) 지연 발송 예정 시각(DEFERRED일 때). NULL이면 지연 대상 아님.
    @Column(name = "deferred_until")
    private OffsetDateTime deferredUntil;

    @CreationTimestamp
    @ColumnDefault("now()")
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    // wire-notification-read-state (V2): per-recipient in-app read timestamp, orthogonal to
    // delivery `status`. NULL = unread (default for existing + new rows).
    @Column(name = "read_at")
    private OffsetDateTime readAt;

}
