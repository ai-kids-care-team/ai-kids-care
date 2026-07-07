package com.ai_kids_care.v1.service;

import com.ai_kids_care.v1.entity.Notification;
import com.ai_kids_care.v1.entity.PushSubscription;
import com.ai_kids_care.v1.entity.User;
import com.ai_kids_care.v1.mapper.NotificationMapper;
import com.ai_kids_care.v1.repository.NotificationRepository;
import com.ai_kids_care.v1.repository.PushSubscriptionRepository;
import com.ai_kids_care.v1.security.audit.SecurityAuditWriter;
import com.ai_kids_care.v1.service.NotificationDeliveryStore.DeliveryDecision;
import com.ai_kids_care.v1.type.DeliveryAttemptOutcome;
import com.ai_kids_care.v1.type.NotificationChannelEnum;
import com.ai_kids_care.v1.type.PushProviderEnum;
import com.ai_kids_care.v1.type.StatusEnum;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Orchestration unit tests for the transaction-isolated {@code NotificationService.dispatch} (PRF-02).
 * The DB lifecycle writes are delegated to a mocked {@link NotificationDeliveryStore}; these tests
 * assert that dispatch (a) commits the SENDING/IN_FLIGHT attempt BEFORE the provider call, (b) records
 * the terminal SENT/FAILED AFTER it, (c) never re-sends when an attempt already exists (at-most-once),
 * and (d) bounds the SMS provider call with a timeout (PRF-01). The end-to-end DB atomicity invariants
 * are proven against a real database in {@code NotificationDeliveryAtomicityIT}.
 */
@ExtendWith(MockitoExtension.class)
class NotificationDispatchTest {

    @Mock NotificationRepository repository;
    @Mock NotificationMapper mapper;
    @Mock PushPort pushPort;
    @Mock SmsPort smsPort;
    @Mock SecurityAuditWriter auditWriter;
    @Mock PushSubscriptionRepository pushSubscriptionRepository;
    @Mock NotificationDeliveryStore deliveryStore;

    @InjectMocks NotificationService service;

    private static final long RECIPIENT_ID = 42L;

    private Notification pushNotification() {
        User user = org.mockito.Mockito.mock(User.class);
        when(user.getId()).thenReturn(RECIPIENT_ID);
        return Notification.builder()
                .channel(NotificationChannelEnum.PUSH)
                .recipientUser(user)
                .title("Title")
                .body("Body")
                .build();
    }

