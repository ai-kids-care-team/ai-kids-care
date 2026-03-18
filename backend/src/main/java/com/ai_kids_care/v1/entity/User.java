package com.ai_kids_care.v1.entity;

import com.ai_kids_care.v1.type.StatusEnum;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.hibernate.annotations.ColumnDefault;

import java.time.OffsetDateTime;

@Getter
@Setter
@Accessors(chain = true)
@Entity
@Table(name = "users", schema = "public", indexes = {
        @Index(name = "users_login_id_key",
                columnList = "login_id",
                unique = true),
        @Index(name = "uq_user_account_email",
                columnList = "email",
                unique = true),
        @Index(name = "uq_user_account_phone",
                columnList = "phone",
                unique = true)})
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id", nullable = false)
    private Long id;

    @Column(name = "login_id", length = Integer.MAX_VALUE)
    private String loginId;

    @Column(name = "email", length = Integer.MAX_VALUE)
    private String email;

    @Column(name = "phone", length = Integer.MAX_VALUE)
    private String phone;

    @Column(name = "password_hash", length = Integer.MAX_VALUE)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", columnDefinition = "status_enum")
    private StatusEnum status;

    @Column(name = "last_login_at")
    private OffsetDateTime lastLoginAt;

    @ColumnDefault("'2026-03-17 12:56:21.947484+00'")
    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @ColumnDefault("'2026-03-17 12:56:21.947484+00'")
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;


}