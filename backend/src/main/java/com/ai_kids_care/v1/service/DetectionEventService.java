package com.ai_kids_care.v1.service;

import com.ai_kids_care.v1.entity.DetectionEvent;
import com.ai_kids_care.v1.mapper.DetectionEventMapper;
import com.ai_kids_care.v1.repository.DetectionEventRepository;
import com.ai_kids_care.v1.security.EffectiveAuthorizationContextHolder;
import com.ai_kids_care.v1.type.EventTypeEnum;
import com.ai_kids_care.v1.vo.DetectionEventVO;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

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
        Long kindergartenId = EffectiveAuthorizationContextHolder.requireActiveKindergartenId();
        // Normalize whitespace-only keywords to null so both the LIKE branch (":keyword is null or
        // :keyword = ''") and the event-type resolution below treat "no meaningful keyword" the same
        // way -- a keyword of all spaces is not equal to '' and would otherwise silently match nothing.
        String normalizedKeyword = StringUtils.hasText(keyword) ? keyword.trim() : null;
        List<EventTypeEnum> matchingEventTypes = matchingEventTypes(normalizedKeyword);
        return repository.searchByKindergarten(kindergartenId, normalizedKeyword, matchingEventTypes, pageable)
                .map(mapper::toVO);
    }

    /**
     * Resolves which {@link EventTypeEnum} constants' names contain {@code keyword} (case-insensitive),
     * so the repository query can match the event-type enum value via a bound {@code IN} list instead
     * of an HQL {@code cast(... as string)} against the Postgres custom enum column type (see
     * {@code DetectionEventRepository#searchByKindergarten} javadoc). A blank keyword resolves to an
     * empty list — the keyword's blank/absent short-circuit inside the query already covers that case.
     */
    private List<EventTypeEnum> matchingEventTypes(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return List.of();
        }
        String needle = keyword.toLowerCase();
        List<EventTypeEnum> matches = new ArrayList<>();
        for (EventTypeEnum type : EventTypeEnum.values()) {
            if (type.name().toLowerCase().contains(needle)) {
                matches.add(type);
            }
        }
        return matches;
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

    /**
     * SSE reconnect replay source — NOT user-facing, so NO {@code @PreAuthorize}: it is driven from
     * the trusted connect path with the connecting client's own active {@code kindergartenId}, and
     * tenant scope is enforced by the {@code (kindergartenId, event_id > lastEventId)} query.
     *
     * <p>Returns the detection events the client missed (those with {@code event_id} strictly greater
     * than {@code lastEventId}) in <strong>ascending</strong> {@code event_id} order, bounded by
     * {@code max}. Over-limit semantics: when more than {@code max} events were missed, the
     * <em>most-recent</em> {@code max} are returned (older history is covered by the read API). This
     * is implemented by querying descending with {@code Limit=max} and reversing back to ascending,
     * so the client ends on the newest event.
     */
    @Transactional(readOnly = true)
    public List<DetectionEventVO> replaySince(Long kindergartenId, Long lastEventId, int max) {
        if (max <= 0) {
            return List.of();
        }
        List<DetectionEvent> descMostRecent = repository
                .findByKindergarten_IdAndIdGreaterThanOrderByIdDesc(kindergartenId, lastEventId, Limit.of(max));
        List<DetectionEventVO> ascending = new ArrayList<>(descMostRecent.size());
        for (int i = descMostRecent.size() - 1; i >= 0; i--) {
            ascending.add(mapper.toVO(descMostRecent.get(i)));
        }
        return ascending;
    }
}
