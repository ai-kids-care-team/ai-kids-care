package com.ai_kids_care.v1.repository;

import com.ai_kids_care.v1.entity.Teacher;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TeacherRepository extends JpaRepository<Teacher, Long> {
}