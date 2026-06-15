package com.ai_kids_care.v1.repository;

import com.ai_kids_care.v1.entity.DetectionEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DetectionEventRepository extends JpaRepository<DetectionEvent, Long> {

    Page<DetectionEvent> findByKindergarten_Id(Long kindergartenId, Pageable pageable);

    Optional<DetectionEvent> findByIdAndKindergarten_Id(Long id, Long kindergartenId);
}
