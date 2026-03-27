package com.ai_kids_care.v1.entity;

import com.ai_kids_care.v1.type.StatusEnum;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;

import java.time.OffsetDateTime;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Entity
@Table(name = "kindergartens")
public class Kindergarten {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "kindergarten_id", nullable = false)
    private Long id;

    @NotNull
    @Column(name = "name", nullable = false, length = Integer.MAX_VALUE)
    private String name;

    @NotNull
    @Column(name = "address", nullable = false, length = Integer.MAX_VALUE)
    private String address;

    @Column(name = "region_code", length = Integer.MAX_VALUE)
    private String regionCode;

    @Column(name = "code", length = Integer.MAX_VALUE)
    private String code;

    @NotNull
    @Column(name = "business_registration_no", nullable = false, length = Integer.MAX_VALUE)
    private String businessRegistrationNo;

    @NotNull
    @Column(name = "contact_name", nullable = false, length = Integer.MAX_VALUE)
    private String contactName;

    @NotNull
    @Column(name = "contact_phone", nullable = false, length = Integer.MAX_VALUE)
    private String contactPhone;

    @NotNull
    @Column(name = "contact_email", nullable = false, length = Integer.MAX_VALUE)
    private String contactEmail;

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