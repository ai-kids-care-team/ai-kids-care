package com.ai_kids_care.v1.service;

import com.ai_kids_care.v1.event.DetectionEventIngestedEvent;
import com.ai_kids_care.v1.vo.DetectionEventVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link DetectionEventSseService} push behaviour (no Spring context / no real
 * stream). A detection-event ingested for a kindergarten is pushed to that kindergarten's
 * registered emitters only; a send failure evicts the dead emitter.
 */
@ExtendWith(MockitoExtension.class)
class DetectionEventSseServiceTest {

    @Mock DetectionEventService detectionEventService;
    @InjectMocks DetectionEventSseService sse;

    private static final long KG = 1L;
    private static final long EVENT_ID = 5L;

    private DetectionEventVO vo() {
        return new DetectionEventVO(EVENT_ID, KG, "KG", null, null, null, null, null,
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
}
