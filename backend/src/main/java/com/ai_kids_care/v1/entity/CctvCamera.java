package com.ai_kids_care.v1.entity;

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
@Table(name = "cctv_cameras", indexes = {
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

    @NotNull
    @Column(name = "camera_name", nullable = false, length = Integer.MAX_VALUE)
    private String cameraName;

    @Column(name = "model", length = Integer.MAX_VALUE)
    private String model;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by_user_id", nullable = false)
    private User createdByUser;

    @NotNull
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "status", columnDefinition = "status_enum")
    private StatusEnum status;

    @Column(name = "last_seen_at")
    private OffsetDateTime lastSeenAt;

    @CreationTimestamp
    @ColumnDefault("now()")
    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @ColumnDefault("now()")
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;


}
