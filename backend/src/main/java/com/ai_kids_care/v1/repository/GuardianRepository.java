package com.ai_kids_care.v1.repository;

import com.ai_kids_care.v1.entity.Guardian;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GuardianRepository extends JpaRepository<Guardian, Long> {
}