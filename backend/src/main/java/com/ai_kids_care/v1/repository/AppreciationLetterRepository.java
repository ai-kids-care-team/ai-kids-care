package com.ai_kids_care.v1.repository;

import com.ai_kids_care.v1.entity.AppreciationLetter;
import com.ai_kids_care.v1.type.AppreciationTargetTypeEnum;
import com.ai_kids_care.v1.type.StatusEnum;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AppreciationLetterRepository extends JpaRepository<AppreciationLetter, Long> {

    // ── GUARDIAN：可见 = 本人所写 + 同租户 is_public=true（ACTIVE 仅）─────────────────

    @Query("""
            select l from AppreciationLetter l
            where l.kindergarten.id = :kgId
              and l.status = :activeStatus
              and (l.senderUser.id = :userId or l.isPublic = true)
              and (:keyword is null
                   or lower(l.title) like lower(concat('%', :keyword, '%'))
                   or lower(l.content) like lower(concat('%', :keyword, '%')))
            order by l.createdAt desc, l.id desc
            """)
    Page<AppreciationLetter> findVisibleForGuardian(
            @Param("kgId") Long kindergartenId,
            @Param("userId") Long userId,
            @Param("keyword") String keyword,
            @Param("activeStatus") StatusEnum activeStatus,
            Pageable pageable);

    @Query("""
            select l from AppreciationLetter l
            where l.id = :id
              and l.kindergarten.id = :kgId
              and l.status = :activeStatus
              and (l.senderUser.id = :userId or l.isPublic = true)
            """)
    Optional<AppreciationLetter> findVisibleForGuardian(
            @Param("id") Long id,
            @Param("kgId") Long kgId,
            @Param("userId") Long userId,
            @Param("activeStatus") StatusEnum activeStatus);

    // ── TEACHER：可见 = is_public=true + 发给本人（target_type=TEACHER, target_id=teacherId）──

    @Query("""
            select l from AppreciationLetter l
            where l.kindergarten.id = :kgId
              and l.status = :activeStatus
              and (l.isPublic = true
                   or (l.targetType = :teacherType and l.targetId = :teacherId))
              and (:keyword is null
                   or lower(l.title) like lower(concat('%', :keyword, '%'))
                   or lower(l.content) like lower(concat('%', :keyword, '%')))
            order by l.createdAt desc, l.id desc
            """)
    Page<AppreciationLetter> findVisibleForTeacher(
            @Param("kgId") Long kindergartenId,
            @Param("teacherId") Long teacherId,
            @Param("keyword") String keyword,
            @Param("activeStatus") StatusEnum activeStatus,
            @Param("teacherType") AppreciationTargetTypeEnum teacherType,
            Pageable pageable);

    @Query("""
            select l from AppreciationLetter l
            where l.id = :id
              and l.kindergarten.id = :kgId
              and l.status = :activeStatus
              and (l.isPublic = true
                   or (l.targetType = :teacherType and l.targetId = :teacherId))
            """)
    Optional<AppreciationLetter> findVisibleForTeacher(
            @Param("id") Long id,
            @Param("kgId") Long kgId,
            @Param("teacherId") Long teacherId,
            @Param("activeStatus") StatusEnum activeStatus,
            @Param("teacherType") AppreciationTargetTypeEnum teacherType);

    // ── KINDERGARTEN_ADMIN：本租户全部 ACTIVE ──────────────────────────────────────────

    @Query("""
            select l from AppreciationLetter l
            where l.kindergarten.id = :kgId
              and l.status = :activeStatus
              and (:keyword is null
                   or lower(l.title) like lower(concat('%', :keyword, '%'))
                   or lower(l.content) like lower(concat('%', :keyword, '%')))
            order by l.createdAt desc, l.id desc
            """)
    Page<AppreciationLetter> findAllForAdmin(
            @Param("kgId") Long kindergartenId,
            @Param("keyword") String keyword,
            @Param("activeStatus") StatusEnum activeStatus,
            Pageable pageable);

    @Query("""
            select l from AppreciationLetter l
            where l.id = :id
              and l.kindergarten.id = :kgId
              and l.status = :activeStatus
            """)
    Optional<AppreciationLetter> findForAdmin(
            @Param("id") Long id,
            @Param("kgId") Long kgId,
            @Param("activeStatus") StatusEnum activeStatus);

    // ── 作者所有权检查（update / delete 用）──────────────────────────────────────────────

    @Query("""
            select l from AppreciationLetter l
            where l.id = :id
              and l.kindergarten.id = :kgId
              and l.senderUser.id = :userId
              and l.status = :activeStatus
            """)
    Optional<AppreciationLetter> findOwnedByAuthor(
            @Param("id") Long id,
            @Param("kgId") Long kgId,
            @Param("userId") Long userId,
            @Param("activeStatus") StatusEnum activeStatus);
}
