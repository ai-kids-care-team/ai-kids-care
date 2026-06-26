package com.ai_kids_care.v1.repository;

import com.ai_kids_care.v1.entity.Teacher;
import com.ai_kids_care.v1.type.StatusEnum;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface TeacherRepository extends JpaRepository<Teacher, Long> {
    Page<Teacher> findByNameContains(String keyword, Pageable pageable);
    Page<Teacher> findByNameContainsAndUserId(String keyword, Long userId, Pageable pageable);
    Page<Teacher> findByUserId(Long userId, Pageable pageable);
    Optional<Teacher> findByUserId(Long userId);

    // GT-1 / SEC-07: tenant-scoped reads — same-kindergarten roster only.
    // Coarse TENANT_S2_READ gate (ADMIN + TEACHER) + kindergarten_id filter; no
    // assignment-level narrowing (a teacher may see same-kindergarten colleagues).
    Page<Teacher> findAllByKindergarten_Id(Long kindergartenId, Pageable pageable);

    Page<Teacher> findByNameContainsAndKindergarten_Id(
            String keyword, Long kindergartenId, Pageable pageable);

    Page<Teacher> findByUserIdAndKindergarten_Id(
            Long userId, Long kindergartenId, Pageable pageable);

    Page<Teacher> findByNameContainsAndUserIdAndKindergarten_Id(
            String keyword, Long userId, Long kindergartenId, Pageable pageable);

    Optional<Teacher> findByIdAndKindergarten_Id(Long id, Long kindergartenId);

    Optional<Teacher> findByUserIdAndKindergarten_Id(Long userId, Long kindergartenId);

    // SPEC-0002 Slice A: 同园 tenant-aware 查询（approve/reject/disable teacher 档案）
    Optional<Teacher> findByUser_IdAndKindergarten_IdAndStatus(
            Long userId,
            Long kindergartenId,
            StatusEnum status);

    // SPEC-0002 Slice A: 条件更新档案状态（防 TOCTOU）
    @Modifying
    @Query("""
            UPDATE Teacher t
            SET t.status = :newStatus
            WHERE t.user.id = :userId
              AND t.kindergarten.id = :kindergartenId
              AND t.status = :expectedStatus
            """)
    int conditionalUpdateStatus(
            @Param("userId") Long userId,
            @Param("kindergartenId") Long kindergartenId,
            @Param("expectedStatus") StatusEnum expectedStatus,
            @Param("newStatus") StatusEnum newStatus);
}
