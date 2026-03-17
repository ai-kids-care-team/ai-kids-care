package com.ai_kids_care.v1.repository;

import com.ai_kids_care.v1.entity.UserRoleAssignment;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRoleAssignmentRepository extends JpaRepository<UserRoleAssignment, Long> {
}