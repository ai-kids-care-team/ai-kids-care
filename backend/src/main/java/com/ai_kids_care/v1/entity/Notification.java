package com.ai_kids_care.v1.entity;

import com.ai_kids_care.v1.type.NotificationStatusEnum;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.hibernate.annotations.ColumnDefault;

import java.time.OffsetDateTime;

@Getter
@Setter
@Accessors(chain = true)
@Entity
@Table(name = "notifications", schema = "public", indexes = {
        @Index(name = "uq_notifications_dedupe",
                columnList = "kindergarten_id, dedupe_key",
                unique = true),
        @Index(name = "idx_notif_event",
                columnList = "kindergarten_id, event_id"),
        @Index(name = "idx_notif_recipient_time",
                columnList = "kindergarten_id, recipient_user_id, created_at"),
        @Index(name = "idx_notif_status_time",
                columnList = "kindergarten_id, status, created_at")})
public class Notification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "notification_id", nullable = false)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private DetectionEvent detectionEvents;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipient_user_id", nullable = false)
    private User recipientUser;

    @Column(name = "channel", length = Integer.MAX_VALUE)
    private String channel;

    @Column(name = "title", length = Integer.MAX_VALUE)
    private String title;

    @Column(name = "body", length = Integer.MAX_VALUE)
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", columnDefinition = "notification_status_enum")
    private NotificationStatusEnum status;

    @Column(name = "dedupe_key", length = Integer.MAX_VALUE)
    private String dedupeKey;

    @Column(name = "sent_at")
    private OffsetDateTime sentAt;

    @Column(name = "fail_reason", length = Integer.MAX_VALUE)
    private String failReason;

    @Column(name = "retry_count")
    private Integer retryCount;

    @ColumnDefault("'2026-03-17 12:56:22.226279+00'")
    @Column(name = "created_at")
    private OffsetDateTime createdAt;


}