package com.ai_kids_care.v1.service;

import com.ai_kids_care.v1.entity.EventReview;
import com.ai_kids_care.v1.mapper.EventReviewMapper;
import com.ai_kids_care.v1.repository.EventReviewRepository;
import com.ai_kids_care.v1.type.EventStatusEnum;
import com.ai_kids_care.v1.vo.EventReviewVO;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EventReviewService {

    private final EventReviewRepository repository;
    private final EventReviewMapper mapper;

    // 此 Service 尚未接入任何 live Controller。
    // 默认拒绝所有调用，防止将来误用绕过授权。
    // 接入时须先通过 SPEC 定义 AuthorizationAction 并替换此注解。
    @PreAuthorize("denyAll()")
    @Transactional(readOnly = true)
    public Page<EventReviewVO> listEventReviews(
            Long eventId,
            Long userId,
            EventStatusEnum fromStatus,
            EventStatusEnum resultStatus,
            Pageable pageable
    ) {
        Specification<EventReview> specification = Specification.where(null);

        if (eventId != null) {
            specification = specification.and((root, query, cb) ->
                    cb.equal(root.get("detectionEvents").get("id"), eventId));
        }

        if (userId != null) {
            specification = specification.and((root, query, cb) ->
                    cb.equal(root.get("user").get("id"), userId));
        }

        if (fromStatus != null) {
            specification = specification.and((root, query, cb) ->
                    cb.equal(root.get("fromStatus"), fromStatus));
        }

        if (resultStatus != null) {
            specification = specification.and((root, query, cb) ->
                    cb.equal(root.get("resultStatus"), resultStatus));
        }

        return repository.findAll(specification, pageable)
                .map(mapper::toVO);
    }

    // 此 Service 尚未接入任何 live Controller。
    // 默认拒绝所有调用，防止将来误用绕过授权。
    // 接入时须先通过 SPEC 定义 AuthorizationAction 并替换此注解。
    @PreAuthorize("denyAll()")
    @Transactional(readOnly = true)
    public EventReviewVO getEventReview(Long id) {
        return repository.findById(id).map(mapper::toVO)
                .orElseThrow(() -> new EntityNotFoundException("EventReview not found"));
    }

    // 此 Service 尚未接入任何 live Controller。
    // 默认拒绝所有调用，防止将来误用绕过授权。
    // 接入时须先通过 SPEC 定义 AuthorizationAction 并替换此注解。
    @PreAuthorize("denyAll()")
    @Transactional(readOnly = true)
    public EventReviewVO getLatestReview(Long eventId) {
        return repository.findTopByDetectionEvents_IdOrderByIdDesc(eventId).map(mapper::toVO)
                .orElseThrow(() -> new EntityNotFoundException("EventReview not found"));
    }
}
