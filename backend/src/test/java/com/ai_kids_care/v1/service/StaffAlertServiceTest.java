package com.ai_kids_care.v1.service;

import com.ai_kids_care.v1.entity.Kindergarten;
import com.ai_kids_care.v1.entity.DetectionEvent;
import com.ai_kids_care.v1.entity.Notification;
import com.ai_kids_care.v1.entity.User;
import com.ai_kids_care.v1.entity.UserRoleAssignment;
import com.ai_kids_care.v1.repository.DetectionEventRepository;
import com.ai_kids_care.v1.repository.KindergartenRepository;
import com.ai_kids_care.v1.repository.NotificationRepository;
import com.ai_kids_care.v1.repository.UserRepository;
import com.ai_kids_care.v1.repository.UserRoleAssignmentRepository;
import com.ai_kids_care.v1.type.NotificationChannelEnum;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@code StaffAlertService} dual-channel delivery (⑤). A staff member with a
 * {@code users.phone} gets both a PUSH and an SMS notification (distinct dedupe keys); a staff
 * member without a phone gets PUSH only. One channel/recipient failing must not abort the rest
 * (best-effort). Delivery itself is delegated to (mocked) {@code NotificationService.dispatch}.
 */
@ExtendWith(MockitoExtension.class)
class StaffAlertServiceTest {

    @Mock UserRoleAssignmentRepository roleAssignmentRepository;
    @Mock NotificationRepository notificationRepository;
    @Mock NotificationService notificationService;
    @Mock KindergartenRepository kindergartenRepository;
    @Mock DetectionEventRepository detectionEventRepository;
    @Mock UserRepository userRepository;

    @InjectMocks StaffAlertService service;

    private static final long EVENT_ID = 7L;
    private static final long KG_ID = 3L;

    private UserRoleAssignment assignmentFor(long userId) {
        User u = User.builder().id(userId).build();
        UserRoleAssignment ura = mock(UserRoleAssignment.class);
        when(ura.getUser()).thenReturn(u);
        return ura;
    }

    private void stubStaff(UserRoleAssignment... assignments) {
        when(roleAssignmentRepository.findAllByStatusAndScopeTypeAndScopeIdAndRoleIn(
                any(), any(), eq(KG_ID), any())).thenReturn(List.of(assignments));
        lenient().when(kindergartenRepository.getReferenceById(KG_ID)).thenReturn(mock(Kindergarten.class));
        lenient().when(detectionEventRepository.getReferenceById(EVENT_ID)).thenReturn(mock(DetectionEvent.class));
    }

    private List<Notification> dispatchedNotifications() {
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationService, atLeast(0)).dispatch(captor.capture());
        return captor.getAllValues();
    }

    @Test
    void staffWithPhone_getsBothPushAndSms_distinctDedupeKeys() {
        when(userRepository.findById(1L))
                .thenReturn(Optional.of(User.builder().id(1L).phone("01012345678").build()));
        stubStaff(assignmentFor(1L));

        service.alertForEvent(EVENT_ID, KG_ID, "LATER_THAN_USUAL");

        List<Notification> dispatched = dispatchedNotifications();
        assertThat(dispatched).hasSize(2);
        assertThat(dispatched).extracting(Notification::getChannel)
                .containsExactlyInAnyOrder(NotificationChannelEnum.PUSH, NotificationChannelEnum.SMS);
        assertThat(dispatched).extracting(Notification::getDedupeKey)
                .containsExactlyInAnyOrder("evt-7-u-1-staff", "evt-7-u-1-staff-sms");
        verify(notificationRepository, times(2)).save(any());
    }

    @Test
    void staffWithoutPhone_getsPushOnly() {
        when(userRepository.findById(1L))
                .thenReturn(Optional.of(User.builder().id(1L).phone(null).build()));
        stubStaff(assignmentFor(1L));

        service.alertForEvent(EVENT_ID, KG_ID, "LATER_THAN_USUAL");

        List<Notification> dispatched = dispatchedNotifications();
        assertThat(dispatched).hasSize(1);
        assertThat(dispatched.get(0).getChannel()).isEqualTo(NotificationChannelEnum.PUSH);
        assertThat(dispatched.get(0).getDedupeKey()).isEqualTo("evt-7-u-1-staff");
    }

    @Test
    void oneChannelFailure_doesNotAbortOtherStaff() {
        when(userRepository.findById(1L))
                .thenReturn(Optional.of(User.builder().id(1L).phone("01011112222").build()));
        when(userRepository.findById(2L))
                .thenReturn(Optional.of(User.builder().id(2L).phone("01033334444").build()));
        stubStaff(assignmentFor(1L), assignmentFor(2L));
        // user 1's PUSH dispatch blows up; user 1's SMS and all of user 2 must still be attempted.
        doThrow(new RuntimeException("boom"))
                .doNothing()
                .when(notificationService).dispatch(any());

        service.alertForEvent(EVENT_ID, KG_ID, "LATER_THAN_USUAL");

        // 2 staff × 2 channels = 4 dispatch attempts even though the first threw.
        verify(notificationService, times(4)).dispatch(any());
    }
}
