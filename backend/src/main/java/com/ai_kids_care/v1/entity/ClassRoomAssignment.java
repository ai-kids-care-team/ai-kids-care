package com.ai_kids_care.v1.entity;

import com.ai_kids_care.dashboard.entity.User;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

import java.time.OffsetDateTime;

@Getter
@Setter
@Entity
@Table(name = "class_room_assignments", schema = "public", indexes = {
        @Index(name = "idx_cra_class_time",
                columnList = "kindergarten_id, class_id, start_at, end_at"),
        @Index(name = "idx_cra_room_time",
                columnList = "kindergarten_id, room_id, start_at, end_at")})
public class ClassRoomAssignment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "assignment_id", nullable = false)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Class aClass;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Room room;

    @NotNull
    @Column(name = "start_at", nullable = false)
    private OffsetDateTime startAt;

    @Column(name = "end_at")
    private OffsetDateTime endAt;

    @Column(name = "purpose", length = Integer.MAX_VALUE)
    private String purpose;

    @Column(name = "note", length = Integer.MAX_VALUE)
    private String note;

    @Column(name = "status", columnDefinition = "status_enum")
    private Object status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id")
    private User createdByUser;

    @ColumnDefault("'2026-03-17 12:56:22.072866+00'")
    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @ColumnDefault("'2026-03-17 12:56:22.072866+00'")
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;


}