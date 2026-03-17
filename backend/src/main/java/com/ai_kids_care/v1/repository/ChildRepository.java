package com.ai_kids_care.v1.repository;

import com.ai_kids_care.v1.entity.Child;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChildRepository extends JpaRepository<Child, Long> {
}