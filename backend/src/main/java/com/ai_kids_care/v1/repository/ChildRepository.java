package com.ai_kids_care.v1.repository;

import com.ai_kids_care.v1.entity.Child;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChildRepository extends JpaRepository<Child, Long> {
    Child findByRrnFirst6AndRrnEncrypted(String rrnFirst6, String rrnEncrypted);

    Page<Child> findByNameContains(String name, Pageable pageable);
}