package com.ai_kids_care.v1.repository;

import com.ai_kids_care.v1.entity.UserKindergartenMembership;
import com.ai_kids_care.v1.type.StatusEnum;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface UserKindergartenMembershipRepository extends JpaRepository<UserKindergartenMembership, Long> {
    List<UserKindergartenMembership> findAllByUser_IdAndStatus(Long userId, StatusEnum status);

    // SPEC-0002 Slice A: 同园 tenant-aware 单条查询（approve/reject/disable）
    Optional<UserKindergartenMembership> findByUser_IdAndKindergarten_IdAndStatus(
            Long userId,
            Long kindergartenId,
            StatusEnum status);

    // SPEC-0002 Slice A: 条件更新 PENDING→ACTIVE / PENDING→REJECTED（防 TOCTOU）
    @Modifying
    @Query("""
            UPDATE UserKindergartenMembership m
            SET m.status = :newStatus
            WHERE m.user.id = :userId
              AND m.kindergarten.id = :kindergartenId
              AND m.status = :expectedStatus
            """)
    int conditionalUpdateStatus(
            @Param("userId") Long userId,
            @Param("kindergartenId") Long kindergartenId,
            @Param("expectedStatus") StatusEnum expectedStatus,
            @Param("newStatus") StatusEnum newStatus);

    // SPEC-0002 Slice A: 条件更新 ACTIVE→DISABLED（disable 端点，填 leftAt）
    @Modifying
    @Query("""
            UPDATE UserKindergartenMembership m
            SET m.status = :disabledStatus,
                m.leftAt = :leftAt
            WHERE m.user.id = :userId
              AND m.kindergarten.id = :kindergartenId
              AND m.status = :activeStatus
            """)
    int conditionalDisable(
            @Param("userId") Long userId,
            @Param("kindergartenId") Long kindergartenId,
            @Param("leftAt") OffsetDateTime leftAt,
            @Param("activeStatus") StatusEnum activeStatus,
            @Param("disabledStatus") StatusEnum disabledStatus);
}
