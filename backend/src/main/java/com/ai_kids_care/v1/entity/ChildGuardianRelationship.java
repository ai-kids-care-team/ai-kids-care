package com.ai_kids_care.v1.entity;

import com.ai_kids_care.v1.type.RelationshipEnum;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.hibernate.annotations.ColumnDefault;

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
    @EmbeddedId
    private ChildGuardianRelationshipId id;

    @MapsId("kindergartenId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "kindergarten_id", nullable = false)
    private Kindergarten kindergarten;

    @MapsId("childId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "child_id", nullable = false)
    private Child children;

    @MapsId("guardianId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "guardian_id", nullable = false)
    private Guardian guardians;

    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
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

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;


}