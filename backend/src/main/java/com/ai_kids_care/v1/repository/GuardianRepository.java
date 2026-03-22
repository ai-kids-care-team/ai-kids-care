package com.ai_kids_care.v1.repository;

import com.ai_kids_care.v1.entity.Guardian;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface GuardianRepository extends JpaRepository<Guardian, Long> {
    Optional<Guardian> findByUser_Id(Long userId);
}