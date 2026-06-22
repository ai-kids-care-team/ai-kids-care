package com.ai_kids_care.v1.repository;

import com.ai_kids_care.v1.entity.Guardian;
import com.ai_kids_care.v1.type.StatusEnum;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface GuardianRepository extends JpaRepository<Guardian, Long> {

    // BE-4: 按 userId 查 guardian 档案（用于 session name 解析）
    Optional<Guardian> findByUser_Id(Long userId);

    // SPEC-0002 Slice A: 同园 tenant-aware 查询（approve/reject/disable guardian 档案）
    Optional<Guardian> findByUser_IdAndKindergarten_IdAndStatus(
            Long userId,
            Long kindergartenId,
            StatusEnum status);

    // SPEC-0002 Slice A: 条件更新档案状态（防 TOCTOU）
    @Modifying
    @Query("""
            UPDATE Guardian g
            SET g.status = :newStatus
            WHERE g.user.id = :userId
              AND g.kindergarten.id = :kindergartenId
              AND g.status = :expectedStatus
            """)
    int conditionalUpdateStatus(
            @Param("userId") Long userId,
            @Param("kindergartenId") Long kindergartenId,
            @Param("expectedStatus") StatusEnum expectedStatus,
            @Param("newStatus") StatusEnum newStatus);
}