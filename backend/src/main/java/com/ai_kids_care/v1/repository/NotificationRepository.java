package com.ai_kids_care.v1.repository;

import com.ai_kids_care.v1.entity.Notification;
import com.ai_kids_care.v1.type.NotificationStatusEnum;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    // ── SPEC-0001 / ADR-0018 A3d：通知读取——作用域 SQL ─────────────────────────────

    /**
     * 受体读取自己的通知列表（同租户 + 同受体；按时间倒序）。
     */
    @Query("select n from Notification n where n.kindergarten.id = :kindergartenId and n.recipientUser.id = :userId order by n.createdAt desc, n.id desc")
    List<Notification> findRecipientNotifications(@Param("kindergartenId") Long kindergartenId, @Param("userId") Long userId);

    /**
     * 受体读取单条自己的通知（同租户 + 同受体）。
     */
    @Query("select n from Notification n where n.id = :id and n.kindergarten.id = :kindergartenId and n.recipientUser.id = :userId")
    Optional<Notification> findRecipientNotification(@Param("id") Long id, @Param("kindergartenId") Long kindergartenId, @Param("userId") Long userId);

    /**
     * KINDERGARTEN_ADMIN 读取所在园的全部通知列表（按时间倒序）。
     */
    @Query("select n from Notification n where n.kindergarten.id = :kindergartenId order by n.createdAt desc, n.id desc")
    List<Notification> findKindergartenNotifications(@Param("kindergartenId") Long kindergartenId);

    /**
     * KINDERGARTEN_ADMIN 读取所在园的单条通知。
     */
    @Query("select n from Notification n where n.id = :id and n.kindergarten.id = :kindergartenId")
    Optional<Notification> findKindergartenNotification(@Param("id") Long id, @Param("kindergartenId") Long kindergartenId);

    /** ③b: deferred notifications whose quiet-hours delay has elapsed (for the scanner). */
    @Query("select n from Notification n where n.status = :status and n.deferredUntil <= :now")
    List<Notification> findByStatusAndDeferredUntilLessThanEqual(@Param("status") NotificationStatusEnum status,
                                                                 @Param("now") OffsetDateTime now);
}
