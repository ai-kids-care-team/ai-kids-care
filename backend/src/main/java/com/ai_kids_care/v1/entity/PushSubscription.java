package com.ai_kids_care.v1.entity;

import com.ai_kids_care.v1.type.PushProviderEnum;
import com.ai_kids_care.v1.type.StatusEnum;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;

/**
 * Per-user PUSH delivery identity (provider-aware), replacing the FCM/APNS-shaped
 * {@code device_tokens} table. For Pushover, {@code address} holds the recipient's
 * Pushover user key. SMS/EMAIL addresses are NOT stored here — they reuse
 * {@code users.phone} / {@code users.email}.
 */
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Entity
@Table(name = "push_subscriptions", indexes = {
        @Index(name = "uq_push_subscriptions_user_provider_address",
                columnList = "user_id, provider, address", unique = true),
        @Index(name = "idx_push_subscriptions_user_status", columnList = "user_id, status")
})
public class PushSubscription {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "push_subscription_id", nullable = false)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @NotNull
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "provider", columnDefinition = "push_provider_enum")
    private PushProviderEnum provider;

    /** Provider delivery address. For Pushover, the recipient's user key. */
    @NotNull
    @Column(name = "address", nullable = false, length = Integer.MAX_VALUE)
    private String address;

    /** Optional human label / Pushover device name. */
    @Column(name = "device_label", length = Integer.MAX_VALUE)
    private String deviceLabel;

    @NotNull
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "status", columnDefinition = "status_enum")
    private StatusEnum status;

    @Column(name = "last_verified_at")
    private OffsetDateTime lastVerifiedAt;

    @CreationTimestamp
    @ColumnDefault("now()")
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
}
