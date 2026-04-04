package com.ai_kids_care.v1.repository;

import com.ai_kids_care.v1.entity.EventReview;
import com.ai_kids_care.v1.type.EventStatusEnum;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface EventReviewRepository extends JpaRepository<EventReview, Long> {

    Optional<EventReview> findTopByDetectionEvents_IdOrderByIdDesc(Long eventId);

    @Query("""
            select er
            from EventReview er
            where (:eventId is null or er.detectionEvents.id = :eventId)
              and (:userId is null or er.user.id = :userId)
              and (:fromStatus is null or er.fromStatus = :fromStatus)
              and (:resultStatus is null or er.resultStatus = :resultStatus)
            """)
    Page<EventReview> findAllByFilters(
            @Param("eventId") Long eventId,
            @Param("userId") Long userId,
            @Param("fromStatus") EventStatusEnum fromStatus,
            @Param("resultStatus") EventStatusEnum resultStatus,
            Pageable pageable
    );
}