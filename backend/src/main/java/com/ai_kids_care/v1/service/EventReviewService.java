package com.ai_kids_care.v1.service;

import com.ai_kids_care.v1.dto.EventReviewCreateDTO;
import com.ai_kids_care.v1.entity.DetectionEvent;
import com.ai_kids_care.v1.entity.EventReview;
import com.ai_kids_care.v1.event.EventReviewedEvent;
import com.ai_kids_care.v1.mapper.EventReviewMapper;
import com.ai_kids_care.v1.repository.DetectionEventRepository;
import com.ai_kids_care.v1.repository.EventReviewRepository;
import com.ai_kids_care.v1.repository.UserRepository;
import com.ai_kids_care.v1.security.EffectiveAuthorizationContext;
import com.ai_kids_care.v1.security.EffectiveAuthorizationContextHolder;
import com.ai_kids_care.v1.type.EventStatusEnum;
import com.ai_kids_care.v1.vo.EventReviewVO;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
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
    private final DetectionEventRepository detectionEventRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Staff confirms a review of a detection event: appends an event_reviews row (reviewer = the
     * session user, from_status = the event's current status) and updates detection_events.status
     * to result_status. Tenant-scoped: the event must be in the caller's active kindergarten
     * (otherwise hidden 404). result_status MUST NOT be OPEN.
     *
     * Step ③a hook: publishes EventReviewedEvent on confirm; GuardianNotificationService notifies
     * guardians AFTER_COMMIT when result_status is ESCALATED (forced) or RESOLVED (with notifyGuardians).
     */
    @Transactional
    @PreAuthorize("@authorizationPolicy.isAllowed(T(com.ai_kids_care.v1.security.AuthorizationAction).EVENT_REVIEW_WRITE)")
    public EventReviewVO confirm(EventReviewCreateDTO dto) {
        EffectiveAuthorizationContext context = EffectiveAuthorizationContextHolder.require();
        Long kindergartenId = EffectiveAuthorizationContextHolder.requireActiveKindergartenId();

        if (dto.getResultStatus() == EventStatusEnum.OPEN) {
            throw new IllegalArgumentException("result_status must not be OPEN");
        }

        DetectionEvent event = detectionEventRepository
                .findByIdAndKindergarten_Id(dto.getEventId(), kindergartenId)
                .orElseThrow(() -> new EntityNotFoundException("Detection event not found"));

        EventReview review = EventReview.builder()
                .detectionEvents(event)
                .kindergarten(event.getKindergarten())
                .user(userRepository.getReferenceById(context.userId()))
                .fromStatus(event.getStatus())
                .resultStatus(dto.getResultStatus())
                .comment(dto.getComment())
                .build();
        EventReview saved = repository.save(review);

        event.setStatus(dto.getResultStatus());
        detectionEventRepository.save(event);

        eventPublisher.publishEvent(new EventReviewedEvent(
                event.getId(), kindergartenId, dto.getResultStatus(),
                event.getRooms().getId(), event.getDetectedAt(),
                dto.getAffectedChildIds(), dto.getNotifyGuardians()));

        return mapper.toVO(saved);
    }

    /** List a detection event's review history, scoped to the caller's active kindergarten. */
    @Transactional(readOnly = true)
    @PreAuthorize("@authorizationPolicy.isAllowed(T(com.ai_kids_care.v1.security.AuthorizationAction).EVENT_REVIEW_READ)")
    public Page<EventReviewVO> listEventReviews(Long eventId, Pageable pageable) {
        Long kindergartenId = EffectiveAuthorizationContextHolder.requireActiveKindergartenId();
        Specification<EventReview> specification = (root, query, cb) ->
                cb.equal(root.get("kindergarten").get("id"), kindergartenId);
        if (eventId != null) {
            specification = specification.and((root, query, cb) ->
                    cb.equal(root.get("detectionEvents").get("id"), eventId));
        }
        return repository.findAll(specification, pageable).map(mapper::toVO);
    }

    /** Single review, scoped to the caller's active kindergarten (cross-tenant hidden as 404). */
    @Transactional(readOnly = true)
    @PreAuthorize("@authorizationPolicy.isAllowed(T(com.ai_kids_care.v1.security.AuthorizationAction).EVENT_REVIEW_READ)")
    public EventReviewVO getEventReview(Long id) {
        Long kindergartenId = EffectiveAuthorizationContextHolder.requireActiveKindergartenId();
        EventReview review = repository.findById(id)
                .filter(r -> r.getKindergarten().getId().equals(kindergartenId))
                .orElseThrow(() -> new EntityNotFoundException("EventReview not found"));
        return mapper.toVO(review);
    }

    // Internal use (step ③ guardian-notify); not published. Resolution of the latest review.
    @PreAuthorize("denyAll()")
    @Transactional(readOnly = true)
    public EventReviewVO getLatestReview(Long eventId) {
        return repository.findTopByDetectionEvents_IdOrderByIdDesc(eventId).map(mapper::toVO)
                .orElseThrow(() -> new EntityNotFoundException("EventReview not found"));
    }
}
