package com.ai_kids_care.v1.entity;

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
@Table(name = "superadmins", schema = "public", indexes = {
        @Index(name = "uq_superadmin_userid", columnList = "user_id", unique = true)
})
public class Superadmin {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "superadmin_id", nullable = false)
    private Long id;

    @NotNull
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @NotNull
    @Column(name = "name", nullable = false, length = Integer.MAX_VALUE)
    private String name;

    @Column(name = "department", length = Integer.MAX_VALUE)
    private String department;

    @ColumnDefault("'2026-03-17 12:56:22.038675+00'")
    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @ColumnDefault("'2026-03-17 12:56:22.038675+00'")
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;


}