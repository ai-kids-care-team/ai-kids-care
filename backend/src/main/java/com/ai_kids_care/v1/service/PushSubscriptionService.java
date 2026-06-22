package com.ai_kids_care.v1.service;

import com.ai_kids_care.v1.mapper.PushSubscriptionMapper;
import com.ai_kids_care.v1.repository.PushSubscriptionRepository;
import com.ai_kids_care.v1.vo.PushSubscriptionVO;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PushSubscriptionService {

    private final PushSubscriptionRepository repository;
    private final PushSubscriptionMapper mapper;

    @PreAuthorize("denyAll()")
    public Page<PushSubscriptionVO> listPushSubscriptions(String keyword, Pageable pageable) {
        // TODO: subscription management API not yet published (see notifications spec)
        return repository.findAll(pageable).map(mapper::toVO);
    }

    @PreAuthorize("denyAll()")
    public PushSubscriptionVO getPushSubscription(Long id) {
        return repository.findById(id).map(mapper::toVO)
                .orElseThrow(() -> new EntityNotFoundException("PushSubscription not found"));
    }
}
