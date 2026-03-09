package com.dashboard.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "user_account")
@Getter
@Setter
@NoArgsConstructor
public class User {

    @Id
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

    @Column(name = "status")
    private String status;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public User(Long userId, String userName, String loginId, String passwordHash, String userEmail, String userTel, String status) {
        this.userId = userId;
        this.userName = userName;
        this.loginId = loginId;
        this.passwordHash = passwordHash;
        this.userEmail = userEmail;
        this.userTel = userTel;
        this.status = status;
    }

    @PrePersist
    public void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
        if (lastLoginAt == null) {
            lastLoginAt = now;
        }
    }

    @PreUpdate
    public void onUpdate() {
        updatedAt = Instant.now();
    }
}
