package com.ai_kids_care.v1.repository;

import com.ai_kids_care.v1.entity.User;
import com.ai_kids_care.v1.type.StatusEnum;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {
    User findByLoginIdOrEmailOrPhone(String loginId, String email, String phone);

    // UX-07 wire-password-management: password-reset/request looks up by loginId only (not
    // email/phone) to minimize the enumeration surface (see api-contract.md).
    User findByLoginId(String loginId);

    boolean existsByLoginIdOrEmailOrPhone(String loginId, String email, String phone);

    boolean existsByLoginId(String loginId);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByPhone(String phone);

    // SPEC-0002 Slice A: 条件更新 User.status（防 TOCTOU）
    @Modifying
    @Query("UPDATE User u SET u.status = :newStatus WHERE u.id = :userId AND u.status = :expectedStatus")
    int conditionalUpdateStatus(
            @Param("userId") Long userId,
            @Param("expectedStatus") StatusEnum expectedStatus,
            @Param("newStatus") StatusEnum newStatus);
}