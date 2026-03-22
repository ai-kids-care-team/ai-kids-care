package com.ai_kids_care.v1.entity;

import com.ai_kids_care.v1.type.StatusEnum;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.JdbcType;
import org.hibernate.dialect.PostgreSQLEnumJdbcType;

import java.time.OffsetDateTime;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Entity
@Table(name = "cctv_cameras", schema = "public", indexes = {
        @Index(name = "uq_camera_kg_cameraid", columnList = "kindergarten_id, camera_id", unique = true),
        @Index(name = "uq_camera_kg_serialno", columnList = "kindergarten_id, serial_no", unique = true)
})
public class CctvCamera {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "camera_id", nullable = false)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "kindergarten_id", nullable = false)
    private Kindergarten kindergarten;

    @Column(name = "serial_no", length = Integer.MAX_VALUE)
    private String serialNo;

    @Column(name = "camera_name", length = Integer.MAX_VALUE)
    private String cameraName;

    @Column(name = "model", length = Integer.MAX_VALUE)
    private String model;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id")
    private User createdByUser;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "status", columnDefinition = "status_enum")
    private StatusEnum status;

    @Column(name = "last_seen_at")
    private OffsetDateTime lastSeenAt;

    @ColumnDefault("'2026-03-17 12:56:22.083549+00'")
    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @ColumnDefault("'2026-03-17 12:56:22.083549+00'")
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;


}