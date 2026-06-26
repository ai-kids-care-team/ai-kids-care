package com.ai_kids_care.v1.service;

import com.ai_kids_care.v1.entity.DetectionEvent;
import com.ai_kids_care.v1.entity.Kindergarten;
import com.ai_kids_care.v1.entity.Notification;
import com.ai_kids_care.v1.entity.User;
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
     * callable (tests invoke it); in production it is invoked on a separate thread (@Async).
     *
     * Deliberately NOT @Transactional: each staff member's notification is saved + dispatched
     * independently and wrapped in try/catch, so one member's delivery failure (e.g. a transient
     * DataAccessException during subscription lookup) neither rolls back the other members'
     * already-committed rows nor aborts the loop. Best-effort, per the pre-review staff alert intent.
     */
    @Async
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
            try {
                User recipient = userRepository.findById(userId).orElse(null);
                if (recipient == null) {
                    continue;
                }
                // PUSH (Pushover via push_subscriptions) — always attempted.
                deliver(kindergarten, event, recipient, NotificationChannelEnum.PUSH, title, body,
                        "evt-" + eventId + "-u-" + userId + "-staff");
                // SMS (Solapi via users.phone) — only when the staff member has a phone.
                String phone = recipient.getPhone();
                if (phone != null && !phone.isBlank()) {
                    deliver(kindergarten, event, recipient, NotificationChannelEnum.SMS, title, body,
                            "evt-" + eventId + "-u-" + userId + "-staff-sms");
                }
            } catch (RuntimeException e) {
                // Best-effort (per-recipient): one staff member's lookup failure must not abort the rest.
            }
        }
    }

    /**
     * Build + persist + dispatch a single staff notification on one channel. Wrapped in its own
     * try/catch so a per-channel delivery failure (e.g. PUSH with no subscription, or SMS with no
     * phone recorded FAILED inside dispatch) neither rolls back nor skips the staff member's other
     * channel. PUSH and SMS use distinct dedupe keys, so {@code (kindergarten_id, dedupe_key)} stays
     * unique across both rows for the same event + recipient.
     */
    private void deliver(Kindergarten kindergarten, DetectionEvent event, User recipient,
                         NotificationChannelEnum channel, String title, String body, String dedupeKey) {
        try {
            Notification notification = Notification.builder()
                    .kindergarten(kindergarten)
                    .detectionEvents(event)
                    .recipientUser(recipient)
                    .channel(channel)
                    .title(title)
                    .body(body)
                    .status(NotificationStatusEnum.QUEUED)
                    .dedupeKey(dedupeKey)
                    .build();
            notificationRepository.save(notification);   // own (auto) commit, independent of others
            notificationService.dispatch(notification);  // own @Transactional; channel-specific lifecycle
        } catch (RuntimeException e) {
            // Best-effort (per-channel): a single channel's delivery failure must not abort the rest.
        }
    }
}
