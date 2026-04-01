package com.ai_kids_care.v1.repository;

import com.ai_kids_care.v1.entity.DetectionEvent;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DetectionEventRepository extends JpaRepository<DetectionEvent, Long> {

    @Override
    Page<DetectionEvent> findAll(Pageable pageable);
}