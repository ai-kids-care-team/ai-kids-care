package com.ai_kids_care.v1.repository;

import com.ai_kids_care.v1.entity.Announcement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AnnouncementRepository extends JpaRepository<Announcement, Long> {

    // AN-READ：平台级广读查询，移除 membership 过滤；任何认证用户可见所有 ACTIVE 公告。
    @Query("""
            select a from Announcement a
            where (:keyword is null or :keyword = '' or a.title like concat('%', :keyword, '%') or a.body like concat('%', :keyword, '%'))
            and (a.status = 'ACTIVE')
            and (a.deletedAt is null or a.deletedAt > CURRENT_TIMESTAMP)
            and (a.publishedAt is null or a.publishedAt <= CURRENT_TIMESTAMP)
            and (a.startsAt is null or a.startsAt <= CURRENT_TIMESTAMP)
            and (a.endsAt is null or a.endsAt >= CURRENT_TIMESTAMP)
            order by a.isPinned desc, a.publishedAt desc, a.id desc
            """)
    Page<Announcement> listActiveAnnouncements(
            @Param("keyword") String keyword,
            Pageable pageable
    );

    // AN-READ：按 ID 查找 ACTIVE 公告（详情读取）；写操作使用 JpaRepository.findById 不限制 status。
    @Query("""
            select a from Announcement a
            where a.id = :id
              and a.status = 'ACTIVE'
              and (a.deletedAt is null or a.deletedAt > CURRENT_TIMESTAMP)
            """)
    Optional<Announcement> findActiveById(@Param("id") Long id);
}
