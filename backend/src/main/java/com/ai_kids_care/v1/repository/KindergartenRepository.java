package com.ai_kids_care.v1.repository;

import com.ai_kids_care.v1.entity.Kindergarten;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KindergartenRepository extends JpaRepository<Kindergarten, Long> {
    Page<Kindergarten> findByNameContains(String keyword, Pageable pageable);
}