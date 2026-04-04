package com.ai_kids_care.v1.entity;

import com.ai_kids_care.v1.type.EventTypeEnum;
import com.ai_kids_care.v1.type.NotificationTargetType;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Entity
@Table(name = "notification_rules", indexes = {
        @Index(name = "idx_rule_owner", columnList = "kindergarten_id, user_id"),
        @Index(name = "idx_rule_enabled", columnList = "kindergarten_id, enabled")
})
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

    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "target_type", columnDefinition = "notification_target_type")
    private NotificationTargetType targetType;

    @NotNull
    @Column(name = "target_id", nullable = false)
    private Long targetId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", length = Integer.MAX_VALUE)
    private EventTypeEnum eventType;

    @NotNull
    @Column(name = "min_severity", nullable = false)
    private Integer minSeverity;

    @Column(name = "quiet_hours_json", length = Integer.MAX_VALUE)
    private String quietHoursJson;

    @NotNull
    @Column(name = "enabled", nullable = false)
    private Boolean enabled;

    @CreationTimestamp
    @ColumnDefault("now()")
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;


}
