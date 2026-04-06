package com.ai_kids_care.v1.entity;

import com.ai_kids_care.v1.type.EventStatusEnum;
import com.ai_kids_care.v1.type.EventTypeEnum;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Entity
@Table(name = "detection_events", indexes = {
        @Index(name = "uq_event_kg_eventid", columnList = "kindergarten_id, event_id", unique = true),
        @Index(name = "idx_event_camera_time", columnList = "kindergarten_id, camera_id, detected_at"),
        @Index(name = "idx_event_room_time", columnList = "kindergarten_id, room_id, detected_at"),
        @Index(name = "idx_event_status_time", columnList = "kindergarten_id, status, detected_at")})
public class DetectionEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "event_id", nullable = false)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "kindergarten_id", nullable = false)
    private Kindergarten kindergarten;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "camera_id", nullable = false)
    private CctvCamera cctvCameras;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    private Room rooms;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private DetectionSession detectionSessions;

    @NotNull
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "event_type", columnDefinition = "event_type_enum")
    private EventTypeEnum eventType;

    @NotNull
    @Column(name = "severity", nullable = false)
    private Integer severity;

    @NotNull
    @Column(name = "confidence", nullable = false)
    private Double confidence;

    @NotNull
    @Column(name = "detected_at", nullable = false)
    private OffsetDateTime detectedAt;

    @NotNull
    @Column(name = "start_time", nullable = false)
    private OffsetDateTime startTime;

    @NotNull
    @Column(name = "end_time", nullable = false)
    private OffsetDateTime endTime;

    @NotNull
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "status", columnDefinition = "event_status_enum")
    private EventStatusEnum status;

    @CreationTimestamp
    @ColumnDefault("now()")
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @ColumnDefault("now()")
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;


}