package com.ai_kids_care.v1.entity;

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
@Table(name = "event_evidence_files", schema = "public", indexes = {@Index(name = "idx_evidence_event_time",
        columnList = "kindergarten_id, event_id, created_at")})
public class EventEvidenceFile {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "evidence_id", nullable = false)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private DetectionEvent detectionEvents;

    @Column(name = "type", length = Integer.MAX_VALUE)
    private String type;

    @Column(name = "storage_uri", length = Integer.MAX_VALUE)
    private String storageUri;

    @Column(name = "mime_type", length = Integer.MAX_VALUE)
    private String mimeType;

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