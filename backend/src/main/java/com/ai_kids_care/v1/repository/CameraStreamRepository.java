package com.ai_kids_care.v1.repository;

import com.ai_kids_care.v1.entity.CameraStream;
import com.ai_kids_care.v1.internal.ActiveStreamProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CameraStreamRepository extends JpaRepository<CameraStream, Long> {

    /**
     * 列出本部署应消费的活跃流（{@code enabled = true}）供 AI 多摄像头 supervisor 枚举（方案 A）。
     *
     * <p>租户归属（{@code kindergartenId}）由后端从 {@code camera_streams → cctv_cameras} 关系
     * <b>在 JPQL 内投影</b>得出（非加载后过滤），AI 仅得到 {@code (streamId, kindergartenId)}，不含凭据。
     * 「活跃」谓词 {@code cs.enabled = true} 写进查询；{@code modelId} 由 service 另行解析后组装。
     */
    @Query("""
            select new com.ai_kids_care.v1.internal.ActiveStreamProjection(
                cs.id, cs.cctvCameras.kindergarten.id)
            from CameraStream cs
            where cs.enabled = true
            order by cs.id
            """)
    List<ActiveStreamProjection> findActiveStreamsForAi();

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

    Optional<CameraStream> findByIdAndCctvCameras_Kindergarten_Id(
            Long id,
            Long kindergartenId
    );
}
