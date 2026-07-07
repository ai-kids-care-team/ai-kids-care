package com.ai_kids_care.v1.repository;

import com.ai_kids_care.v1.entity.Notification;
import com.ai_kids_care.v1.type.NotificationStatusEnum;
import org.springframework.data.jpa.repository.Modifying;
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

    // ── wire-notification-read-state / D3：mark-read + unread-count ────────────────

    /**
     * 归属判定（决定 404 vs 200）：同租户 + 本人收件人 + 该 id 存在。与是否已读无关——
     * 用它先判「越权/跨租户/不存在」，再执行幂等 UPDATE，避免把「已读」误判成「不存在」。
     */
    boolean existsByIdAndKindergarten_IdAndRecipientUser_Id(Long id, Long kindergartenId, Long recipientUserId);

    /**
     * 幂等置位：仅本人 + 同租户 + 尚未读 的行才会被置位；已读再调返回 0（no-op，由调用方转 200）。
     * 谓词全部写进 JPQL（禁「加载后过滤」）。
     */
    @Modifying
    @Query("""
            update Notification n
               set n.readAt = CURRENT_TIMESTAMP
             where n.id = :id
               and n.recipientUser.id = :recipientUserId
               and n.kindergarten.id = :kindergartenId
               and n.readAt is null
            """)
    int markRead(@Param("id") Long id,
                 @Param("kindergartenId") Long kindergartenId,
                 @Param("recipientUserId") Long recipientUserId);

    /**
     * 未读徽标：永远按本人（recipient_user_id=:me），即便 KINDERGARTEN_ADMIN 全园可读列表。
     */
    long countByKindergarten_IdAndRecipientUser_IdAndReadAtIsNull(Long kindergartenId, Long recipientUserId);
}
