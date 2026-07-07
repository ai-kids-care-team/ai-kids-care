package com.ai_kids_care.v1.service;

import com.ai_kids_care.v1.config.SolapiConfig;
import net.nurigo.sdk.message.model.Message;
import net.nurigo.sdk.message.request.SingleMessageSendingRequest;
import net.nurigo.sdk.message.service.DefaultMessageService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * {@link SmsPort} implementation backed by the Solapi Java SDK ({@code net.nurigo:sdk}). The SDK
 * client ({@code DefaultMessageService}) is an injected bean (see {@code SolapiClientConfig}) so this
 * adapter can be unit-tested with a stubbed client — no real Solapi calls in CI. The sender
 * (발신번호) comes from {@link SolapiConfig}; the per-recipient phone is passed in by the caller
 * (resolved from {@code users.phone}). Mirrors {@link PushoverPushAdapter} (the {@link PushPort}
 * implementation): translate the SDK's failure into an {@link IllegalStateException} the dispatcher
 * records as FAILED.
 */
@Service
public class SolapiSmsAdapter implements SmsPort {

    private final DefaultMessageService messageService;
    private final SolapiConfig config;

    public SolapiSmsAdapter(DefaultMessageService messageService, SolapiConfig config) {
        this.messageService = messageService;
        this.config = config;
    }

    @Override
    public void send(String phone, String text) {
        if (!StringUtils.hasText(phone)) {
            throw new IllegalArgumentException("SolapiSmsAdapter: recipient phone must not be blank");
        }
        Message message = new Message();
        message.setFrom(config.getSender());
        message.setTo(phone);
        message.setText(text);
        // The Solapi SDK signals delivery problems with NurigoException implementers (NurigoMessage-
        // NotReceivedException, NurigoEmptyResponseException, NurigoUnknownException, …). They extend
        // java.lang.Exception (checked) yet sendOne() declares no throws, so we catch Exception to
        // cover both those and any runtime failure, translating to IllegalStateException — the only
        // delivery failure the dispatcher catches (programming errors above stay IllegalArgumentException).
        try {
            messageService.sendOne(new SingleMessageSendingRequest(message));
        } catch (Exception e) {
            throw new IllegalStateException("Solapi SMS 발송 실패", e);
        }
    }
}
