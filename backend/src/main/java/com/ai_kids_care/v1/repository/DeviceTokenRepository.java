package com.ai_kids_care.v1.repository;

import com.ai_kids_care.v1.entity.DeviceToken;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeviceTokenRepository extends JpaRepository<DeviceToken, Long> {
}