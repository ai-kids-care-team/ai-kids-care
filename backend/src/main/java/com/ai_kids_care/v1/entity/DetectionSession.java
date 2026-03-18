package com.ai_kids_care.v1.entity;

import com.ai_kids_care.v1.type.StatusEnum;
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
@Table(name = "detection_sessions", schema = "public", indexes = {
        @Index(name = "uq_session_kg_sessionid", columnList = "kindergarten_id, session_id", unique = true),
        @Index(name = "idx_session_camera_time", columnList = "kindergarten_id, camera_id, started_at")
})
public class DetectionSession {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "session_id", nullable = false)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private CameraStream cameraStreams;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "model_id", nullable = false)
    private AiModel model;

    @ColumnDefault("'2026-03-17 12:56:22.166748+00'")
    @Column(name = "started_at")
    private OffsetDateTime startedAt;

    @Column(name = "ended_at")
    private OffsetDateTime endedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", columnDefinition = "status_enum")
    private StatusEnum status;

    @Column(name = "avg_latency_ms")
    private Integer avgLatencyMs;

    @Column(name = "inference_fps")
    private Double inferenceFps;


}