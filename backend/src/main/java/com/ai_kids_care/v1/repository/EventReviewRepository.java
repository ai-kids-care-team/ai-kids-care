package com.ai_kids_care.v1.repository;

import com.ai_kids_care.v1.entity.Child;
import com.ai_kids_care.v1.entity.EventReview;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface EventReviewRepository extends JpaRepository<EventReview, Long> {
    Optional<EventReview> findTopByDetectionEvents_IdOrderByIdDesc(Long eventId );

    List<EventReview> findAllByDetectionEvents_IdOrderByIdAsc(Long eventId);
}