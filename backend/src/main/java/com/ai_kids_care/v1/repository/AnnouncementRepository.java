package com.ai_kids_care.v1.repository;

import com.ai_kids_care.v1.entity.Announcement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AnnouncementRepository extends JpaRepository<Announcement, Long> {

    @Query("""
            select a from Announcement a
            where (:keyword is null or :keyword = '' or a.title like concat('%', :keyword, '%') or a.body like concat('%', :keyword, '%'))
            and (a.status = 'ACTIVE')
            and (a.deletedAt is null or a.deletedAt > CURRENT_TIMESTAMP)
            and (a.publishedAt is null or a.publishedAt <= CURRENT_TIMESTAMP)
            and (a.startsAt is null or a.startsAt <= CURRENT_TIMESTAMP)
            and (a.endsAt is null or a.endsAt >= CURRENT_TIMESTAMP)
            """)
    Page<Announcement> listActiveAnnouncements(@Param("keyword") String keyword, Pageable pageable);

}
