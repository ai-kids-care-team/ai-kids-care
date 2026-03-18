package com.ai_kids_care.v1.entity;

import jakarta.persistence.*;
import lombok.*;
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

    @MapsId("id")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Child children;

    @MapsId("id")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Guardian guardians;

    @Column(name = "relationship", length = Integer.MAX_VALUE)
    private String relationship;

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