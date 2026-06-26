package com.ai_kids_care.v1.entity;

import com.ai_kids_care.v1.type.DeliveryAttemptOutcome;
import com.ai_kids_care.v1.type.NotificationChannelEnum;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;

/**
 * Per-notification external-delivery attempt + idempotency record (PRF-02 delivery atomicity).
 *
 * <p>Exactly one row per notification ({@code UNIQUE(notification_id)} + {@code UNIQUE(idempotency_key)}):
 * {@code NotificationService} commits an {@code IN_FLIGHT} row in a short transaction <em>before</em> the
 * provider network call, then records the terminal {@code SUCCEEDED}/{@code FAILED} outcome in a second
 * short transaction afterwards. Because the attempt is committed before the provider call, a retry of a
 * notification that already has an attempt row never re-invokes the provider — this is the at-most-once
 * guard (design Decision 2). {@code outcome}/{@code provider} are plain varchars to keep the migration
 * additive (no new PG enum type).</p>
 */
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Entity
@Table(name = "notification_delivery_attempts", indexes = {
        @Index(name = "uq_notif_delivery_attempt_notification", columnList = "notification_id", unique = true),
        @Index(name = "uq_notif_delivery_attempt_idem", columnList = "idempotency_key", unique = true),
        @Index(name = "idx_notif_delivery_attempt_outcome", columnList = "outcome")
})
public class NotificationDeliveryAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "attempt_id", nullable = false)
    private Long id;

    /** The notification whose external delivery this attempt records (one attempt per notification). */
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "notification_id", nullable = false)
    private Notification notification;

    /** Globally-unique idempotency token for this delivery (derived from the notification id). */
    @NotNull
    @Column(name = "idempotency_key", nullable = false, length = Integer.MAX_VALUE)
    private String idempotencyKey;

    @NotNull
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "channel", columnDefinition = "notification_channel_enum")
    private NotificationChannelEnum channel;

    /** Provider label (e.g. {@code PUSHOVER}, {@code SOLAPI}); informational. */
    @Column(name = "provider", length = Integer.MAX_VALUE)
    private String provider;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "outcome", nullable = false, length = 32)
    private DeliveryAttemptOutcome outcome;

    /** Failure reason / provider detail when the attempt did not succeed. */
    @Column(name = "detail", length = Integer.MAX_VALUE)
    private String detail;

    @CreationTimestamp
    @ColumnDefault("now()")
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @ColumnDefault("now()")
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
