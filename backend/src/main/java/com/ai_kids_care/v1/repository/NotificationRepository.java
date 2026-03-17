package com.ai_kids_care.v1.repository;

import com.ai_kids_care.v1.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
}