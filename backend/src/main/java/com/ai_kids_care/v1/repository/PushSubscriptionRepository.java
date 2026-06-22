package com.ai_kids_care.v1.repository;

import com.ai_kids_care.v1.entity.PushSubscription;
import com.ai_kids_care.v1.type.PushProviderEnum;
import com.ai_kids_care.v1.type.StatusEnum;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PushSubscriptionRepository extends JpaRepository<PushSubscription, Long> {

    /** Resolve a recipient's delivery identities for a given provider/status (used by PUSH dispatch). */
    List<PushSubscription> findByUser_IdAndProviderAndStatus(
            Long userId, PushProviderEnum provider, StatusEnum status);
}
