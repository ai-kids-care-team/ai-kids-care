package com.ai_kids_care.v1.repository;

import com.ai_kids_care.v1.entity.User;
import com.ai_kids_care.v1.entity.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRoleRepository extends JpaRepository<User, Long> {

}