package com.ai_kids_care.v1.service;

import com.ai_kids_care.v1.event.DetectionEventIngestedEvent;
import com.ai_kids_care.v1.vo.DetectionEventVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link DetectionEventSseService} push behaviour (no Spring context / no real
 * stream). A detection-event ingested for a kindergarten is pushed to that kindergarten's
 * registered emitters only; a send failure evicts the dead emitter. Reconnect replay pushes the
 * service-supplied missed events to the connecting emitter in order, with id: == event_id, and
 * evicts the emitter on a replay-frame send failure.
 */
@ExtendWith(MockitoExtension.class)
class DetectionEventSseServiceTest {

    @Mock DetectionEventService detectionEventService;
    DetectionEventSseService sse;

    private static final long KG = 1L;
    private static final long EVENT_ID = 5L;
    private static final int REPLAY_MAX = 5;

    @BeforeEach
    void initService() {
        // Explicit construction (not @InjectMocks) so the replay-max bound is a known non-zero value.
        sse = new DetectionEventSseService(detectionEventService, REPLAY_MAX);
    }

    private DetectionEventVO vo() {
        return voWithId(EVENT_ID);
    }

    private DetectionEventVO voWithId(long eventId) {
        return new DetectionEventVO(eventId, KG, "KG", null, null, null, null, null,
                "FIGHT", null, null, null, null, null, "OPEN", null, null);
    }

