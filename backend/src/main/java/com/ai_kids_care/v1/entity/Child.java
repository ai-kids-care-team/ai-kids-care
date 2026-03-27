package com.ai_kids_care.v1.entity;

import com.ai_kids_care.v1.type.GenderEnum;
import com.ai_kids_care.v1.type.StatusEnum;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Entity
@Table(name = "children", indexes = {
        @Index(name = "uq_child_kg_childid", columnList = "kindergarten_id, child_id", unique = true),
        @Index(name = "uq_child_kg_childno", columnList = "kindergarten_id, child_no", unique = true)
})
public class Child {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "child_id", nullable = false)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "kindergarten_id", nullable = false)
    private Kindergarten kindergarten;
    @NotNull
    @Column(name = "name", nullable = false, length = Integer.MAX_VALUE)
    private String name;

    @NotNull
    @Column(name = "child_no", nullable = false, length = Integer.MAX_VALUE)
    private String childNo;

    @NotNull
    @Column(name = "rrn_first6", nullable = false, length = Integer.MAX_VALUE)
    private String rrnEncrypted;

    @NotNull
    @Column(name = "rrn_encrypted", nullable = false, length = Integer.MAX_VALUE)
    private String rrnFirst6;

    @NotNull
    @Column(name = "birth_date", nullable = false)
    private LocalDate birthDate;

    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "gender", columnDefinition = "gender_enum not null")
    private GenderEnum gender;

    @NotNull
    @Column(name = "address", nullable = false, length = Integer.MAX_VALUE)
    private String address;

    @NotNull
    @Column(name = "enroll_date", nullable = false)
    private LocalDate enrollDate;

    @Column(name = "leave_date")
    private LocalDate leaveDate;

    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "status", columnDefinition = "status_enum not null")
    private StatusEnum status;

    @NotNull
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @NotNull
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;


}