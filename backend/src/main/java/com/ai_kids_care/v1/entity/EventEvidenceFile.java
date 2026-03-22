package com.ai_kids_care.v1.entity;

import com.ai_kids_care.v1.converter.MimeTypeEnumConverter;
import com.ai_kids_care.v1.type.EvidenceFileTypeEnum;
import com.ai_kids_care.v1.type.MimeTypeEnum;
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
@Table(name = "event_evidence_files", schema = "public", indexes = {
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

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "type", columnDefinition = "evidence_file_type_enum")
    private EvidenceFileTypeEnum type;

    @Column(name = "storage_uri", length = Integer.MAX_VALUE)
    private String storageUri;

    @Convert(converter = MimeTypeEnumConverter.class)
    @Column(name = "mime_type", columnDefinition = "mime_type_enum")
    private MimeTypeEnum mimeType;

    @ColumnDefault("'2026-03-17 12:56:22.194471+00'")
    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @Column(name = "retention_until")
    private OffsetDateTime retentionUntil;

    @Column(name = "hold")
    private Boolean hold;

    @Column(name = "hash", length = Integer.MAX_VALUE)
    private String hash;


}