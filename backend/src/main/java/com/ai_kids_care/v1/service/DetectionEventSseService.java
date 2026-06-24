package com.ai_kids_care.v1.service;

import com.ai_kids_care.v1.event.DetectionEventIngestedEvent;
import com.ai_kids_care.v1.vo.DetectionEventVO;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ⑥ Realtime detection-event push over SSE. Holds a per-kindergarten registry of open
 * {@link SseEmitter}s (in-process; single-instance assumption — multi-instance fanout via Redis
 * pub/sub is a follow-up). On a {@link DetectionEventIngestedEvent} (published by the ingest path),
 * pushes the event's VO to every open emitter of that kindergarten on an {@code @Async} thread, so
 * the ingest response is never blocked. Tenant isolation: an emitter is only ever registered under
 * the subscriber's own active kindergarten, and a push only reaches that kindergarten's emitters.
 */
@Service
public class DetectionEventSseService {

    /** Stream lifetime before the client must reconnect (EventSource auto-reconnects). */
    static final long STREAM_TIMEOUT_MS = 30 * 60 * 1000L;

    private final DetectionEventService detectionEventService;
    private final Map<Long, Set<SseEmitter>> emittersByKindergarten = new ConcurrentHashMap<>();

    public DetectionEventSseService(DetectionEventService detectionEventService) {
        this.detectionEventService = detectionEventService;
    }

    /** Create + register an emitter for the given kindergarten and wire its cleanup callbacks. */
    public SseEmitter register(Long kindergartenId) {
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MS);
        registerEmitter(kindergartenId, emitter);
        return emitter;
    }

    /** Register an existing emitter (package-visible so tests can inject a stub emitter). */
    void registerEmitter(Long kindergartenId, SseEmitter emitter) {
        emittersByKindergarten.computeIfAbsent(kindergartenId, k -> ConcurrentHashMap.newKeySet()).add(emitter);
        emitter.onCompletion(() -> remove(kindergartenId, emitter));
        emitter.onTimeout(() -> remove(kindergartenId, emitter));
        emitter.onError(e -> remove(kindergartenId, emitter));
    }

    private void remove(Long kindergartenId, SseEmitter emitter) {
        Set<SseEmitter> set = emittersByKindergarten.get(kindergartenId);
        if (set != null) {
            set.remove(emitter);
        }
    }

    @Async
    @EventListener
    public void onIngested(DetectionEventIngestedEvent event) {
        Set<SseEmitter> set = emittersByKindergarten.get(event.kindergartenId());
        if (set == null || set.isEmpty()) {
            return; // no open dashboard for this kindergarten — nothing to push (and no DB read)
        }
        DetectionEventVO vo = detectionEventService.getForPush(event.eventId(), event.kindergartenId());
        if (vo == null) {
            return;
        }
        for (SseEmitter emitter : set) {
            try {
                emitter.send(SseEmitter.event()
                        .id(String.valueOf(vo.eventId()))
                        .name("detection-event")
                        .data(vo));
            } catch (Exception ex) {
                remove(event.kindergartenId(), emitter); // dead/slow client → evict
            }
        }
    }
}
