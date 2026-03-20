package com.ai_kids_care.v1.entity;

import com.ai_kids_care.v1.type.GenderEnum;
import com.ai_kids_care.v1.type.LevelEnum;
import com.ai_kids_care.v1.type.StatusEnum;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.JdbcType;
import org.hibernate.dialect.PostgreSQLEnumJdbcType;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Entity
@Table(name = "teachers", schema = "public", indexes = {
        @Index(name = "uq_teacher_kg_teacherid", columnList = "kindergarten_id, teacher_id", unique = true),
        @Index(name = "uq_teacher_kg_staffno", columnList = "kindergarten_id, staff_no", unique = true),
        @Index(name = "uq_teacher_user_id", columnList = "user_id", unique = true)
})
public class Teacher {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "teacher_id", nullable = false)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "kindergarten_id", nullable = false)
    private Kindergarten kindergarten;

    @NotNull
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "staff_no", length = Integer.MAX_VALUE)
    private String staffNo;

    @Column(name = "name", length = Integer.MAX_VALUE)
    private String name;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "gender", columnDefinition = "gender_enum")
    private GenderEnum gender;

    @Column(name = "emergency_contact_name", length = Integer.MAX_VALUE)
    private String emergencyContactName;

    @Column(name = "emergency_contact_phone", length = Integer.MAX_VALUE)
    private String emergencyContactPhone;

    @Column(name = "rrn_encrypted", length = Integer.MAX_VALUE)
    private String rrnEncrypted;

    @Column(name = "rrn_first6", length = Integer.MAX_VALUE)
    private String rrnFirst6;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "level", columnDefinition = "level_enum")
    private LevelEnum level;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "status", columnDefinition = "status_enum")
    private StatusEnum status;

    @ColumnDefault("'2026-03-17 12:56:22.027559+00'")
    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @ColumnDefault("'2026-03-17 12:56:22.027559+00'")
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
}