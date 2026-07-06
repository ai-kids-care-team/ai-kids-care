package com.ai_kids_care.v1.repository;

import com.ai_kids_care.v1.entity.NotificationRule;
import com.ai_kids_care.v1.type.NotificationTargetType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface NotificationRuleRepository extends JpaRepository<NotificationRule, Long> {

    /**
     * UX-08: the caller's canonical per-user preference row (one per (kindergarten_id, user_id,
     * target_type=KINDERGARTEN)). Both tenant and self-ownership predicates are in the JPQL —
     * never load-then-filter (security.md invariant).
     */
    @Query("select r from NotificationRule r "
            + "where r.kindergarten.id = :kindergartenId "
            + "and r.user.id = :userId "
            + "and r.targetType = :targetType")
    Optional<NotificationRule> findCanonical(
            @Param("kindergartenId") Long kindergartenId,
            @Param("userId") Long userId,
            @Param("targetType") NotificationTargetType targetType);
}