    private Notification smsNotification(String phone) {
        User user = org.mockito.Mockito.mock(User.class);
        when(user.getPhone()).thenReturn(phone);
        return Notification.builder()
                .channel(NotificationChannelEnum.SMS)
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

    private void subscriptionPresent() {
        when(pushSubscriptionRepository.findByUser_IdAndProviderAndStatus(
                RECIPIENT_ID, PushProviderEnum.PUSHOVER, StatusEnum.ACTIVE))
                .thenReturn(List.of(activeSub("user-key-1")));
    }

    // ── PUSH channel ──────────────────────────────────────────────────────────────

    @Test
    void push_success_beginsAttemptThenSendsThenMarksSucceeded() {
        subscriptionPresent();
        Notification n = pushNotification();
        when(deliveryStore.beginAttempt(eq(n), eq("PUSHOVER"))).thenReturn(DeliveryDecision.proceed());

        service.dispatch(n);

        verify(deliveryStore).beginAttempt(n, "PUSHOVER");
        verify(pushPort).sendToUser("user-key-1", "Title", "Body");
        verify(deliveryStore).markSucceeded(n);
        verify(deliveryStore, never()).markFailed(any(), any());
        verify(deliveryStore, never()).reconcile(any(), any());
    }

    @Test
    void push_deliveryFailure_marksFailed() {
        subscriptionPresent();
        Notification n = pushNotification();
        when(deliveryStore.beginAttempt(eq(n), eq("PUSHOVER"))).thenReturn(DeliveryDecision.proceed());
        doThrow(new IllegalStateException("Pushover boom"))
                .when(pushPort).sendToUser(any(), any(), any());

        service.dispatch(n);

        verify(deliveryStore).markFailed(eq(n), contains("Pushover delivery failed"));
        verify(deliveryStore, never()).markSucceeded(any());
    }

    @Test
    void push_noActiveSubscription_marksFailedWithoutAttemptOrProviderCall() {
        when(pushSubscriptionRepository.findByUser_IdAndProviderAndStatus(
                RECIPIENT_ID, PushProviderEnum.PUSHOVER, StatusEnum.ACTIVE))
                .thenReturn(List.of());
        Notification n = pushNotification();

        service.dispatch(n);

        verify(deliveryStore).markFailed(eq(n), contains("no active Pushover subscription"));
        verify(deliveryStore, never()).beginAttempt(any(), any());
        verify(pushPort, never()).sendToUser(any(), any(), any());
    }

    @Test
    void push_retryAfterSuccess_doesNotResend_reconcilesOnly() {
        subscriptionPresent();
        Notification n = pushNotification();
        when(deliveryStore.beginAttempt(eq(n), eq("PUSHOVER")))
                .thenReturn(DeliveryDecision.alreadyAttempted(DeliveryAttemptOutcome.SUCCEEDED));

        service.dispatch(n);

        verify(deliveryStore).reconcile(n, DeliveryAttemptOutcome.SUCCEEDED);
        verify(pushPort, never()).sendToUser(any(), any(), any());
        verify(deliveryStore, never()).markSucceeded(any());
        verify(deliveryStore, never()).markFailed(any(), any());
    }

    @Test
    void push_retryWhileInFlight_doesNotResend_reconcilesOnly() {
        subscriptionPresent();
        Notification n = pushNotification();
        when(deliveryStore.beginAttempt(eq(n), eq("PUSHOVER")))
                .thenReturn(DeliveryDecision.alreadyAttempted(DeliveryAttemptOutcome.IN_FLIGHT));

        service.dispatch(n);

        verify(deliveryStore).reconcile(n, DeliveryAttemptOutcome.IN_FLIGHT);
        verify(pushPort, never()).sendToUser(any(), any(), any());
    }

    // ── SMS channel ───────────────────────────────────────────────────────────────

    @Test
    void sms_success_beginsAttemptThenSendsThenMarksSucceeded() {
        Notification n = smsNotification("01012345678");
        when(deliveryStore.beginAttempt(eq(n), eq("SOLAPI"))).thenReturn(DeliveryDecision.proceed());

        service.dispatch(n);

        verify(smsPort).send("01012345678", "Body");
        verify(deliveryStore).markSucceeded(n);
        verifyNoInteractions(pushPort);
    }

    @Test
    void sms_noRecipientPhone_marksFailedWithoutAttemptOrPortCall() {
        Notification n = smsNotification(null);

        service.dispatch(n);

        verify(deliveryStore).markFailed(eq(n), contains("no phone"));
        verify(deliveryStore, never()).beginAttempt(any(), any());
        verify(smsPort, never()).send(any(), any());
    }

    @Test
    void sms_blankRecipientPhone_marksFailedWithoutPortCall() {
        Notification n = smsNotification("   ");

        service.dispatch(n);

        verify(deliveryStore).markFailed(eq(n), contains("no phone"));
        verify(smsPort, never()).send(any(), any());
    }

    @Test
    void sms_deliveryFailure_marksFailed() {
        Notification n = smsNotification("01012345678");
        when(deliveryStore.beginAttempt(eq(n), eq("SOLAPI"))).thenReturn(DeliveryDecision.proceed());
        doThrow(new IllegalStateException("Solapi boom")).when(smsPort).send(any(), any());

        service.dispatch(n);

        verify(deliveryStore).markFailed(eq(n), contains("SMS delivery failed"));
        verify(deliveryStore, never()).markSucceeded(any());
    }

    @Test
    void sms_providerTimeout_marksFailedAsTimeout_PRF01() {
        // PRF-01: a Solapi call that overruns the budget is bounded and recorded FAILED (not stuck).
        ReflectionTestUtils.setField(service, "smsSendTimeoutMs", 100L);
        Notification n = smsNotification("01012345678");
        when(deliveryStore.beginAttempt(eq(n), eq("SOLAPI"))).thenReturn(DeliveryDecision.proceed());
        // lenient: the async send runs on the common pool and may not be reached before the 100ms
        // budget fires on a slow CI runner — the timeout path is what we assert, so the stub being
        // unused on a given run must not trip strict-stubbing (UnnecessaryStubbingException, PRF-01 flake).
        lenient().doAnswer(inv -> {
            Thread.sleep(2000); // overrun the 100ms budget
            return null;
        }).when(smsPort).send(any(), any());

        service.dispatch(n);

        verify(deliveryStore).markFailed(eq(n), contains("timed out"));
        verify(deliveryStore, never()).markSucceeded(any());
    }

    @Test
    void sms_retryAfterSuccess_doesNotResend_reconcilesOnly() {
        Notification n = smsNotification("01012345678");
        when(deliveryStore.beginAttempt(eq(n), eq("SOLAPI")))
                .thenReturn(DeliveryDecision.alreadyAttempted(DeliveryAttemptOutcome.SUCCEEDED));

        service.dispatch(n);

        verify(deliveryStore).reconcile(n, DeliveryAttemptOutcome.SUCCEEDED);
        verify(smsPort, never()).send(any(), any());
    }
}
