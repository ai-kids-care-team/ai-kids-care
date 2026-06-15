package com.ai_kids_care.v1.repository;

import com.ai_kids_care.v1.entity.Kindergarten;
import com.ai_kids_care.v1.type.StatusEnum;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface KindergartenRepository extends JpaRepository<Kindergarten, Long> {
    Page<Kindergarten> findByNameContains(String keyword, Pageable pageable);

    List<Kindergarten> findByBusinessRegistrationNoContains(String businessRegistrationNo);

    Optional<Kindergarten> findByIdAndStatus(Long id, StatusEnum status);
}
