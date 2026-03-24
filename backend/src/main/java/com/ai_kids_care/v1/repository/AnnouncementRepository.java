package com.ai_kids_care.v1.repository;

import com.ai_kids_care.v1.entity.Announcement;
import com.ai_kids_care.v1.type.StatusEnum;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AnnouncementRepository extends JpaRepository<Announcement, Long> {

    @Query("""
            SELECT a FROM Announcement a
             WHERE (a.deletedAt IS NULL OR a.deletedAt > CURRENT_TIMESTAMP)
               AND a.status = :activeStatus
               AND (a.publishedAt IS NULL OR a.publishedAt <= CURRENT_TIMESTAMP)
               AND (a.startsAt IS NULL OR a.startsAt <= CURRENT_TIMESTAMP)
               AND (a.endsAt IS NULL OR a.endsAt >= CURRENT_TIMESTAMP)
             ORDER BY a.createdAt DESC, a.id DESC
            """)
    Page<Announcement> findPublishedActive(@Param("activeStatus") StatusEnum activeStatus, Pageable pageable);

    @Query("""
            SELECT a FROM Announcement a
             WHERE (a.deletedAt IS NULL OR a.deletedAt > CURRENT_TIMESTAMP)
               AND a.status = :activeStatus
               AND (a.publishedAt IS NULL OR a.publishedAt <= CURRENT_TIMESTAMP)
               AND (a.startsAt IS NULL OR a.startsAt <= CURRENT_TIMESTAMP)
               AND (a.endsAt IS NULL OR a.endsAt >= CURRENT_TIMESTAMP)
               AND (LOWER(a.title) LIKE LOWER(CONCAT('%', :keyword, '%'))
                    OR LOWER(a.body) LIKE LOWER(CONCAT('%', :keyword, '%')))
             ORDER BY a.createdAt DESC, a.id DESC
            """)
    Page<Announcement> findPublishedActiveByKeyword(
            @Param("keyword") String keyword,
            @Param("activeStatus") StatusEnum activeStatus,
            Pageable pageable);
}
