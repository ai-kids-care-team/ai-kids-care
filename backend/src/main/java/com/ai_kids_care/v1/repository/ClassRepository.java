package com.ai_kids_care.v1.repository;

import com.ai_kids_care.v1.entity.Class;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;

public interface ClassRepository extends JpaRepository<Class, Long> {
    Page<Class> findAllByKindergarten_Id(Long kindergartenId, Pageable pageable);

    Optional<Class> findByIdAndKindergarten_Id(Long id, Long kindergartenId);

    /**
     * Classes in the tenant that the given user covers through an effective teacher
     * assignment: the teacher profile and the assignment are both ACTIVE and the
     * assignment date window contains {@code asOf} (ADR-0019 §7).
     */
    @Query("""
            select c from Class c
            where c.kindergarten.id = :kindergartenId
              and exists (
                  select a.id from ClassTeacherAssignment a
                  where a.classes = c
                    and a.teachers.user.id = :userId
                    and a.teachers.status = 'ACTIVE'
                    and a.status = 'ACTIVE'
                    and a.startDate <= :asOf
                    and (a.endDate is null or a.endDate >= :asOf)
              )
            """)
    Page<Class> findActivelyAssignedClassesForTeacher(
            @Param("kindergartenId") Long kindergartenId,
            @Param("userId") Long userId,
            @Param("asOf") LocalDate asOf,
            Pageable pageable);

    @Query("""
            select c from Class c
            where c.id = :id
              and c.kindergarten.id = :kindergartenId
              and exists (
                  select a.id from ClassTeacherAssignment a
                  where a.classes = c
                    and a.teachers.user.id = :userId
                    and a.teachers.status = 'ACTIVE'
                    and a.status = 'ACTIVE'
                    and a.startDate <= :asOf
                    and (a.endDate is null or a.endDate >= :asOf)
              )
            """)
    Optional<Class> findActivelyAssignedClassForTeacher(
            @Param("id") Long id,
            @Param("kindergartenId") Long kindergartenId,
            @Param("userId") Long userId,
            @Param("asOf") LocalDate asOf);
}
