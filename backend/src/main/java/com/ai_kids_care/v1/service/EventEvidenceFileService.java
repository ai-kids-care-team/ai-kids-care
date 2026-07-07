package com.ai_kids_care.v1.service;

import com.ai_kids_care.v1.entity.EventEvidenceFile;
import com.ai_kids_care.v1.mapper.EventEvidenceFileMapper;
import com.ai_kids_care.v1.repository.DetectionEventRepository;
import com.ai_kids_care.v1.repository.EventEvidenceFileRepository;
import com.ai_kids_care.v1.security.EffectiveAuthorizationContextHolder;
import com.ai_kids_care.v1.storage.EvidenceObjectStream;
import com.ai_kids_care.v1.storage.EvidenceStoragePort;
import com.ai_kids_care.v1.vo.EventEvidenceFileVO;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * D-STORE: detection-event evidence metadata read + backend-proxied content read. Mirrors
 * {@link DetectionEventService}'s staff + tenant-scoped policy (evidence is internal review
 * material, not a guardian-facing channel) — {@code DETECTION_EVENT_READ} gates both operations.
 */
@Service
@RequiredArgsConstructor
public class EventEvidenceFileService {

    private static final String CONTENT_PATH_TEMPLATE = "/api/v1/event_evidence_files/%d/content";

    private final EventEvidenceFileRepository repository;
    private final DetectionEventRepository detectionEventRepository;
    private final EventEvidenceFileMapper mapper;
    private final EvidenceStoragePort storagePort;

    /**
     * Lists an event's evidence metadata, tenant-scoped via a JPQL join predicate (never
     * load-then-filter). A cross-tenant or nonexistent {@code eventId} 404s even when it has zero
     * evidence rows — otherwise an attacker could distinguish "exists, no evidence" (200 + []) from
     * "doesn't exist / not mine" by evidence count alone.
     */
    @Transactional(readOnly = true)
    @PreAuthorize("@authorizationPolicy.isAllowed(T(com.ai_kids_care.v1.security.AuthorizationAction).DETECTION_EVENT_READ)")
    public List<EventEvidenceFileVO> listByEvent(Long eventId) {
        Long kindergartenId = EffectiveAuthorizationContextHolder.requireActiveKindergartenId();
        if (!detectionEventRepository.existsByIdAndKindergarten_Id(eventId, kindergartenId)) {
            throw new EntityNotFoundException("DetectionEvent not found");
        }
        return repository.findByDetectionEvents_IdAndDetectionEvents_Kindergarten_Id(eventId, kindergartenId)
                .stream()
                .map(this::toVOWithAvailability)
                .toList();
    }

    /**
     * refine-evidence-readback-robustness: {@code available} now reflects a real, just-confirmed
     * object-store presence check ({@link EvidenceStoragePort#exists}), not merely a
     * {@code storage_uri} scheme guess — a since-deleted object is no longer misreported as
     * available. {@code exists} itself skips the network call entirely for non-resolvable URIs (e.g.
     * legacy {@code file://} rows), so this list path still costs at most one {@code statObject} per
     * row (never per {@code file://} row).
     */
    private EventEvidenceFileVO toVOWithAvailability(EventEvidenceFile entity) {
        EventEvidenceFileVO base = mapper.toVO(entity);
        boolean available = storagePort.exists(entity.getStorageUri());
        String contentPath = available ? CONTENT_PATH_TEMPLATE.formatted(base.evidenceId()) : null;
        return new EventEvidenceFileVO(
                base.evidenceId(), base.eventId(), base.kindergartenId(), base.type(), base.mimeType(),
                base.createdAt(), base.retentionUntil(), base.hold(), base.hash(), contentPath, available);
    }

    /**
     * DB-only metadata lookup, gated by the same staff+tenant policy as {@link #listByEvent}.
     * Deliberately returns just enough to open the byte stream ({@link ContentMeta}), and is called
     * as a SEPARATE proxied invocation from {@link #openContentStream} (both invoked by the
     * controller, never via internal self-invocation — a same-class call would silently skip the
     * {@code @Transactional}/{@code @PreAuthorize} proxy interception). This split is what keeps the
     * MinIO byte-stream fetch outside any DB transaction/connection hold: this method's transaction
     * commits and its connection is released before the controller ever calls
     * {@link #openContentStream}.
     */
    @Transactional(readOnly = true)
    @PreAuthorize("@authorizationPolicy.isAllowed(T(com.ai_kids_care.v1.security.AuthorizationAction).DETECTION_EVENT_READ)")
    public ContentMeta getContentMeta(Long evidenceId) {
        Long kindergartenId = EffectiveAuthorizationContextHolder.requireActiveKindergartenId();
        EventEvidenceFile entity = repository
                .findByIdAndDetectionEvents_Kindergarten_Id(evidenceId, kindergartenId)
                .orElseThrow(() -> new EntityNotFoundException("EventEvidenceFile not found"));
        return new ContentMeta(entity.getStorageUri(), entity.getMimeType().getValue(), entity.getHash());
    }

    /**
     * Object-store byte stream fetch. Deliberately NOT {@code @Transactional} and NOT
     * {@code @PreAuthorize}: authorization for this evidence row was already enforced by the
     * {@link #getContentMeta} call the controller makes first, and this method only accepts the
     * already-resolved {@link ContentMeta} (not a user-supplied id) — it cannot be used to widen
     * scope. Legacy {@code file://} rows and objects missing from the bucket both throw
     * {@link EntityNotFoundException} (hidden 404, not a 500).
     */
    public EvidenceObjectStream openContentStream(ContentMeta meta, String rangeHeader) {
        if (!storagePort.isAvailable(meta.storageUri())) {
            throw new EntityNotFoundException("Evidence content not available");
        }
        return storagePort.open(meta.storageUri(), rangeHeader);
    }

    /**
     * Internal handoff between the two proxied service calls the controller makes.
     * {@code storageUri} is never serialized into the HTTP response — the controller only reads
     * {@code mimeType}/{@code hash} for response headers and passes the whole record back into
     * {@link #openContentStream}.
     */
    public record ContentMeta(String storageUri, String mimeType, String hash) {
    }
}
