package com.ai_kids_care.v1.repository;

import com.ai_kids_care.v1.entity.DetectionSession;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DetectionSessionRepository extends JpaRepository<DetectionSession, Long> {
}