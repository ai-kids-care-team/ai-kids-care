package com.ai_kids_care.v1.controller;

import com.ai_kids_care.v1.security.EffectiveAuthorizationContextHolder;
import com.ai_kids_care.v1.service.DetectionEventService;
import com.ai_kids_care.v1.service.DetectionEventSseService;
import com.ai_kids_care.v1.service.EventEvidenceFileService;
import com.ai_kids_care.v1.vo.DetectionEventVO;
import com.ai_kids_care.v1.vo.EventEvidenceFileVO;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;

/**
 * ⑥ 看板的检测事件读取 API（staff + tenant-scoped；作用域由 {@link DetectionEventService} 经
 * EffectiveAuthorizationContext 强制，调用方不传 kindergartenId）。看板初始/历史数据源;
 * 实时增量走 {@code GET /api/v1/detection-events/stream}(SSE)。
 */
@Tag(name = "DetectionEvent")
@RestController
@RequestMapping("/api/v1/detection-events")
@RequiredArgsConstructor
public class DetectionEventController {

    private final DetectionEventService service;
    private final DetectionEventSseService sseService;
    private final EventEvidenceFileService evidenceFileService;

    @GetMapping
    public ResponseEntity<Page<DetectionEventVO>> listDetectionEvents(
            @RequestParam(required = false) String keyword,
            @ParameterObject @PageableDefault(size = 20, sort = "detectedAt", direction = Sort.Direction.DESC)
            Pageable pageable
    ) {
        return ResponseEntity.ok(service.listDetectionEvents(keyword, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<DetectionEventVO> getDetectionEvent(@PathVariable Long id) {
        return ResponseEntity.ok(service.getDetectionEvent(id));
    }

    /**
     * D-STORE: this event's evidence metadata (lazy-loaded by the frontend on card
     * expand/detail-open, not batched with the list). Staff+tenant policy and the hidden-404 gate
     * live in {@link EventEvidenceFileService#listByEvent} — this controller only dispatches.
     */
    @GetMapping("/{id}/evidence")
    public ResponseEntity<List<EventEvidenceFileVO>> getEvidence(@PathVariable Long id) {
        return ResponseEntity.ok(evidenceFileService.listByEvent(id));
    }

    /**
     * SSE stream of this staff member's own kindergarten's detection events. Staff-only +
     * tenant-scoped: the stream is registered under the caller's active kindergarten, so it only
     * ever carries that kindergarten's events. The frontend loads recent history via the list
     * endpoint, then receives live increments here (de-duplicated by event id).
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("@authorizationPolicy.isAllowed(T(com.ai_kids_care.v1.security.AuthorizationAction).DETECTION_EVENT_READ)")
    public SseEmitter stream(
            @RequestHeader(value = "Last-Event-ID", required = false) String lastEventId,
            HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("X-Accel-Buffering", "no"); // belt-and-suspenders with nginx proxy_buffering off
        Long kindergartenId = EffectiveAuthorizationContextHolder.requireActiveKindergartenId();
        // SSE reconnect catch-up: the browser EventSource auto-supplies Last-Event-ID (the id: of the
        // last frame it received). When present and numeric, the events missed during the disconnect
        // (event_id > lastId, this kindergarten only) are replayed to the emitter BEFORE it joins the
        // live fan-out, so replay frames always precede live pushes and the same event is never
        // delivered twice on one connection. Missing/blank/non-numeric → no replay; behaves exactly
        // as the pre-existing connect. (Ordering is owned by SseService#openStream.)
        Long lastId = parseLastEventId(lastEventId);
        SseEmitter emitter = sseService.openStream(kindergartenId, lastId);
        try {
            emitter.send(SseEmitter.event().name("connected").data("ok")); // flush headers / confirm stream
        } catch (IOException e) {
            emitter.completeWithError(e);
        }
        return emitter;
    }

    /** Parse the SSE {@code Last-Event-ID} header to a cursor; null/blank/non-numeric → no replay. */
    private static Long parseLastEventId(String lastEventId) {
        if (lastEventId == null || lastEventId.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(lastEventId.trim());
        } catch (NumberFormatException e) {
            return null; // non-numeric → silently skip replay
        }
    }
}
