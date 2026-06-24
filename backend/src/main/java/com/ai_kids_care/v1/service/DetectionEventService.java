package com.ai_kids_care.v1.service;

import com.ai_kids_care.v1.mapper.DetectionEventMapper;
import com.ai_kids_care.v1.repository.DetectionEventRepository;
import com.ai_kids_care.v1.security.EffectiveAuthorizationContextHolder;
import com.ai_kids_care.v1.vo.DetectionEventVO;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ⑥ 看板的检测事件读取。tenant-scoped(经 {@link EffectiveAuthorizationContextHolder} 的 active
 * kindergarten —— 调用方不传 kindergartenId)、staff-only(KINDERGARTEN_ADMIN/TEACHER,
 * {@code DETECTION_EVENT_READ})。镜像 {@code DetectionSessionService} 的范式。
 */
@Service
@RequiredArgsConstructor
public class DetectionEventService {

    private final DetectionEventRepository repository;
    private final DetectionEventMapper mapper;

    @Transactional(readOnly = true)
    @PreAuthorize("@authorizationPolicy.isAllowed(T(com.ai_kids_care.v1.security.AuthorizationAction).DETECTION_EVENT_READ)")
    public Page<DetectionEventVO> listDetectionEvents(String keyword, Pageable pageable) {
        // TODO: filter DetectionEvent by keyword
        Long kindergartenId = EffectiveAuthorizationContextHolder.requireActiveKindergartenId();
        return repository.findByKindergarten_Id(kindergartenId, pageable).map(mapper::toVO);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@authorizationPolicy.isAllowed(T(com.ai_kids_care.v1.security.AuthorizationAction).DETECTION_EVENT_READ)")
    public DetectionEventVO getDetectionEvent(Long id) {
        Long kindergartenId = EffectiveAuthorizationContextHolder.requireActiveKindergartenId();
        return repository.findByIdAndKindergarten_Id(id, kindergartenId).map(mapper::toVO)
                .orElseThrow(() -> new EntityNotFoundException("DetectionEvent not found"));
    }

    /**
     * Internal lookup for the SSE push path — NOT user-facing, so NO {@code @PreAuthorize}: it runs
     * on an {@code @Async} listener thread with no security context, and the {@code kindergartenId}
     * comes from the trusted ingest event (not user input). Tenant scope is still enforced by the
     * {@code (id, kindergartenId)} query. Returns {@code null} if the event is absent for that KG.
     */
    @Transactional(readOnly = true)
    public DetectionEventVO getForPush(Long eventId, Long kindergartenId) {
        return repository.findByIdAndKindergarten_Id(eventId, kindergartenId).map(mapper::toVO).orElse(null);
    }
}
