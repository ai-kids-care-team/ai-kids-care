package com.ai_kids_care.v1.entity;

import com.ai_kids_care.v1.type.StatusEnum;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.hibernate.annotations.ColumnDefault;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Getter
@Setter
@Accessors(chain = true)
@Entity
@Table(name = "children", schema = "public", indexes = {
        @Index(name = "uq_child_kg_childid",
                columnList = "kindergarten_id, child_id",
                unique = true),
        @Index(name = "uq_child_kg_childno",
                columnList = "kindergarten_id, child_no",
                unique = true)})
public class Child {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "child_id", nullable = false)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "kindergarten_id", nullable = false)
    private Kindergarten kindergarten;

    @Column(name = "name", length = Integer.MAX_VALUE)
    private String name;

    @Column(name = "child_no", length = Integer.MAX_VALUE)
    private String childNo;

    @Column(name = "rrn_encrypted", length = Integer.MAX_VALUE)
    private String rrnEncrypted;

    @Column(name = "rrn_first6", length = Integer.MAX_VALUE)
    private String rrnFirst6;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Column(name = "gender", length = Integer.MAX_VALUE)
    private String gender;

    @Column(name = "address", length = Integer.MAX_VALUE)
    private String address;

    @Column(name = "enroll_date")
    private LocalDate enrollDate;

    @Column(name = "leave_date")
    private LocalDate leaveDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", columnDefinition = "status_enum")
    private StatusEnum status;

    @ColumnDefault("'2026-03-17 12:56:22.005959+00'")
    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @ColumnDefault("'2026-03-17 12:56:22.005959+00'")
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;


}