package com.ai_kids_care.v1.repository;

import com.ai_kids_care.v1.entity.Superadmin;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SuperadminRepository extends JpaRepository<Superadmin, Long> {
    Optional<Superadmin> findByUser_Id(Long userId);
}