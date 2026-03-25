package com.ai_kids_care.v1.repository;

import com.ai_kids_care.v1.entity.Child;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChildRepository extends JpaRepository<Child, Long> {

    Page<Child> findByNameContains(String name, Pageable pageable);

    List<Child> findByRrnFirst6(String rrnFirst6);
}