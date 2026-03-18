package com.ai_kids_care.v1.entity;

import com.ai_kids_care.v1.type.CameraStreamTypeEnum;
import com.ai_kids_care.v1.type.StatusEnum;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.hibernate.annotations.ColumnDefault;

import java.time.OffsetDateTime;

@Getter
@Setter
@Accessors(chain = true)
@Entity
@Table(name = "camera_streams", schema = "public", indexes = {
        @Index(name = "uq_stream_kg_camera_streamid",
                columnList = "kindergarten_id, camera_id, stream_id",
                unique = true),
        @Index(name = "idx_stream_camera_primary",
                columnList = "kindergarten_id, camera_id, is_primary"),
        @Index(name = "idx_stream_camera_enabled",
                columnList = "kindergarten_id, camera_id, enabled"),
        @Index(name = "idx_stream_camera",
                columnList = "kindergarten_id, camera_id")})
public class CameraStream {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "stream_id", nullable = false)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private CctvCamera cctvCameras;

    @Enumerated(EnumType.STRING)
    @Column(name = "stream_type", columnDefinition = "camera_stream_type_enum")
    private CameraStreamTypeEnum streamType;

    @Column(name = "stream_url", length = Integer.MAX_VALUE)
    private String streamUrl;

    @Column(name = "stream_user", length = Integer.MAX_VALUE)
    private String streamUser;

    @Column(name = "stream_password_encrypted", length = Integer.MAX_VALUE)
    private String streamPasswordEncrypted;

    @Column(name = "protocol", length = Integer.MAX_VALUE)
    private String protocol;

    @Column(name = "fps")
    private Integer fps;

    @Column(name = "resolution", length = Integer.MAX_VALUE)
    private String resolution;

    @Column(name = "is_primary")
    private Boolean isPrimary;

    @Column(name = "enabled")
    private Boolean enabled;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", columnDefinition = "status_enum")
    private StatusEnum status;

    @ColumnDefault("'2026-03-17 12:56:22.145436+00'")
    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @ColumnDefault("'2026-03-17 12:56:22.145436+00'")
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;


}