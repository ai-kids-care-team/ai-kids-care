package com.ai_kids_care.v1.service;

import com.ai_kids_care.v1.dto.PushSubscriptionRegisterDTO;
import com.ai_kids_care.v1.dto.PushSubscriptionUpdateDTO;
import com.ai_kids_care.v1.entity.PushSubscription;
import com.ai_kids_care.v1.mapper.PushSubscriptionMapper;
import com.ai_kids_care.v1.repository.PushSubscriptionRepository;
import com.ai_kids_care.v1.repository.UserRepository;
import com.ai_kids_care.v1.security.EffectiveAuthorizationContextHolder;
import com.ai_kids_care.v1.type.PushProviderEnum;
import com.ai_kids_care.v1.type.StatusEnum;
import com.ai_kids_care.v1.vo.PushSubscriptionVO;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * Self-service management of the caller's own push delivery subscriptions (Pushover user keys),
 * so the PUSH delivery primitive ({@link NotificationService#dispatch}) has an address to target.
 *
 * Coarse gate: {@code PUSH_SUBSCRIPTION_MANAGE} (any authenticated user). Fine-grained scope: every
 * operation derives {@code user_id} from the session identity and queries are user-scoped, so a
 * caller can only see/modify/delete their own rows (cross-user access → hidden 404).
 */
@Service
@RequiredArgsConstructor
public class PushSubscriptionService {

    private final PushSubscriptionRepository repository;
    private final PushSubscriptionMapper mapper;
    private final UserRepository userRepository;

    @Transactional
    @PreAuthorize("@authorizationPolicy.isAllowed(T(com.ai_kids_care.v1.security.AuthorizationAction).PUSH_SUBSCRIPTION_MANAGE)")
    public PushSubscriptionVO register(PushSubscriptionRegisterDTO dto) {
        Long userId = EffectiveAuthorizationContextHolder.require().userId();
        PushProviderEnum provider = parseProvider(dto.getProvider());

        if (repository.existsByUser_IdAndProviderAndAddress(userId, provider, dto.getAddress())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Push subscription already exists");
        }

        PushSubscription entity = PushSubscription.builder()
                .user(userRepository.getReferenceById(userId))
                .provider(provider)
                .address(dto.getAddress())
                .deviceLabel(dto.getDeviceLabel())
                .status(StatusEnum.ACTIVE)
                .build();
        try {
            return mapper.toVO(repository.saveAndFlush(entity));
        } catch (DataIntegrityViolationException e) {
            // Backstop for the unique (user_id, provider, address) constraint under a race.
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Push subscription already exists");
        }
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@authorizationPolicy.isAllowed(T(com.ai_kids_care.v1.security.AuthorizationAction).PUSH_SUBSCRIPTION_MANAGE)")
    public List<PushSubscriptionVO> listOwn() {
        Long userId = EffectiveAuthorizationContextHolder.require().userId();
        return repository.findByUser_IdOrderByIdDesc(userId).stream().map(mapper::toVO).toList();
    }

    @Transactional
    @PreAuthorize("@authorizationPolicy.isAllowed(T(com.ai_kids_care.v1.security.AuthorizationAction).PUSH_SUBSCRIPTION_MANAGE)")
    public PushSubscriptionVO update(Long id, PushSubscriptionUpdateDTO dto) {
        PushSubscription entity = requireOwned(id);
        if (StringUtils.hasText(dto.getAddress())) {
            entity.setAddress(dto.getAddress());
        }
        if (StringUtils.hasText(dto.getDeviceLabel())) {
            // patch semantics consistent with address/status: null/blank = no change
            entity.setDeviceLabel(dto.getDeviceLabel());
        }
        if (StringUtils.hasText(dto.getStatus())) {
            entity.setStatus(parseStatus(dto.getStatus()));
        }
        return mapper.toVO(repository.save(entity));
    }

    @Transactional
    @PreAuthorize("@authorizationPolicy.isAllowed(T(com.ai_kids_care.v1.security.AuthorizationAction).PUSH_SUBSCRIPTION_MANAGE)")
    public void delete(Long id) {
        repository.delete(requireOwned(id));
    }

    /** Ownership-scoped lookup: only the caller's own row; otherwise hidden 404. */
    private PushSubscription requireOwned(Long id) {
        Long userId = EffectiveAuthorizationContextHolder.require().userId();
        return repository.findByIdAndUser_Id(id, userId)
                .orElseThrow(() -> new EntityNotFoundException("PushSubscription not found"));
    }

    private PushProviderEnum parseProvider(String raw) {
        if (!StringUtils.hasText(raw)) {
            return PushProviderEnum.PUSHOVER; // default + only supported provider
        }
        try {
            return PushProviderEnum.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unsupported push provider: " + raw);
        }
    }

    private StatusEnum parseStatus(String raw) {
        try {
            return StatusEnum.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid status: " + raw);
        }
    }
}
