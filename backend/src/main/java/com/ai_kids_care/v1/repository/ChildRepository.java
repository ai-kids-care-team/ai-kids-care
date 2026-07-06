package com.ai_kids_care.v1.repository;

import com.ai_kids_care.v1.entity.Child;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ChildRepository extends JpaRepository<Child, Long> {

    Optional<Child> findByRrnHash(String rrnHash);

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

    // ── SPEC-0001 §351：Teacher assignment-scoped 读取（镜像 RoomRepository 的嵌套 EXISTS 链）──
    // 活跃分配 = ChildClassAssignment ACTIVE + 日期窗 + ClassTeacherAssignment ACTIVE + 日期窗 + teacher 档案 ACTIVE；
    // 两个 EXISTS 子查询嵌套在 SQL 内强制，不做加载后过滤。

    @Query("""
            select c from Child c
            where c.kindergarten.id = :kindergartenId
              and c.status = 'ACTIVE'
              and exists (
                  select cca.id from ChildClassAssignment cca
                  where cca.children = c
                    and cca.status = 'ACTIVE'
                    and cca.startDate <= :asOf
                    and (cca.endDate is null or cca.endDate >= :asOf)
                    and exists (
                        select cta.id from ClassTeacherAssignment cta
                        where cta.classes = cca.classes
                          and cta.teachers.user.id = :teacherUserId
                          and cta.teachers.status = 'ACTIVE'
                          and cta.status = 'ACTIVE'
                          and cta.startDate <= :asOf
                          and (cta.endDate is null or cta.endDate >= :asOf)
                    )
              )
            order by c.id
            """)
    List<Child> findActivelyAssignedChildrenForTeacher(
            @Param("kindergartenId") Long kindergartenId,
            @Param("teacherUserId") Long teacherUserId,
            @Param("asOf") LocalDate asOf);

    @Query("""
            select c from Child c
            where c.id = :childId
              and c.kindergarten.id = :kindergartenId
              and c.status = 'ACTIVE'
              and exists (
                  select cca.id from ChildClassAssignment cca
                  where cca.children = c
                    and cca.status = 'ACTIVE'
                    and cca.startDate <= :asOf
                    and (cca.endDate is null or cca.endDate >= :asOf)
                    and exists (
                        select cta.id from ClassTeacherAssignment cta
                        where cta.classes = cca.classes
                          and cta.teachers.user.id = :teacherUserId
                          and cta.teachers.status = 'ACTIVE'
                          and cta.status = 'ACTIVE'
                          and cta.startDate <= :asOf
                          and (cta.endDate is null or cta.endDate >= :asOf)
                    )
              )
            """)
    Optional<Child> findActivelyAssignedChildForTeacher(
            @Param("childId") Long childId,
            @Param("kindergartenId") Long kindergartenId,
            @Param("teacherUserId") Long teacherUserId,
            @Param("asOf") LocalDate asOf);
}