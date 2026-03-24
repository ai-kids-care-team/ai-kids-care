package com.ai_kids_care.v1.repository;

import com.ai_kids_care.v1.entity.UserRoleAssignment;
import com.ai_kids_care.v1.type.StatusEnum;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRoleAssignmentRepository extends JpaRepository<UserRoleAssignment, Long> {
    Optional<UserRoleAssignment> findFirstByUser_IdAndStatusOrderByGrantedAtDesc(Long userId, StatusEnum status);
}