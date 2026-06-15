package com.ai_kids_care.v1.entity;

import com.ai_kids_care.v1.type.UserRoleAssignmentScopeType;
import jakarta.persistence.*;
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
@Table(name = "audit_logs")
public class AuditLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "audit_id", nullable = false)
    private Long id;

    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "scope_type", columnDefinition = "user_role_assignment_scope_type", nullable = false)
    private UserRoleAssignmentScopeType scopeType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "kindergarten_id")
    private Kindergarten kindergarten;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "action", length = Integer.MAX_VALUE)
    private String action;

    @Column(name = "resource_type", length = Integer.MAX_VALUE)
    private String resourceType;

    @Column(name = "resource_id")
    private Long resourceId;

    @Column(name = "ip", length = Integer.MAX_VALUE)
    private String ip;

    @Column(name = "user_agent", length = Integer.MAX_VALUE)
    private String userAgent;

    @Column(name = "effective_role", length = Integer.MAX_VALUE)
    private String effectiveRole;

    @Column(name = "result", length = Integer.MAX_VALUE)
    private String result;

    @Column(name = "correlation_id", length = Integer.MAX_VALUE)
    private String correlationId;

    @CreationTimestamp
    @ColumnDefault("now()")
    @Column(name = "created_at")
    private OffsetDateTime createdAt;
}
