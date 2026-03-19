package com.ai_kids_care.v1.entity;

import com.ai_kids_care.v1.type.EventStatusEnum;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;

import java.time.OffsetDateTime;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Entity
@Table(name = "detection_events", schema = "public", indexes = {
        @Index(name = "uq_event_kg_eventid", columnList = "kindergarten_id, event_id", unique = true),
        @Index(name = "idx_event_camera_time", columnList = "kindergarten_id, camera_id, detected_at"),
        @Index(name = "idx_event_room_time", columnList = "kindergarten_id, room_id, detected_at"),
        @Index(name = "idx_event_status_time", columnList = "kindergarten_id, status, detected_at")
})
public class DetectionEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "event_id", nullable = false)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private CctvCamera cctvCameras;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Room rooms;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private DetectionSession detectionSessions;

    @Column(name = "event_type", length = Integer.MAX_VALUE)
    private String eventType;

    @Column(name = "severity")
    private Integer severity;

    @Column(name = "confidence")
    private Double confidence;

    @ColumnDefault("'2026-03-17 12:56:22.173225+00'")
    @Column(name = "detected_at")
    private OffsetDateTime detectedAt;

    @Column(name = "start_time")
    private OffsetDateTime startTime;

    @Column(name = "end_time")
    private OffsetDateTime endTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", columnDefinition = "event_status_enum")
    private EventStatusEnum status;

    @ColumnDefault("'2026-03-17 12:56:22.173225+00'")
    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @ColumnDefault("'2026-03-17 12:56:22.173225+00'")
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;


}