package com.dashboard.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcType;
import org.hibernate.dialect.PostgreSQLEnumJdbcType;

import java.time.Instant;

@Entity
@Table(name = "user_account")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "user_name", nullable = false)
    private String userName;

    @Column(name = "login_id", nullable = false)
    private String loginId;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "user_email", nullable = false)
    private String userEmail;

    @Column(name = "user_tel", nullable = false)
    private String userTel;

    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "status", columnDefinition = "status_enum", nullable = false)
    private StatusEnum status;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public User(String userName, String loginId, String passwordHash, String userEmail, String userTel) {
        this.userName = userName;
        this.loginId = loginId;
        this.passwordHash = passwordHash;
        this.userEmail = userEmail;
        this.userTel = userTel;
        this.status = StatusEnum.ACTIVE;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = (this.createdAt == null) ? now : this.createdAt;
        this.updatedAt = (this.updatedAt == null) ? now : this.updatedAt;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public void recordLogin(Instant at) {
        this.lastLoginAt = at;
    }
}
