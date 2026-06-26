package com.ai_kids_care.v1.service;

import com.ai_kids_care.v1.config.PushoverConfig;
import net.pushover.client.*;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * PUSH delivery via Pushover. The application API token comes from {@link PushoverConfig}
 * (configured, fail-fast on blank) — never hard-coded. The per-recipient Pushover user key
 * is supplied by the caller (resolved from {@code push_subscriptions.address}).
 */
@Service
public class PushoverService {

    private final PushoverClient client;
    private final PushoverConfig config;

    public PushoverService(PushoverClient client, PushoverConfig config) {
        this.client = client;
        this.config = config;
    }

    /**
     * Send a PUSH message to a single recipient identified by their Pushover user key.
     *
     * @param userKey recipient's Pushover user key (from {@code push_subscriptions.address})
     */
    public Status sendToUser(String userKey, String title, String message) {
        if (!StringUtils.hasText(userKey)) {
            throw new IllegalArgumentException(
                    "PushoverService: recipient user key must not be blank");
        }
        try {
            return client.pushMessage(
                    PushoverMessage.builderWithApiToken(config.getApiToken())
                            .setUserId(userKey)
                            .setMessage(message)
                            .setPriority(MessagePriority.HIGH)
                            .setTitle(title)
                            .build()
            );
        } catch (PushoverException e) {
            throw new IllegalStateException("Pushover 消息发送失败", e);
        }
    }

    public List<PushOverSound> getSounds() {
        try {
            Set<PushOverSound> sounds = client.getSounds();
            return sounds.stream()
                    .sorted((a, b) -> a.getName().compareToIgnoreCase(b.getName()))
                    .collect(Collectors.toList());
        } catch (PushoverException e) {
            throw new IllegalStateException("获取 Pushover sounds 失败", e);
        }
    }
}
