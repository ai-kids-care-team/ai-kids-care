package com.ai_kids_care.v1.repository;

import com.ai_kids_care.v1.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {
}