package com.ai_kids_care.v1.service;

import com.ai_kids_care.v1.entity.DetectionEvent;
import com.ai_kids_care.v1.entity.Kindergarten;
import com.ai_kids_care.v1.entity.Notification;
import com.ai_kids_care.v1.entity.User;
import com.ai_kids_care.v1.repository.ChildClassAssignmentRepository;
import com.ai_kids_care.v1.repository.ChildGuardianRelationshipRepository;
import com.ai_kids_care.v1.repository.ClassRoomAssignmentRepository;
import com.ai_kids_care.v1.repository.DetectionEventRepository;
import com.ai_kids_care.v1.repository.KindergartenRepository;
import com.ai_kids_care.v1.repository.NotificationRepository;
import com.ai_kids_care.v1.repository.UserRepository;
import com.ai_kids_care.v1.type.EventStatusEnum;
import com.ai_kids_care.v1.type.NotificationChannelEnum;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Guardian SMS channel (this slice): on an {@code ESCALATED} review confirmation a guardian whose
 * linked {@code users.phone} is non-blank receives BOTH a PUSH ({@code -guardian}) and an SMS
 * ({@code -guardian-sms}) notification (distinct dedupe keys); a guardian with a null/blank phone
 * gets PUSH only; {@code RESOLVED} stays PUSH-only. Per-recipient AND per-channel best-effort: one
 * guardian's SMS send failing must not affect that guardian's PUSH or other guardians' notifications.
 *
 * Pure Mockito (no DB / no Solapi): recipient resolution and delivery are delegated to mocked
 * collaborators, and assertions are made on the {@link Notification} rows handed to
 * {@code NotificationService.dispatch} (mirrors {@code StaffAlertServiceTest}). The real SMS path
 * ({@code dispatch} → {@code SmsPort} → {@code users.phone}) is covered by {@code NotificationDispatchTest}.
 */
@ExtendWith(MockitoExtension.class)
class GuardianNotificationSmsTest {

    @Mock ClassRoomAssignmentRepository classRoomAssignmentRepository;
    @Mock ChildClassAssignmentRepository childClassAssignmentRepository;
    @Mock ChildGuardianRelationshipRepository childGuardianRelationshipRepository;
    @Mock DetectionEventRepository detectionEventRepository;
    @Mock KindergartenRepository kindergartenRepository;
    @Mock NotificationRepository notificationRepository;
    @Mock NotificationService notificationService;
    @Mock UserRepository userRepository;
    @Mock QuietHoursService quietHoursService;

    @InjectMocks GuardianNotificationService service;

    private static final long EVENT_ID = 7L;
    private static final long KG_ID = 1L;
    private static final long ROOM_ID = 11L;
    private static final OffsetDateTime DETECTED = OffsetDateTime.parse("2026-06-24T10:00:00Z");

    private void stubGuardians(Long... userIds) {
        // affectedChildIds path (public-space) keeps resolution simple and avoids the room→class chain.
        when(childGuardianRelationshipRepository
                .findActiveGuardianUserIds(any(), any(), any(), any()))
                .thenReturn(List.of(userIds));
        lenient().when(kindergartenRepository.getReferenceById(KG_ID)).thenReturn(mock(Kindergarten.class));
        lenient().when(detectionEventRepository.getReferenceById(EVENT_ID)).thenReturn(mock(DetectionEvent.class));
        lenient().when(userRepository.getReferenceById(anyLong())).thenReturn(mock(User.class));
        // ESCALATED never computes a quiet window; RESOLVED would — keep it lenient + empty.
        lenient().when(quietHoursService.resolveQuietWindow(any(), any())).thenReturn(Optional.empty());
    }

    private void stubPhones(User... users) {
        when(userRepository.findAllById(any())).thenReturn(List.of(users));
    }

    private User user(long id, String phone) {
        return User.builder().id(id).phone(phone).build();
    }

    /** affectedChildIds is non-empty so resolveRecipients uses the guardian stub directly. */
    private void notifyEscalated() {
        service.notifyOnReview(EVENT_ID, KG_ID, EventStatusEnum.ESCALATED,
                ROOM_ID, DETECTED, List.of(99L), null);
    }

