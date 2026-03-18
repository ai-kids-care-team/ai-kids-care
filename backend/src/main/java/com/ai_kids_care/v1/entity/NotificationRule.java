package com.ai_kids_care.v1.entity;

import com.ai_kids_care.v1.type.NotificationTargetType;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

import java.time.OffsetDateTime;

@Getter
@Setter
@Entity
@Table(name = "notification_rules", schema = "public", indexes = {
        @Index(name = "idx_rule_owner",
                columnList = "kindergarten_id, user_id"),
        @Index(name = "idx_rule_enabled",
                columnList = "kindergarten_id, enabled")})
public class NotificationRule {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "rule_id", nullable = false)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "kindergarten_id", nullable = false)
    private Kindergarten kindergarten;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", columnDefinition = "notification_target_type")
    private NotificationTargetType targetType;

    @Column(name = "target_id")
    private Long targetId;

    @Column(name = "event_type", length = Integer.MAX_VALUE)
    private String eventType;

    @Column(name = "min_severity")
    private Integer minSeverity;

    @Column(name = "quiet_hours_json", length = Integer.MAX_VALUE)
    private String quietHoursJson;

    @Column(name = "enabled")
    private Boolean enabled;

    @ColumnDefault("'2026-03-17 12:56:22.215651+00'")
    @Column(name = "created_at")
    private OffsetDateTime createdAt;


}