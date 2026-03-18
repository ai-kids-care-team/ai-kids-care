package com.ai_kids_care.v1.entity;

import com.ai_kids_care.v1.type.StatusEnum;
import com.ai_kids_care.v1.type.UserRoleAssignmentScopeType;
import com.ai_kids_care.v1.type.UserRoleEnum;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.hibernate.annotations.ColumnDefault;

import java.time.OffsetDateTime;

@Getter
@Setter
@Entity
@Accessors(chain = true)
@Table(name = "user_role_assignments", schema = "public", indexes = {
        @Index(name = "uq_ura_user_role_scope",
                columnList = "user_id, role, scope_type, scope_id",
                unique = true),
        @Index(name = "idx_ura_scope_status",
                columnList = "scope_type, scope_id, status")})
public class UserRoleAssignment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "role_assignment_id", nullable = false)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "role", columnDefinition = "user_role_enum")
    private UserRoleEnum role;

    @Column(name = "scope_type", columnDefinition = "user_role_assignment_scope_type")
    private UserRoleAssignmentScopeType scopeType;

    @Column(name = "scope_id")
    private Long scopeId;

    @Column(name = "status", columnDefinition = "status_enum")
    private StatusEnum status;

    @ColumnDefault("'2026-03-17 12:56:22.138783+00'")
    @Column(name = "granted_at")
    private OffsetDateTime grantedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "granted_by_user_id")
    private User grantedByUser;

    @Column(name = "revoked_at")
    private OffsetDateTime revokedAt;


}