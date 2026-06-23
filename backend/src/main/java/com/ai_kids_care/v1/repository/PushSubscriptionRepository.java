package com.ai_kids_care.v1.repository;

import com.ai_kids_care.v1.entity.PushSubscription;
import com.ai_kids_care.v1.type.PushProviderEnum;
import com.ai_kids_care.v1.type.StatusEnum;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PushSubscriptionRepository extends JpaRepository<PushSubscription, Long> {

    /** Resolve a recipient's delivery identities for a given provider/status (used by PUSH dispatch). */
    List<PushSubscription> findByUser_IdAndProviderAndStatus(
            Long userId, PushProviderEnum provider, StatusEnum status);

    /** Self-service list: a user's own subscriptions, newest first. */
    List<PushSubscription> findByUser_IdOrderByIdDesc(Long userId);

    /** Self-service ownership-scoped lookup: the row only if it belongs to the caller (else empty → hidden 404). */
    Optional<PushSubscription> findByIdAndUser_Id(Long id, Long userId);

    /** Duplicate pre-check for a clear 409 (the DB unique (user_id,provider,address) is the backstop). */
    boolean existsByUser_IdAndProviderAndAddress(Long userId, PushProviderEnum provider, String address);
}
