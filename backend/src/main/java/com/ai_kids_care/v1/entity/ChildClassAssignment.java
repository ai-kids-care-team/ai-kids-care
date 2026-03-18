package com.ai_kids_care.v1.entity;

import com.ai_kids_care.v1.type.StatusEnum;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Getter
@Setter
@Entity
@Table(name = "child_class_assignments", schema = "public", indexes = {
        @Index(name = "idx_cca_child_time",
                columnList = "kindergarten_id, child_id, start_date, end_date"),
        @Index(name = "idx_cca_child",
                columnList = "kindergarten_id, child_id"),
        @Index(name = "idx_cca_class_time",
                columnList = "kindergarten_id, class_id, start_date, end_date")})
public class ChildClassAssignment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "assignment_id", nullable = false)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Child children;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Class classes;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "reason", length = Integer.MAX_VALUE)
    private String reason;

    @Column(name = "note", length = Integer.MAX_VALUE)
    private String note;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", columnDefinition = "status_enum")
    private StatusEnum status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id")
    private User createdByUser;

    @ColumnDefault("'2026-03-17 12:56:22.016653+00'")
    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @ColumnDefault("'2026-03-17 12:56:22.016653+00'")
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;


}