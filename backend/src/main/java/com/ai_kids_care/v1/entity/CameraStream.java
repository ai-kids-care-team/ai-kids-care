package com.ai_kids_care.v1.entity;

import com.ai_kids_care.v1.type.CameraStreamTypeEnum;
import com.ai_kids_care.v1.type.ProtocolEnum;
import com.ai_kids_care.v1.type.StatusEnum;
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
@Table(name = "camera_streams", indexes = {@Index(name = "uq_stream_kg_camera_streamid", columnList = "kindergarten_id, camera_id, stream_id", unique = true), @Index(name = "idx_stream_camera_primary", columnList = "kindergarten_id, camera_id, is_primary"), @Index(name = "idx_stream_camera_enabled", columnList = "kindergarten_id, camera_id, enabled"), @Index(name = "idx_stream_camera", columnList = "kindergarten_id, camera_id")})
public class CameraStream {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "stream_id", nullable = false)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "camera_id", nullable = false)
    private CctvCamera cctvCameras;

    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "stream_type", columnDefinition = "camera_stream_type_enum")
    private CameraStreamTypeEnum streamType;

    @Column(name = "stream_url", length = Integer.MAX_VALUE)
    private String streamUrl;

    @Column(name = "stream_user", length = Integer.MAX_VALUE)
    private String streamUser;

    @Column(name = "stream_password_ciphertext", length = Integer.MAX_VALUE)
    private String streamPasswordCiphertext;

    @Column(name = "stream_password_iv", length = Integer.MAX_VALUE)
    private String streamPasswordIv;

    @Column(name = "stream_password_key_version", length = 32)
    private String streamPasswordKeyVersion;

    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "protocol", columnDefinition = "protocol_enum")
    private ProtocolEnum protocol;

    @Column(name = "fps")
    private Integer fps;

    @Column(name = "resolution", length = Integer.MAX_VALUE)
    private String resolution;

    @Column(name = "is_primary")
    private Boolean isPrimary;

    @Column(name = "enabled")
    private Boolean enabled;

    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "status", columnDefinition = "status_enum")
    private StatusEnum status;

    @Column(name = "credential_updated_at")
    private OffsetDateTime credentialUpdatedAt;

    @CreationTimestamp
    @ColumnDefault("now()")
    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @ColumnDefault("now()")
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;


}