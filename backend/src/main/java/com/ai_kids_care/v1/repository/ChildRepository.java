package com.ai_kids_care.v1.repository;

import com.ai_kids_care.v1.entity.Child;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ChildRepository extends JpaRepository<Child, Long> {

    Page<Child> findByNameContains(String name, Pageable pageable);

    List<Child> findByRrnFirst6(String rrnFirst6);

    // ── SPEC-0001 §3 / §349：Guardian 关系-scoped 读取（镜像 ClassRepository assignment-scoped idiom）──
    // 活跃关系 = guardian 档案 ACTIVE + 关系 end_date 窗（IS NULL 或 >= asOf）+ 同租户；
    // 关系条件写进 EXISTS 子查询，在 SQL 内强制（不做加载后过滤）。

    @Query("""
            select c from Child c
            where c.kindergarten.id = :kindergartenId
              and c.status = 'ACTIVE'
              and exists (
                  select r.id from ChildGuardianRelationship r
                  where r.children = c
                    and r.kindergarten.id = :kindergartenId
                    and r.guardians.user.id = :guardianUserId
                    and r.guardians.status = 'ACTIVE'
                    and (r.endDate is null or r.endDate >= :asOf)
              )
            order by c.id
            """)
    List<Child> findRelatedChildrenForGuardian(
            @Param("kindergartenId") Long kindergartenId,
            @Param("guardianUserId") Long guardianUserId,
            @Param("asOf") LocalDate asOf);

    @Query("""
            select c from Child c
            where c.id = :childId
              and c.kindergarten.id = :kindergartenId
              and c.status = 'ACTIVE'
              and exists (
                  select r.id from ChildGuardianRelationship r
                  where r.children = c
                    and r.kindergarten.id = :kindergartenId
                    and r.guardians.user.id = :guardianUserId
                    and r.guardians.status = 'ACTIVE'
                    and (r.endDate is null or r.endDate >= :asOf)
              )
            """)
    Optional<Child> findRelatedChildForGuardian(
            @Param("childId") Long childId,
            @Param("kindergartenId") Long kindergartenId,
            @Param("guardianUserId") Long guardianUserId,
            @Param("asOf") LocalDate asOf);
}