    private List<Notification> dispatched() {
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationService, atLeast(0)).dispatch(captor.capture());
        return captor.getAllValues();
    }

    @Test
    void escalatedGuardianWithPhone_getsBothPushAndSms_distinctDedupeKeys() {
        stubGuardians(121L);
        stubPhones(user(121L, "010-1000-0121"));

        notifyEscalated();

        List<Notification> dispatched = dispatched();
        assertThat(dispatched).hasSize(2);
        assertThat(dispatched).extracting(Notification::getChannel)
                .containsExactlyInAnyOrder(NotificationChannelEnum.PUSH, NotificationChannelEnum.SMS);
        assertThat(dispatched).extracting(Notification::getDedupeKey)
                .containsExactlyInAnyOrder("evt-7-u-121-guardian", "evt-7-u-121-guardian-sms");
        verify(notificationRepository, times(2)).save(any());
    }

    @Test
    void escalatedGuardianWithoutPhone_getsPushOnly() {
        stubGuardians(121L);
        stubPhones(user(121L, null));

        notifyEscalated();

        List<Notification> dispatched = dispatched();
        assertThat(dispatched).hasSize(1);
        assertThat(dispatched.get(0).getChannel()).isEqualTo(NotificationChannelEnum.PUSH);
        assertThat(dispatched.get(0).getDedupeKey()).isEqualTo("evt-7-u-121-guardian");
    }

    @Test
    void escalatedGuardianWithBlankPhone_getsPushOnly() {
        stubGuardians(121L);
        stubPhones(user(121L, "   "));

        notifyEscalated();

        List<Notification> dispatched = dispatched();
        assertThat(dispatched).hasSize(1);
        assertThat(dispatched.get(0).getChannel()).isEqualTo(NotificationChannelEnum.PUSH);
    }

    @Test
    void resolvedOptedIn_getsPushOnly_noSms() {
        when(childGuardianRelationshipRepository
                .findActiveGuardianUserIds(any(), any(), any(), any()))
                .thenReturn(List.of(121L));
        lenient().when(kindergartenRepository.getReferenceById(KG_ID)).thenReturn(mock(Kindergarten.class));
        lenient().when(detectionEventRepository.getReferenceById(EVENT_ID)).thenReturn(mock(DetectionEvent.class));
        lenient().when(userRepository.getReferenceById(anyLong())).thenReturn(mock(User.class));
        when(quietHoursService.resolveQuietWindow(any(), any())).thenReturn(Optional.empty());

        service.notifyOnReview(EVENT_ID, KG_ID, EventStatusEnum.RESOLVED,
                ROOM_ID, DETECTED, List.of(99L), true);

        List<Notification> dispatched = dispatched();
        assertThat(dispatched).hasSize(1);
        assertThat(dispatched.get(0).getChannel()).isEqualTo(NotificationChannelEnum.PUSH);
        assertThat(dispatched.get(0).getDedupeKey()).isEqualTo("evt-7-u-121-guardian");
        // No phone batch-load and no SMS row for RESOLVED.
        verify(userRepository, times(0)).findAllById(any());
    }

    @Test
    void oneGuardianSmsFailure_doesNotAffectItsPushOrOtherGuardians() {
        stubGuardians(121L, 221L);
        stubPhones(user(121L, "010-1000-0121"), user(221L, "010-2000-0221"));
        // The guardian-121 SMS dispatch blows up; its PUSH and all of guardian-221 must still be dispatched.
        // (Other dispatch calls default to the void no-op.)
        doThrow(new RuntimeException("solapi boom"))
                .when(notificationService).dispatch(argThatDedupe("evt-7-u-121-guardian-sms"));

        notifyEscalated();

        List<Notification> dispatched = dispatched();
        // 2 guardians × (PUSH + SMS) = 4 dispatch attempts even though one SMS threw.
        assertThat(dispatched).extracting(Notification::getDedupeKey)
                .containsExactlyInAnyOrder(
                        "evt-7-u-121-guardian", "evt-7-u-121-guardian-sms",
                        "evt-7-u-221-guardian", "evt-7-u-221-guardian-sms");
    }

    private static Notification argThatDedupe(String dedupeKey) {
        return org.mockito.ArgumentMatchers.argThat(
                n -> n != null && dedupeKey.equals(n.getDedupeKey()));
    }
}
