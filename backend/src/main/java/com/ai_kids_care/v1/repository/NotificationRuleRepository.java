package com.ai_kids_care.v1.repository;

import com.ai_kids_care.v1.entity.NotificationRule;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRuleRepository extends JpaRepository<NotificationRule, Long> {
}