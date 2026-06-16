package com.ai_kids_care.v1.repository;

import com.ai_kids_care.v1.entity.Superadmin;
import com.ai_kids_care.v1.type.StatusEnum;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface SuperadminRepository extends JpaRepository<Superadmin, Long> {

    // SPEC-0002 Slice B: 按 userId 查 superadmin 档案（approve/reject/disable）
    Optional<Superadmin> findByUser_Id(Long userId);

    // SPEC-0002 Slice B: 条件更新 superadmin 档案状态（防 TOCTOU）
    @Modifying
    @Query("""
            UPDATE Superadmin s
            SET s.status = :newStatus
            WHERE s.user.id = :userId
              AND s.status = :expectedStatus
            """)
    int conditionalUpdateStatus(
            @Param("userId") Long userId,
            @Param("expectedStatus") StatusEnum expectedStatus,
            @Param("newStatus") StatusEnum newStatus);
}