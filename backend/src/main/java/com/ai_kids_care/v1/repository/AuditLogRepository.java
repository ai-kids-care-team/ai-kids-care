package com.ai_kids_care.v1.repository;

import com.ai_kids_care.v1.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
}