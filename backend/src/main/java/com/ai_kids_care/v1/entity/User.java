package com.ai_kids_care.v1.entity;

import com.ai_kids_care.v1.type.StatusEnum;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Entity
@Table(name = "users", indexes = {
        @Index(name = "users_login_id_key", columnList = "login_id", unique = true),
        @Index(name = "uq_user_account_email", columnList = "email", unique = true),
        @Index(name = "uq_user_account_phone", columnList = "phone", unique = true)
})
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id", nullable = false)
    private Long id;

    @NotNull
    @Column(name = "login_id", nullable = false, length = Integer.MAX_VALUE)
    private String loginId;

    @Column(name = "email", length = Integer.MAX_VALUE)
    private String email;

    /**
     * 휴대폰·유선 10~11자리(클라이언트에서 숫자만 전송 권장).
     */
    @Size(message = "전화번호는10~11자리로 입력해 주세요.", min = 10, max = 11)
    @Column(name = "phone", length = Integer.MAX_VALUE)
    private String phone;

    @NotNull
    @Column(name = "password_hash", nullable = false, length = Integer.MAX_VALUE)
    private String passwordHash;

    @NotNull
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "status", columnDefinition = "status_enum")
    private StatusEnum status;

    @Column(name = "last_login_at")
    private OffsetDateTime lastLoginAt;

    @CreationTimestamp
    @ColumnDefault("now()")
    @NotNull
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @ColumnDefault("now()")
    @NotNull
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;


}