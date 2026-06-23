package com.ai_kids_care.v1.service;

import com.ai_kids_care.v1.entity.DetectionEvent;
import com.ai_kids_care.v1.entity.Kindergarten;
import com.ai_kids_care.v1.entity.Notification;
import com.ai_kids_care.v1.entity.UserRoleAssignment;
import com.ai_kids_care.v1.repository.DetectionEventRepository;
import com.ai_kids_care.v1.repository.KindergartenRepository;
import com.ai_kids_care.v1.repository.NotificationRepository;
import com.ai_kids_care.v1.repository.UserRepository;
import com.ai_kids_care.v1.repository.UserRoleAssignmentRepository;
import com.ai_kids_care.v1.type.NotificationChannelEnum;
import com.ai_kids_care.v1.type.NotificationStatusEnum;
import com.ai_kids_care.v1.type.StatusEnum;
import com.ai_kids_care.v1.type.UserRoleAssignmentScopeType;
import com.ai_kids_care.v1.type.UserRoleEnum;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Immediate (pre-review) staff alert for an ingested detection event. Role-based: every ACTIVE
 * KINDERGARTEN_ADMIN / TEACHER of the event's kindergarten gets a PUSH Notification (Pushover via
 * their push_subscriptions) + an in-app row. Runs @Async so N staff × Pushover does not block the
 * AI's ingest POST. Parents are NOT notified here (guardian notification only after review).
 */
@Service
@RequiredArgsConstructor
public class StaffAlertService {

    private static final List<UserRoleEnum> STAFF_ROLES =
            List.of(UserRoleEnum.KINDERGARTEN_ADMIN, UserRoleEnum.TEACHER);

    private final UserRoleAssignmentRepository roleAssignmentRepository;
    private final NotificationRepository notificationRepository;
    private final NotificationService notificationService;
    private final KindergartenRepository kindergartenRepository;
    private final DetectionEventRepository detectionEventRepository;
    private final UserRepository userRepository;

    /**
     * Create + dispatch a staff alert for an already-persisted detection event. Public + directly
     * callable (tests invoke it synchronously); in production it is invoked on a separate thread.
     */
    @Async
    @Transactional
    public void alertForEvent(Long eventId, Long kindergartenId, String eventTypeLabel) {
        Set<Long> staffUserIds = new LinkedHashSet<>();
        for (UserRoleAssignment ura : roleAssignmentRepository.findAllByStatusAndScopeTypeAndScopeIdAndRoleIn(
                StatusEnum.ACTIVE, UserRoleAssignmentScopeType.KINDERGARTEN, kindergartenId, STAFF_ROLES)) {
            staffUserIds.add(ura.getUser().getId());
        }
        if (staffUserIds.isEmpty()) {
            return;
        }

        Kindergarten kindergarten = kindergartenRepository.getReferenceById(kindergartenId);
        DetectionEvent event = detectionEventRepository.getReferenceById(eventId);
        String title = "검토 필요 알림";
        String body = "새 감지 이벤트(" + eventTypeLabel + ")가 접수되었습니다. 시스템에서 검토해 주세요.";

        for (Long userId : staffUserIds) {
            Notification notification = Notification.builder()
                    .kindergarten(kindergarten)
                    .detectionEvents(event)
                    .recipientUser(userRepository.getReferenceById(userId))
                    .channel(NotificationChannelEnum.PUSH)
                    .title(title)
                    .body(body)
                    .status(NotificationStatusEnum.QUEUED)
                    .dedupeKey("evt-" + eventId + "-u-" + userId + "-staff")
                    .build();
            notificationRepository.save(notification);
            notificationService.dispatch(notification);
        }
    }
}
