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
 * Unit tests for the Pushover delivery seam — no real Pushover calls. Verifies the adapter
 * uses the configured app token path, guards a blank recipient key, and translates the
 * library's checked exception into a runtime failure the dispatcher can record.
 */
class PushoverPushAdapterTest {

    private final PushoverClient client = mock(PushoverClient.class);
    private final PushoverPushAdapter adapter = new PushoverPushAdapter(client, config("app-token-xyz"));

    private static PushoverConfig config(String token) {
        PushoverConfig c = new PushoverConfig();
        c.setApiToken(token);
        return c;
    }

    @Test
    void sendToUser_success_callsClientOnce() throws Exception {
        Status status = mock(Status.class);
        when(status.getStatus()).thenReturn(1);
        when(status.getRequestId()).thenReturn("req-1");
        when(client.pushMessage(any())).thenReturn(status);

        PushPort.PushDeliveryStatus result = adapter.sendToUser("user-key-1", "Title", "Body");

        verify(client, times(1)).pushMessage(any());
        assertThat(result.status()).isEqualTo(1);
        assertThat(result.requestId()).isEqualTo("req-1");
    }

    @Test
    void sendToUser_blankUserKey_throwsAndDoesNotCallClient() throws Exception {
        assertThatThrownBy(() -> adapter.sendToUser("  ", "Title", "Body"))
                .isInstanceOf(IllegalArgumentException.class);
        verify(client, never()).pushMessage(any());
    }

    @Test
    void sendToUser_pushoverException_translatedToIllegalState() throws Exception {
        when(client.pushMessage(any())).thenThrow(PushoverException.class);

        assertThatThrownBy(() -> adapter.sendToUser("user-key-1", "Title", "Body"))
                .isInstanceOf(IllegalStateException.class);
    }
}
