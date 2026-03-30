package com.ai_kids_care.v1.entity;

import com.ai_kids_care.v1.type.RelationshipEnum;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Entity
@Table(name = "child_guardian_relationships", indexes = {
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

    @NotNull
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "relationship", columnDefinition = "relationship_enum")
    private RelationshipEnum relationship;

    @NotNull
    @Column(name = "is_primary", nullable = false)
    private Boolean isPrimary;

    @NotNull
    @Column(name = "priority", nullable = false)
    private Integer priority;

    @NotNull
    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @CreationTimestamp
    @ColumnDefault("now()")
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @ColumnDefault("now()")
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;


}
