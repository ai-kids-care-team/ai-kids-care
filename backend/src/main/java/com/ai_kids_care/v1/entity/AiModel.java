package com.ai_kids_care.v1.entity;

import com.ai_kids_care.v1.type.StatusEnum;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;

import java.time.OffsetDateTime;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Entity
@Table(name = "ai_models", schema = "public")
public class AiModel {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "model_id", nullable = false)
    private Long id;

    @Column(name = "name", length = Integer.MAX_VALUE)
    private String name;

    @Column(name = "version", length = Integer.MAX_VALUE)
    private String version;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", columnDefinition = "status_enum")
    private StatusEnum status;

    @ColumnDefault("'2026-03-17 12:56:22.156233+00'")
    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @ColumnDefault("'2026-03-17 12:56:22.156233+00'")
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;


}