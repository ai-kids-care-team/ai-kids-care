package com.ai_kids_care.v1.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

import java.time.OffsetDateTime;

@Getter
@Setter
@Entity
@Table(name = "room_camera_assignments", schema = "public", indexes = {
        @Index(name = "idx_rca_camera_time",
                columnList = "kindergarten_id, camera_id, start_at, end_at"),
        @Index(name = "idx_rca_room_time",
                columnList = "kindergarten_id, room_id, start_at, end_at")})
public class RoomCameraAssignment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "assignment_id", nullable = false)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private CctvCamera cctvCameras;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Room rooms;

    @NotNull
    @ColumnDefault("'2026-03-17 12:56:22.099651+00'")
    @Column(name = "start_at", nullable = false)
    private OffsetDateTime startAt;

    @Column(name = "end_at")
    private OffsetDateTime endAt;

    @ColumnDefault("'2026-03-17 12:56:22.099651+00'")
    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @ColumnDefault("'2026-03-17 12:56:22.099651+00'")
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;


}