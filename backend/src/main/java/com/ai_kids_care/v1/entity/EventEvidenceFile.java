package com.ai_kids_care.v1.entity;

import com.ai_kids_care.v1.type.EvidenceFileTypeEnum;
import com.ai_kids_care.v1.type.MimeTypeEnum;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Entity
@Table(name = "event_evidence_files", indexes = {
        @Index(name = "idx_evidence_event_time", columnList = "kindergarten_id, event_id, created_at")
})
public class EventEvidenceFile {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "evidence_id", nullable = false)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    private DetectionEvent detectionEvents;

    @NotNull
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "type", columnDefinition = "evidence_file_type_enum")
    private EvidenceFileTypeEnum type;

    @NotNull
    @Column(name = "storage_uri", nullable = false, length = Integer.MAX_VALUE)
    private String storageUri;

    @NotNull
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "mime_type", columnDefinition = "mime_type_enum")
    private MimeTypeEnum mimeType;

    @CreationTimestamp
    @ColumnDefault("now()")
    @NotNull
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "retention_until")
    private OffsetDateTime retentionUntil;

    @NotNull
    @Column(name = "hold", nullable = false)
    private Boolean hold;

    @NotNull
    @Column(name = "hash", nullable = false, length = Integer.MAX_VALUE)
    private String hash;


}