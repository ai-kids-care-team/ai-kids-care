package com.ai_kids_care.v1.repository;

import com.ai_kids_care.v1.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    @Query("""
            select n from Notification n
            where (:keyword is null or :keyword = ''
              or lower(n.title) like lower(concat('%', :keyword, '%'))
              or lower(n.body) like lower(concat('%', :keyword, '%'))
              or lower(n.dedupeKey) like lower(concat('%', :keyword, '%'))
              or str(n.recipientUser.id) like concat('%', :keyword, '%')
              or str(n.detectionEvents.id) like concat('%', :keyword, '%'))
            """)
    Page<Notification> search(@Param("keyword") String keyword, Pageable pageable);

    Optional<Notification> findByKindergarten_IdAndDedupeKey(Long kindergartenId, String dedupeKey);
}