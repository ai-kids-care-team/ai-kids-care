package com.ai_kids_care.v1.repository;

import com.ai_kids_care.v1.entity.CameraStream;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CameraStreamRepository extends JpaRepository<CameraStream, Long> {

    @Query("""
            select cs
            from CameraStream cs
            where cs.cctvCameras.kindergarten.id = :kindergartenId
              and (:cameraId is null or cs.cctvCameras.id = :cameraId)
              and (:enabled is null or cs.enabled = :enabled)
              and (:isPrimary is null or cs.isPrimary = :isPrimary)
            """)
    Page<CameraStream> findAllByFilters(
            @Param("kindergartenId") Long kindergartenId,
            @Param("cameraId") Long cameraId,
            @Param("enabled") Boolean enabled,
            @Param("isPrimary") Boolean isPrimary,
            Pageable pageable
    );
}
