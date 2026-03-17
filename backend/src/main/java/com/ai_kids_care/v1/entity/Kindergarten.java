package com.ai_kids_care.v1.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

import java.time.OffsetDateTime;

@Getter
@Setter
@Entity
@Table(name = "kindergartens", schema = "public")
public class Kindergarten {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "kindergarten_id", nullable = false)
    private Long id;

    @Column(name = "name", length = Integer.MAX_VALUE)
    private String name;

    @Column(name = "address", length = Integer.MAX_VALUE)
    private String address;

    @Column(name = "region_code", length = Integer.MAX_VALUE)
    private String regionCode;

    @Column(name = "code", length = Integer.MAX_VALUE)
    private String code;

    @Column(name = "business_registration_no", length = Integer.MAX_VALUE)
    private String businessRegistrationNo;

    @Column(name = "contact_name", length = Integer.MAX_VALUE)
    private String contactName;

    @Column(name = "contact_phone", length = Integer.MAX_VALUE)
    private String contactPhone;

    @Column(name = "contact_email", length = Integer.MAX_VALUE)
    private String contactEmail;

    @Column(name = "status", columnDefinition = "status_enum")
    private Object status;

    @ColumnDefault("'2026-03-17 12:56:21.964081+00'")
    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @ColumnDefault("'2026-03-17 12:56:21.964081+00'")
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;


}