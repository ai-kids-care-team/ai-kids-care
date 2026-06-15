package com.ai_kids_care.v1.repository;

import com.ai_kids_care.v1.entity.UserRoleAssignment;
import com.ai_kids_care.v1.type.StatusEnum;
import com.ai_kids_care.v1.type.UserRoleAssignmentScopeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface UserRoleAssignmentRepository extends JpaRepository<UserRoleAssignment, Long> {
    List<UserRoleAssignment> findAllByUser_IdAndStatusOrderByGrantedAtDesc(Long userId, StatusEnum status);

    // SPEC-0002 Slice A: 按 kindergarten(scopeId) + status 查 PENDING 申请（list）
    List<UserRoleAssignment> findAllByStatusAndScopeTypeAndScopeId(
            StatusEnum status,
            UserRoleAssignmentScopeType scopeType,
            Long scopeId);

    // SPEC-0002 Slice A: 加载单条 PENDING/ACTIVE role assignment（同园 tenant-aware）
    Optional<UserRoleAssignment> findByUser_IdAndStatusAndScopeTypeAndScopeId(
            Long userId,
            StatusEnum status,
            UserRoleAssignmentScopeType scopeType,
            Long scopeId);

    // SPEC-0002 Slice A: 条件更新 PENDING→ACTIVE（防 TOCTOU）
    @Modifying
    @Query("""
            UPDATE UserRoleAssignment ura
            SET ura.status = :newStatus,
                ura.grantedByUser = (SELECT u FROM User u WHERE u.id = :actorUserId)
            WHERE ura.user.id = :userId
              AND ura.status = :expectedStatus
              AND ura.scopeType = :scopeType
              AND ura.scopeId = :scopeId
            """)
    int conditionalUpdateStatus(
            @Param("userId") Long userId,
            @Param("expectedStatus") StatusEnum expectedStatus,
            @Param("newStatus") StatusEnum newStatus,
            @Param("scopeType") UserRoleAssignmentScopeType scopeType,
            @Param("scopeId") Long scopeId,
            @Param("actorUserId") Long actorUserId);

    // SPEC-0002 Slice A: 条件更新 ACTIVE→DISABLED（disable 端点，填 revokedAt / revokedByUser）
    @Modifying
    @Query("""
            UPDATE UserRoleAssignment ura
            SET ura.status = :disabledStatus,
                ura.revokedAt = :revokedAt,
                ura.revokedByUser = (SELECT u FROM User u WHERE u.id = :actorUserId)
            WHERE ura.user.id = :userId
              AND ura.status = :activeStatus
              AND ura.scopeType = :scopeType
              AND ura.scopeId = :scopeId
            """)
    int conditionalDisable(
            @Param("userId") Long userId,
            @Param("scopeType") UserRoleAssignmentScopeType scopeType,
            @Param("scopeId") Long scopeId,
            @Param("revokedAt") OffsetDateTime revokedAt,
            @Param("actorUserId") Long actorUserId,
            @Param("activeStatus") StatusEnum activeStatus,
            @Param("disabledStatus") StatusEnum disabledStatus);
}