    @Test
    void onIngested_sendsToRegisteredEmitterOfSameKindergarten() throws Exception {
        when(detectionEventService.getForPush(EVENT_ID, KG)).thenReturn(vo());
        SseEmitter emitter = mock(SseEmitter.class);
        sse.registerEmitter(KG, emitter);

        sse.onIngested(new DetectionEventIngestedEvent(EVENT_ID, KG));

        verify(emitter).send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    void onIngested_doesNotCrossKindergarten() throws Exception {
        SseEmitter emitter = mock(SseEmitter.class);
        sse.registerEmitter(KG, emitter);

        // event for a different kindergarten (2L) — KG=1 emitter must not be touched
        sse.onIngested(new DetectionEventIngestedEvent(9L, 2L));

        verify(emitter, never()).send(any(SseEmitter.SseEventBuilder.class));
        verifyNoInteractions(detectionEventService);
    }

    @Test
    void onIngested_evictsEmitterOnSendFailure() throws Exception {
        when(detectionEventService.getForPush(EVENT_ID, KG)).thenReturn(vo());
        SseEmitter emitter = mock(SseEmitter.class);
        doThrow(new IOException("broken pipe")).when(emitter).send(any(SseEmitter.SseEventBuilder.class));
        sse.registerEmitter(KG, emitter);

        sse.onIngested(new DetectionEventIngestedEvent(EVENT_ID, KG)); // send throws → evicted
        sse.onIngested(new DetectionEventIngestedEvent(EVENT_ID, KG)); // emitter gone → no 2nd send

        verify(emitter, times(1)).send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    void sendHeartbeat_sendsKeepaliveFrameToRegisteredEmitter() throws Exception {
        SseEmitter emitter = mock(SseEmitter.class);
        sse.registerEmitter(KG, emitter);

        sse.sendHeartbeats();

        verify(emitter).send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    void sendHeartbeat_evictsEmitterOnFailure() throws Exception {
        SseEmitter emitter = mock(SseEmitter.class);
        doThrow(new IOException("broken pipe")).when(emitter).send(any(SseEmitter.SseEventBuilder.class));
        sse.registerEmitter(KG, emitter);

        sse.sendHeartbeats(); // send throws → evicted
        sse.sendHeartbeats(); // emitter gone → no 2nd send

        verify(emitter, times(1)).send(any(SseEmitter.SseEventBuilder.class));
    }

    // ── reconnect replay ─────────────────────────────────────────────────────────

    @Test
    void replaySince_sendsEachMissedEventToEmitterInOrderWithIdEqualsEventId() throws Exception {
        long lastId = 2L;
        // service returns the missed events ascending (6,7,8); replaySince must send each, in order.
        when(detectionEventService.replaySince(KG, lastId, REPLAY_MAX))
                .thenReturn(List.of(voWithId(6L), voWithId(7L), voWithId(8L)));
        SseEmitter emitter = mock(SseEmitter.class);

        sse.replaySince(KG, lastId, emitter);

        // One frame per missed event. Each built frame carries id: == event_id and the VO payload;
        // assert that by rendering the builder's data set to a string (contains "id:6"/"data:{...}").
        ArgumentCaptor<SseEmitter.SseEventBuilder> frames =
                ArgumentCaptor.forClass(SseEmitter.SseEventBuilder.class);
        verify(emitter, times(3)).send(frames.capture());
        List<String> rendered = frames.getAllValues().stream().map(this::render).toList();
        assertThat(rendered.get(0)).contains("id:6").contains("detection-event");
        assertThat(rendered.get(1)).contains("id:7");
        assertThat(rendered.get(2)).contains("id:8");
        // ascending: 6 before 7 before 8 across the three captured frames
        assertThat(rendered.get(0)).doesNotContain("id:7").doesNotContain("id:8");
    }

    /** Render an SseEventBuilder's data set (id/name/data lines + payload) to one inspectable string. */
    private String render(SseEmitter.SseEventBuilder builder) {
        StringBuilder sb = new StringBuilder();
        builder.build().forEach(d -> sb.append(d.getData()));
        return sb.toString();
    }

    @Test
    void replaySince_passesConfiguredReplayMaxToService() {
        when(detectionEventService.replaySince(eq(KG), eq(2L), eq(REPLAY_MAX))).thenReturn(List.of());
        SseEmitter emitter = mock(SseEmitter.class);

        sse.replaySince(KG, 2L, emitter);

        verify(detectionEventService).replaySince(KG, 2L, REPLAY_MAX);
    }

    @Test
    void replaySince_evictsEmitterOnFrameSendFailureAndStops() throws Exception {
        long lastId = 2L;
        when(detectionEventService.replaySince(KG, lastId, REPLAY_MAX))
                .thenReturn(List.of(voWithId(6L), voWithId(7L), voWithId(8L)));
        SseEmitter emitter = mock(SseEmitter.class);
        // first replay frame fails → emitter evicted, replay aborts (no 2nd/3rd send).
        doThrow(new IOException("broken pipe")).when(emitter).send(any(SseEmitter.SseEventBuilder.class));
        sse.registerEmitter(KG, emitter);

        sse.replaySince(KG, lastId, emitter);

        verify(emitter, times(1)).send(any(SseEmitter.SseEventBuilder.class));
        // emitter removed → a later live push for this KG does not reach it.
        when(detectionEventService.getForPush(EVENT_ID, KG)).thenReturn(vo());
        sse.onIngested(new DetectionEventIngestedEvent(EVENT_ID, KG));
        verify(emitter, times(1)).send(any(SseEmitter.SseEventBuilder.class)); // still just the one
    }

    @Test
    void replaySince_otherEmitterUnaffectedByOneEmitterReplayFailure() throws Exception {
        long lastId = 2L;
        when(detectionEventService.replaySince(KG, lastId, REPLAY_MAX))
                .thenReturn(List.of(voWithId(6L)));
        SseEmitter dead = mock(SseEmitter.class);
        SseEmitter live = mock(SseEmitter.class);
        doThrow(new IOException("broken pipe")).when(dead).send(any(SseEmitter.SseEventBuilder.class));
        sse.registerEmitter(KG, dead);
        sse.registerEmitter(KG, live);

        // replay only targets the connecting (dead) emitter; the other registered emitter is untouched.
        sse.replaySince(KG, lastId, dead);

        verify(dead, times(1)).send(any(SseEmitter.SseEventBuilder.class));
        verifyNoInteractions(live);
    }
}
