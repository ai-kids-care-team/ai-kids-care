package com.ai_kids_care.v1.entity;

import com.ai_kids_care.v1.type.GenderEnum;
import com.ai_kids_care.v1.type.LevelEnum;
import com.ai_kids_care.v1.type.StatusEnum;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Entity
@Table(name = "teachers", indexes = {
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

    @NotNull
    @Column(name = "staff_no", nullable = false, length = Integer.MAX_VALUE)
    private String staffNo;

    @NotNull
    @Column(name = "name", nullable = false, length = Integer.MAX_VALUE)
    private String name;

    @NotNull
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "gender", columnDefinition = "gender_enum")
    private GenderEnum gender;

    @Column(name = "emergency_contact_name", length = Integer.MAX_VALUE)
    private String emergencyContactName;

    @Column(name = "emergency_contact_phone", length = Integer.MAX_VALUE)
    private String emergencyContactPhone;

    @NotNull
    @Column(name = "rrn_encrypted", nullable = false, length = Integer.MAX_VALUE)
    private String rrnEncrypted;

    @NotNull
    @Column(name = "rrn_first6", nullable = false, length = Integer.MAX_VALUE)
    private String rrnFirst6;

    @NotNull
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "level", columnDefinition = "level_enum")
    private LevelEnum level;

    @NotNull
    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @NotNull
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "status", columnDefinition = "status_enum")
    private StatusEnum status;

    @CreationTimestamp
    @ColumnDefault("now()")
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @ColumnDefault("now()")
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
