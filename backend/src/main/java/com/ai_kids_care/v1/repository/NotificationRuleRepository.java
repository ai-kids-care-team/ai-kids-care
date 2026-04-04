package com.ai_kids_care.v1.repository;

import com.ai_kids_care.v1.entity.NotificationRule;
import com.ai_kids_care.v1.type.EventTypeEnum;
import com.ai_kids_care.v1.type.NotificationTargetType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRuleRepository extends JpaRepository<NotificationRule, Long> {
    @Query("""
            select r from NotificationRule r
            where (:userId is null or r.user.id = :userId)
              and (:targetType is null or r.targetType = :targetType)
              and (:targetId is null or r.targetId = :targetId)
              and (:eventType is null or r.eventType = :eventType)
              and (:minSeverity is null or r.minSeverity = :minSeverity)
              and (:enabled is null or r.enabled = :enabled)
            """)
    Page<NotificationRule> searchByFilters(
            @Param("userId") Long userId,
            @Param("targetType") NotificationTargetType targetType,
            @Param("targetId") Long targetId,
            @Param("eventType") EventTypeEnum eventType,
            @Param("minSeverity") Integer minSeverity,
            @Param("enabled") Boolean enabled,
            Pageable pageable
    );
}