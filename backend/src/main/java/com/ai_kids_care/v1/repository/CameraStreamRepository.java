package com.ai_kids_care.v1.repository;

import com.ai_kids_care.v1.entity.CameraStream;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CameraStreamRepository extends JpaRepository<CameraStream, Long> {
}