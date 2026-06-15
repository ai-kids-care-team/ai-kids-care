package com.ai_kids_care.v1.repository;

import com.ai_kids_care.v1.entity.User;
import com.ai_kids_care.v1.entity.UserRoleAssignment;
import com.ai_kids_care.v1.type.StatusEnum;
import com.ai_kids_care.v1.type.UserRoleAssignmentScopeType;
import com.ai_kids_care.v1.type.UserRoleEnum;
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
                ura.grantedByUser = :actor
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
            @Param("actor") User actor);

    // SPEC-0002 Slice A: 条件更新 ACTIVE→DISABLED（disable 端点，填 revokedAt / revokedByUser）
    @Modifying
    @Query("""
            UPDATE UserRoleAssignment ura
            SET ura.status = :disabledStatus,
                ura.revokedAt = :revokedAt,
                ura.revokedByUser = :actor
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
            @Param("actor") User actor,
            @Param("activeStatus") StatusEnum activeStatus,
            @Param("disabledStatus") StatusEnum disabledStatus);

    // SPEC-0002 Slice B: 按 PLATFORM scope + scopeId IS NULL + status + role 查 PENDING 申请（list）
    // 用 @Query 因 Spring Data 命名规则无法表达 "IS NULL" + role 组合条件
    @Query("""
            SELECT ura FROM UserRoleAssignment ura
            WHERE ura.status = :status
              AND ura.scopeType = :scopeType
              AND ura.scopeId IS NULL
              AND ura.role = :role
            """)
    List<UserRoleAssignment> findAllByStatusAndScopeTypeAndScopeIdIsNullAndRole(
            @Param("status") StatusEnum status,
            @Param("scopeType") UserRoleAssignmentScopeType scopeType,
            @Param("role") UserRoleEnum role);

    // SPEC-0002 Slice B: 平台级条件更新 PENDING→ACTIVE/REJECTED（scopeId IS NULL + role=SUPERADMIN，防 TOCTOU）
    // 注意：ura.scopeId = NULL 在 JPQL 中不匹配（NULL != NULL 语义），必须使用 IS NULL
    @Modifying
    @Query("""
            UPDATE UserRoleAssignment ura
            SET ura.status = :newStatus,
                ura.grantedByUser = :actor
            WHERE ura.user.id = :userId
              AND ura.status = :expectedStatus
              AND ura.scopeType = :scopeType
              AND ura.scopeId IS NULL
              AND ura.role = :role
            """)
    int platformConditionalUpdateStatus(
            @Param("userId") Long userId,
            @Param("expectedStatus") StatusEnum expectedStatus,
            @Param("newStatus") StatusEnum newStatus,
            @Param("scopeType") UserRoleAssignmentScopeType scopeType,
            @Param("role") com.ai_kids_care.v1.type.UserRoleEnum role,
            @Param("actor") User actor);

    // SPEC-0002 Slice B: 平台级条件更新 ACTIVE→DISABLED（scopeId IS NULL + role=SUPERADMIN，填 revokedAt / revokedByUser）
    @Modifying
    @Query("""
            UPDATE UserRoleAssignment ura
            SET ura.status = :disabledStatus,
                ura.revokedAt = :revokedAt,
                ura.revokedByUser = :actor
            WHERE ura.user.id = :userId
              AND ura.status = :activeStatus
              AND ura.scopeType = :scopeType
              AND ura.scopeId IS NULL
              AND ura.role = :role
            """)
    int platformConditionalDisable(
            @Param("userId") Long userId,
            @Param("scopeType") UserRoleAssignmentScopeType scopeType,
            @Param("role") com.ai_kids_care.v1.type.UserRoleEnum role,
            @Param("revokedAt") OffsetDateTime revokedAt,
            @Param("actor") User actor,
            @Param("activeStatus") StatusEnum activeStatus,
            @Param("disabledStatus") StatusEnum disabledStatus);
}
