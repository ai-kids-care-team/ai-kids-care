package com.ai_kids_care.v1.repository;

import com.ai_kids_care.v1.entity.Kindergarten;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface KindergartenRepository extends JpaRepository<Kindergarten, Long> {
    Page<Kindergarten> findByNameContains(String keyword, Pageable pageable);

    /** 숫자만 추출한 사업자등록번호(10자리)로 일치 검색 — DB 저장 형식(하이픈 등) 무관 */
    @Query(
            value = "SELECT * FROM public.kindergartens k "
                    + "WHERE regexp_replace(COALESCE(k.business_registration_no, ''), '[^0-9]', '', 'g') = :digits",
            nativeQuery = true
    )
    List<Kindergarten> findByBusinessRegistrationDigits(@Param("digits") String digits);
}