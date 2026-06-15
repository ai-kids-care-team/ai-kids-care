package com.ai_kids_care.v1.repository;

import com.ai_kids_care.v1.entity.CctvCamera;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CctvCameraRepository extends JpaRepository<CctvCamera, Long> {

    Page<CctvCamera> findByKindergarten_Id(Long id, Pageable pageable);

    Optional<CctvCamera> findByIdAndKindergarten_Id(Long id, Long kindergartenId);
}
