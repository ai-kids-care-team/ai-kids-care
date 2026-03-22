package com.ai_kids_care.v1.entity;

import com.ai_kids_care.v1.type.DevicePlatformEnum;
import com.ai_kids_care.v1.type.StatusEnum;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.JdbcType;
import org.hibernate.dialect.PostgreSQLEnumJdbcType;

import java.time.OffsetDateTime;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Entity
@Table(name = "device_tokens", schema = "public", indexes = {
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

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "platform", columnDefinition = "device_platform_enum")
    private DevicePlatformEnum platform;

    @Column(name = "push_token", length = Integer.MAX_VALUE)
    private String pushToken;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "status", columnDefinition = "status_enum")
    private StatusEnum status;

    @Column(name = "last_seen_at")
    private OffsetDateTime lastSeenAt;

    @ColumnDefault("'2026-03-17 12:56:22.204973+00'")
    @Column(name = "created_at")
    private OffsetDateTime createdAt;


}