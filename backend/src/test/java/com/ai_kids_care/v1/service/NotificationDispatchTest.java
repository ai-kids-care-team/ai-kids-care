package com.ai_kids_care.v1.service;

import com.ai_kids_care.v1.entity.Notification;
import com.ai_kids_care.v1.entity.PushSubscription;
import com.ai_kids_care.v1.entity.User;
import com.ai_kids_care.v1.mapper.NotificationMapper;
import com.ai_kids_care.v1.repository.NotificationRepository;
import com.ai_kids_care.v1.repository.PushSubscriptionRepository;
import com.ai_kids_care.v1.security.audit.SecurityAuditWriter;
import com.ai_kids_care.v1.type.NotificationChannelEnum;
import com.ai_kids_care.v1.type.NotificationStatusEnum;
import com.ai_kids_care.v1.type.PushProviderEnum;
import com.ai_kids_care.v1.type.StatusEnum;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Delivery lifecycle unit tests for {@code NotificationService.dispatch} (no DB/Pushover).
 * Verifies the PUSH path drives QUEUED→SENDING→SENT on success and →FAILED with fail_reason
 * + retry_count on failure / missing subscription, per the notifications spec.
 */
@ExtendWith(MockitoExtension.class)
class NotificationDispatchTest {

    @Mock NotificationRepository repository;
    @Mock NotificationMapper mapper;
    @Mock PushoverService pushoverService;
    @Mock SecurityAuditWriter auditWriter;
    @Mock PushSubscriptionRepository pushSubscriptionRepository;

    @InjectMocks NotificationService service;

    private static final long RECIPIENT_ID = 42L;

    private Notification pushNotification() {
        User user = mock(User.class);
        when(user.getId()).thenReturn(RECIPIENT_ID);
        return Notification.builder()
                .channel(NotificationChannelEnum.PUSH)
                .recipientUser(user)
                .title("Title")
                .body("Body")
                .build();
    }

    private PushSubscription activeSub(String address) {
        return PushSubscription.builder()
                .provider(PushProviderEnum.PUSHOVER)
                .address(address)
                .status(StatusEnum.ACTIVE)
                .build();
    }

    @Test
    void push_success_marksSentWithTimestamp() {
        when(pushSubscriptionRepository.findByUser_IdAndProviderAndStatus(
                RECIPIENT_ID, PushProviderEnum.PUSHOVER, StatusEnum.ACTIVE))
                .thenReturn(List.of(activeSub("user-key-1")));
        Notification n = pushNotification();

        service.dispatch(n);

        assertThat(n.getStatus()).isEqualTo(NotificationStatusEnum.SENT);
        assertThat(n.getSentAt()).isNotNull();
        verify(pushoverService).sendToUser("user-key-1", "Title", "Body");
    }

    @Test
    void push_deliveryFailure_marksFailedAndIncrementsRetry() {
        when(pushSubscriptionRepository.findByUser_IdAndProviderAndStatus(
                RECIPIENT_ID, PushProviderEnum.PUSHOVER, StatusEnum.ACTIVE))
                .thenReturn(List.of(activeSub("user-key-1")));
        doThrow(new IllegalStateException("Pushover 消息发送失败"))
                .when(pushoverService).sendToUser(any(), any(), any());
        Notification n = pushNotification();

        service.dispatch(n);

        assertThat(n.getStatus()).isEqualTo(NotificationStatusEnum.FAILED);
        assertThat(n.getFailReason()).contains("Pushover delivery failed");
        assertThat(n.getRetryCount()).isEqualTo(1);
    }

    @Test
    void push_noActiveSubscription_marksFailedWithoutCallingPushover() {
        when(pushSubscriptionRepository.findByUser_IdAndProviderAndStatus(
                RECIPIENT_ID, PushProviderEnum.PUSHOVER, StatusEnum.ACTIVE))
                .thenReturn(List.of());
        Notification n = pushNotification();

        service.dispatch(n);

        assertThat(n.getStatus()).isEqualTo(NotificationStatusEnum.FAILED);
        assertThat(n.getFailReason()).contains("no active Pushover subscription");
        assertThat(n.getRetryCount()).isEqualTo(1);
        verify(pushoverService, never()).sendToUser(any(), any(), any());
    }
}
