package com.ai_kids_care.v1.repository;

import com.ai_kids_care.v1.entity.CommonCode;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CommonCodeRepository extends JpaRepository<CommonCode, Long> {

    Page<CommonCode> findByCodeGroupIgnoreCase(String codeGroup, Pageable pageable);

    List<CommonCode> findByCodeGroupIgnoreCaseAndCodeIgnoreCase(String codeGroup, String code);
}