package com.ai_kids_care.v1.repository;

import com.ai_kids_care.v1.entity.UserRoleAssignment;
import com.ai_kids_care.v1.type.StatusEnum;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserRoleAssignmentRepository extends JpaRepository<UserRoleAssignment, Long> {
    List<UserRoleAssignment> findByUser_IdAndStatus(Long userId, StatusEnum status);
}