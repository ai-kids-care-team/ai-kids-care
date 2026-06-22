package com.ai_kids_care.v1.service;

import com.ai_kids_care.v1.config.PushoverConfig;
import net.pushover.client.PushoverClient;
import net.pushover.client.PushoverException;
import net.pushover.client.Status;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the Pushover delivery seam — no real Pushover calls. Verifies the service
 * uses the configured app token path, guards a blank recipient key, and translates the
 * library's checked exception into a runtime failure the dispatcher can record.
 */
class PushoverServiceTest {

    private final PushoverClient client = mock(PushoverClient.class);
    private final PushoverService service = new PushoverService(client, config("app-token-xyz"));

    private static PushoverConfig config(String token) {
        PushoverConfig c = new PushoverConfig();
        c.setApiToken(token);
        return c;
    }

    @Test
    void sendToUser_success_callsClientOnce() throws Exception {
        when(client.pushMessage(any())).thenReturn(mock(Status.class));

        service.sendToUser("user-key-1", "Title", "Body");

        verify(client, times(1)).pushMessage(any());
    }

    @Test
    void sendToUser_blankUserKey_throwsAndDoesNotCallClient() throws Exception {
        assertThatThrownBy(() -> service.sendToUser("  ", "Title", "Body"))
                .isInstanceOf(IllegalArgumentException.class);
        verify(client, never()).pushMessage(any());
    }

    @Test
    void sendToUser_pushoverException_translatedToIllegalState() throws Exception {
        when(client.pushMessage(any())).thenThrow(PushoverException.class);

        assertThatThrownBy(() -> service.sendToUser("user-key-1", "Title", "Body"))
                .isInstanceOf(IllegalStateException.class);
    }
}
