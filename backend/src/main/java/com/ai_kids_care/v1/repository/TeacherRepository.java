package com.ai_kids_care.v1.repository;

import com.ai_kids_care.v1.entity.Teacher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TeacherRepository extends JpaRepository<Teacher, Long> {
    Page<Teacher> findByNameContains(String keyword, Pageable pageable);
    Page<Teacher> findByNameContainsAndUserId(String keyword, Long userId, Pageable pageable);
    Page<Teacher> findByUserId(Long userId, Pageable pageable);
    Optional<Teacher> findByUserId(Long userId);
}
