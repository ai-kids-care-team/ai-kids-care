package com.ai_kids_care.v1.repository;

import com.ai_kids_care.v1.entity.EventReview;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface EventReviewRepository extends JpaRepository<EventReview, Long>, JpaSpecificationExecutor<EventReview> {
}
