package com.ai_kids_care.v1.repository;

import com.ai_kids_care.v1.entity.DetectionSession;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DetectionSessionRepository extends JpaRepository<DetectionSession, Long> {
    Page<DetectionSession> findAllByCameraStreams_CctvCameras_Kindergarten_Id(
            Long kindergartenId,
            Pageable pageable
    );

    Optional<DetectionSession> findByIdAndCameraStreams_CctvCameras_Kindergarten_Id(
            Long id,
            Long kindergartenId
    );
}
