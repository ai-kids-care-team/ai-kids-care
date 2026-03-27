package com.ai_kids_care.v1.entity;

import com.ai_kids_care.v1.type.StatusEnum;
import com.ai_kids_care.v1.type.UserRoleAssignmentScopeType;
import com.ai_kids_care.v1.type.UserRoleEnum;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;

import java.time.OffsetDateTime;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Entity
@Table(name = "user_role_assignments", indexes = {
        @Index(name = "uq_ura_user_role_scope", columnList = "user_id, role, scope_type, scope_id", unique = true),
        @Index(name = "idx_ura_scope_status", columnList = "scope_type, scope_id, status")
})
public class UserRoleAssignment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "role_assignment_id", nullable = false)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "role", columnDefinition = "user_role_enum not null")
    private UserRoleEnum role;

    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "scope_type", columnDefinition = "user_role_assignment_scope_type not null")
    private UserRoleAssignmentScopeType scopeType;

    @Column(name = "scope_id")
    private Long scopeId;

    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "status", columnDefinition = "status_enum not null")
    private StatusEnum status;

    @NotNull
    @Column(name = "granted_at", nullable = false)
    private OffsetDateTime grantedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "granted_by_user_id")
    private User grantedByUser;

    @Column(name = "revoked_at")
    private OffsetDateTime revokedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "revoked_by_user_id")
    private User revokedByUser;


}