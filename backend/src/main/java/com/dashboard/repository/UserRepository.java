package com.dashboard.repository;

import com.dashboard.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByLoginId(String loginId);

    boolean existsByLoginId(String loginId);
    boolean existsByEmail(String email);

    @Query("SELECT COALESCE(MAX(u.userId), 0) FROM User u")
    Long findMaxUserId();

    @Query(value = """
            SELECT ura.role
            FROM user_role_assignments ura
            WHERE ura.user_id = :userId
            ORDER BY ura.granted_at DESC NULLS LAST, ura.role_assignment_id DESC
            LIMIT 1
            """, nativeQuery = true)
    Optional<String> findLatestRoleByUserId(@Param("userId") Long userId);
}
