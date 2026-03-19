package com.ai_kids_care.v1.repository;

import com.ai_kids_care.v1.entity.AiModel;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiModelRepository extends JpaRepository<AiModel, Long> {
}