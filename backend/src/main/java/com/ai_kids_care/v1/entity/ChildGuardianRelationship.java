package com.ai_kids_care.v1.entity;

import com.ai_kids_care.v1.type.RelationshipEnum;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.JdbcType;
import org.hibernate.dialect.PostgreSQLEnumJdbcType;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Entity
@Table(name = "child_guardian_relationships", schema = "public", indexes = {
        @Index(name = "idx_cgr_child_primary", columnList = "kindergarten_id, child_id, is_primary"),
        @Index(name = "idx_cgr_child", columnList = "kindergarten_id, child_id"),
        @Index(name = "idx_cgr_guardian", columnList = "kindergarten_id, guardian_id")
})
public class ChildGuardianRelationship {
    /** null 이면 Hibernate 가 @MapsId 로 채우기 전에 NPE 가능 — 빌더에서도 기본 인스턴스 유지 */
    @Builder.Default
    @EmbeddedId
    private ChildGuardianRelationshipId id = new ChildGuardianRelationshipId();

    @MapsId("childId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "child_id", nullable = false)
    private Child children;

    @MapsId("guardianId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "guardian_id", nullable = false)
    private Guardian guardians;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "relationship", columnDefinition = "relationship_enum")
    private RelationshipEnum relationship;

    @Column(name = "is_primary")
    private Boolean isPrimary;

    @Column(name = "priority")
    private Integer priority;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @ColumnDefault("'2026-03-17 12:56:22.120648+00'")
    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @ColumnDefault("'2026-03-17 12:56:22.120648+00'")
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;


}