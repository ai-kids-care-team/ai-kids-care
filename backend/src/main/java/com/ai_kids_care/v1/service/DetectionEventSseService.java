package com.ai_kids_care.v1.service;

import com.ai_kids_care.v1.event.DetectionEventIngestedEvent;
import com.ai_kids_care.v1.vo.DetectionEventVO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
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

    /** Upper bound on events replayed on a single reconnect (older history via the read API). */
    private final int replayMax;

    public DetectionEventSseService(
            DetectionEventService detectionEventService,
            @Value("${detection.sse.replay-max:200}") int replayMax) {
        this.detectionEventService = detectionEventService;
        this.replayMax = replayMax;
    }

    /**
     * Open a stream emitter with correct frame ordering: create the emitter, replay the missed
     * events (when a numeric reconnect cursor is given) to it BEFORE it joins the live fan-out set,
     * then register it for live pushes. Registering only after replay guarantees replay frames
     * precede any live frame, so a reconnecting client never receives the same event both as a
     * replay frame and as a live push on the same connection (the prior order — register, then
     * replay — left a window where an in-flight async push could interleave a duplicate frame).
     */
    public SseEmitter openStream(Long kindergartenId, Long lastEventId) {
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MS);
        if (lastEventId != null) {
            replaySince(kindergartenId, lastEventId, emitter);
        }
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
                sendEvent(emitter, vo);
            } catch (Exception ex) {
                remove(event.kindergartenId(), emitter); // dead/slow client → evict
            }
        }
    }

    /**
     * Write one detection-event frame to an emitter, with the SSE {@code id:} set to the event's
     * {@code event_id} (the reconnect cursor) — shared by the live {@link #onIngested} push and the
     * reconnect {@link #replaySince} path so both frames are byte-identical. Throws on send failure;
     * the caller decides eviction.
     */
    private void sendEvent(SseEmitter emitter, DetectionEventVO vo) throws java.io.IOException {
        emitter.send(SseEmitter.event()
                .id(String.valueOf(vo.eventId()))
                .name("detection-event")
                .data(vo));
    }

    /**
     * SSE reconnect replay: on a new connection presenting a {@code Last-Event-ID}, push the events
     * the client missed during its disconnect — those of the connecting client's active kindergarten
     * with {@code event_id > lastEventId} — to the just-registered {@code emitter}, in ascending
     * {@code event_id} order, as normal {@code detection-event} frames, before live pushes resume.
     * Bounded by {@code detection.sse.replay-max}; tenant scope comes from {@code kindergartenId}
     * (the numeric {@code lastEventId} is only a lower bound). A send failure mid-replay evicts that
     * emitter (same eviction as the live path) and stops the replay for it.
     */
    public void replaySince(Long kindergartenId, Long lastEventId, SseEmitter emitter) {
        List<DetectionEventVO> missed = detectionEventService.replaySince(kindergartenId, lastEventId, replayMax);
        for (DetectionEventVO vo : missed) {
            try {
                sendEvent(emitter, vo);
            } catch (Exception ex) {
                remove(kindergartenId, emitter); // dead/slow client → evict, abort replay
                return;
            }
        }
    }

    /**
     * Periodic SSE keepalive: send a tiny comment frame to every registered emitter so idle
     * connections survive intermediate proxy/NAT idle timeouts, and a silently-dead connection is
     * detected and evicted within one heartbeat interval instead of lingering until
     * {@link #STREAM_TIMEOUT_MS}. Comment frames are ignored by the browser {@code EventSource}
     * (no {@code onmessage}). Send failure reuses the same {@link #remove} eviction as
     * {@link #onIngested}. Interval/initial-delay are configurable so the test profile can disable
     * auto-fire and call this method directly.
     */
    @Scheduled(
            fixedDelayString = "${detection.sse.heartbeat-interval-ms:25000}",
            initialDelayString = "${detection.sse.heartbeat-initial-ms:25000}")
    public void sendHeartbeats() {
        for (Map.Entry<Long, Set<SseEmitter>> entry : emittersByKindergarten.entrySet()) {
            Long kindergartenId = entry.getKey();
            for (SseEmitter emitter : entry.getValue()) {
                try {
                    emitter.send(SseEmitter.event().comment("hb"));
                } catch (Exception ex) {
                    remove(kindergartenId, emitter); // dead connection → evict promptly
                }
            }
        }
    }
}
