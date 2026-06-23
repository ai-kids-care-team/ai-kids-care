package com.ai_kids_care.v1.service;

import com.ai_kids_care.v1.config.SolapiConfig;
import net.nurigo.sdk.message.model.Message;
import net.nurigo.sdk.message.request.SingleMessageSendingRequest;
import net.nurigo.sdk.message.service.DefaultMessageService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the Solapi delivery seam — no real Solapi calls (the SDK's
 * {@code DefaultMessageService} is mocked). Verifies the adapter maps sender/recipient/body onto
 * the SDK request, guards a blank phone, and translates an SDK failure into the
 * {@link IllegalStateException} the dispatcher records as FAILED. Mirrors {@code PushoverServiceTest}.
 */
class SolapiSmsAdapterTest {

    private final DefaultMessageService messageService = mock(DefaultMessageService.class);
    private final SolapiSmsAdapter adapter = new SolapiSmsAdapter(messageService, config());

    private static SolapiConfig config() {
        SolapiConfig c = new SolapiConfig();
        c.setApiKey("key");
        c.setApiSecret("secret");
        c.setSender("0212345678");
        return c;
    }

    @Test
    void send_mapsSenderRecipientAndText_andCallsServiceOnce() {
        adapter.send("01012345678", "hello world");

        ArgumentCaptor<SingleMessageSendingRequest> captor =
                ArgumentCaptor.forClass(SingleMessageSendingRequest.class);
        verify(messageService, times(1)).sendOne(captor.capture());
        Message sent = captor.getValue().getMessage();
        assertThat(sent.getFrom()).isEqualTo("0212345678");
        assertThat(sent.getTo()).isEqualTo("01012345678");
        assertThat(sent.getText()).isEqualTo("hello world");
    }

    @Test
    void send_blankPhone_throwsIllegalArgument_withoutCallingService() {
        assertThatThrownBy(() -> adapter.send("   ", "hello"))
                .isInstanceOf(IllegalArgumentException.class);
        verify(messageService, never()).sendOne(any());
    }

    @Test
    void send_sdkFailure_translatedToIllegalState() {
        when(messageService.sendOne(any())).thenThrow(new RuntimeException("solapi down"));

        assertThatThrownBy(() -> adapter.send("01012345678", "hello"))
                .isInstanceOf(IllegalStateException.class);
    }
}
