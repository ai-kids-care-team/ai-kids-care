package com.ai_kids_care.v1.entity;

import com.ai_kids_care.dashboard.entity.User;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

import java.time.OffsetDateTime;

@Getter
@Setter
@Entity
@Table(name = "guardians", schema = "public", indexes = {
        @Index(name = "uq_guardian_kg_guardianid",
                columnList = "kindergarten_id, guardian_id",
                unique = true),
        @Index(name = "uq_guardian_user_id",
                columnList = "user_id",
                unique = true)})
public class Guardian {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "guardian_id", nullable = false)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "kindergarten_id", nullable = false)
    private Kindergarten kindergarten;

    @NotNull
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "name", length = Integer.MAX_VALUE)
    private String name;

    @Column(name = "rrn_encrypted", length = Integer.MAX_VALUE)
    private String rrnEncrypted;

    @Column(name = "rrn_first6", length = Integer.MAX_VALUE)
    private String rrnFirst6;

    @Column(name = "gender", length = Integer.MAX_VALUE)
    private String gender;

    @Column(name = "address", length = Integer.MAX_VALUE)
    private String address;

    @Column(name = "status", columnDefinition = "status_enum")
    private Object status;

    @ColumnDefault("'2026-03-17 12:56:22.109709+00'")
    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @ColumnDefault("'2026-03-17 12:56:22.109709+00'")
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;


}