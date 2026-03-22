package com.ai_kids_care.v1.repository;

import com.ai_kids_care.v1.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface UserRepository extends JpaRepository<User, Long> {
    User findByLoginIdOrEmailOrPhone(String loginId, String email, String phone);

    boolean existsByLoginIdOrEmailOrPhone(String loginId, String email, String phone);

    boolean existsByLoginId(String loginId);
}