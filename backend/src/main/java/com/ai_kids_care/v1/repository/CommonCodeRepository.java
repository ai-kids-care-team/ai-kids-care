package com.ai_kids_care.v1.repository;

import com.ai_kids_care.v1.entity.CommonCode;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CommonCodeRepository extends JpaRepository<CommonCode, Long> {

    @Query("""
            select cc
            from CommonCode cc
            where (:codeGroup is null or lower(cc.codeGroup) = lower(:codeGroup))
              and (:code is null or lower(cc.code) = lower(:code))
              and (:parentCode is null or lower(cc.parentCode) = lower(:parentCode))
              and (:isActive is null or cc.isActive = :isActive)
            """)
    Page<CommonCode> findAllByFilters(
            @Param("codeGroup") String codeGroup,
            @Param("code") String code,
            @Param("parentCode") String parentCode,
            @Param("isActive") Boolean isActive,
            Pageable pageable
    );
}
