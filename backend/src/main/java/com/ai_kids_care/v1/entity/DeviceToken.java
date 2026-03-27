package com.ai_kids_care.v1.entity;

import com.ai_kids_care.v1.type.DevicePlatformEnum;
import com.ai_kids_care.v1.type.StatusEnum;
import jakarta.persistence.*;
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
@Table(name = "device_tokens", indexes = {
        @Index(name = "uq_device_tokens_platform_push_token", columnList = "platform, push_token", unique = true),
        @Index(name = "idx_device_tokens_user_status", columnList = "user_id, status")
})
public class DeviceToken {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "device_id", nullable = false)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @NotNull
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "platform", columnDefinition = "device_platform_enum not null")
    private DevicePlatformEnum platform;

    @NotNull
    @Column(name = "push_token", nullable = false, length = Integer.MAX_VALUE)
    private String pushToken;

    @NotNull
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "status", columnDefinition = "status_enum not null")
    private StatusEnum status;

    @Column(name = "last_seen_at")
    private OffsetDateTime lastSeenAt;

    @NotNull
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;


}