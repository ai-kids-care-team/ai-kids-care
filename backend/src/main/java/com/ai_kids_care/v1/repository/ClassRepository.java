package com.ai_kids_care.v1.repository;

import com.ai_kids_care.v1.entity.Class;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ClassRepository extends JpaRepository<Class, Long> {
    Page<Class> findAllByKindergarten_Id(Long kindergartenId, Pageable pageable);

    Optional<Class> findByIdAndKindergarten_Id(Long id, Long kindergartenId);
}
