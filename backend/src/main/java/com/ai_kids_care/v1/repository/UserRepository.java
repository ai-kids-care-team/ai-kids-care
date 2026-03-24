package com.ai_kids_care.v1.repository;

import com.ai_kids_care.v1.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {
    User findByLoginIdOrEmailOrPhone(String loginId, String email, String phone);

    boolean existsByLoginIdOrEmailOrPhone(String loginId, String email, String phone);

    boolean existsByLoginId(String loginId);

    boolean existsByEmailIgnoreCase(String email);

//    /** 하이픈 등 제거 후 숫자만 비교 (PostgreSQL {@code regexp_replace}) */
//    @Query(
//            value = "SELECT COUNT(*) FROM users WHERE regexp_replace(COALESCE(phone, ''), '[^0-9]', '', 'g') = :digits AND LENGTH(:digits) > 0",
//            nativeQuery = true
//    )
//    long countByPhoneDigitsOnly(@Param("digits") String digits);

    boolean existsByPhone(String phone);
}