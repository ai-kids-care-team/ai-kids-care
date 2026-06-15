package com.ai_kids_care.v1.service;

import net.pushover.client.*;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class PushoverService {
    private final PushoverClient client = new PushoverRestClient();

    public Status sendMessage(
            String apiToken,
            String userId,
            String message,
            String device,
            String title,
            String url,
            String titleForUrl,
            String sound
    ) {
        try {
            return client.pushMessage(
                    PushoverMessage.builderWithApiToken(apiToken)
                            .setUserId(userId)
                            .setMessage(message)
                            .setDevice(device)
                            .setPriority(MessagePriority.HIGH)
                            .setTitle(title)
                            .setUrl(url)
                            .setTitleForURL(titleForUrl)
                            .setSound(sound)
                            .build()
            );
        } catch (PushoverException e) {
            throw new IllegalStateException("Pushover 消息发送失败", e);
        }
    }

    public List<PushOverSound> getSounds() {
        try {
            Set<PushOverSound> sounds = client.getSounds();
            return sounds.stream().sorted((a, b) -> a.getName().compareToIgnoreCase(b.getName())).collect(Collectors.toList());
        } catch (PushoverException e) {
            throw new IllegalStateException("获取 Pushover sounds 失败", e);
        }
    }
}
