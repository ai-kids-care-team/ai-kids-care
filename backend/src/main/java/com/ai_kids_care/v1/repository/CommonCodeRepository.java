package com.ai_kids_care.v1.repository;

import com.ai_kids_care.v1.entity.CommonCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface CommonCodeRepository extends JpaRepository<CommonCode, Long>, JpaSpecificationExecutor<CommonCode> {
}
