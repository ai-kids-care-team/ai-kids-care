package com.ai_kids_care.v1.service;

import com.ai_kids_care.v1.entity.Kindergarten;
import com.ai_kids_care.v1.entity.Notification;
import com.ai_kids_care.v1.event.EventReviewedEvent;
import com.ai_kids_care.v1.repository.ChildClassAssignmentRepository;
import com.ai_kids_care.v1.repository.ChildGuardianRelationshipRepository;
import com.ai_kids_care.v1.repository.ClassRoomAssignmentRepository;
import com.ai_kids_care.v1.repository.DetectionEventRepository;
import com.ai_kids_care.v1.repository.KindergartenRepository;
import com.ai_kids_care.v1.repository.NotificationRepository;
import com.ai_kids_care.v1.repository.UserRepository;
import com.ai_kids_care.v1.type.EventStatusEnum;
import com.ai_kids_care.v1.type.NotificationChannelEnum;
import com.ai_kids_care.v1.type.NotificationStatusEnum;
import com.ai_kids_care.v1.type.StatusEnum;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Closed-loop step ③a: on review confirmation, notify the guardians of the affected children via
 * PUSH. ESCALATED forces notification; RESOLVED only when notifyGuardians is true; other result
 * statuses notify nobody. Recipients come from the relationship graph (room + detected_at → active
 * class_room_assignment → class → active child_class_assignment → children → active
 * child_guardian_relationship → guardian's user), or from explicit affectedChildIds for public-space
 * events. Listens AFTER_COMMIT + @Async so a dispatch failure never rolls back the authoritative
 * review; each recipient is independent and best-effort (mirrors StaffAlertService). All
 * notifications are immediate in this slice — quiet-hours deferral is ③b.
 */
@Service
@RequiredArgsConstructor
public class GuardianNotificationService {

    private static final Logger log = LoggerFactory.getLogger(GuardianNotificationService.class);

    private final ClassRoomAssignmentRepository classRoomAssignmentRepository;
    private final ChildClassAssignmentRepository childClassAssignmentRepository;
    private final ChildGuardianRelationshipRepository childGuardianRelationshipRepository;
    private final DetectionEventRepository detectionEventRepository;
    private final KindergartenRepository kindergartenRepository;
    private final NotificationRepository notificationRepository;
    private final NotificationService notificationService;
    private final UserRepository userRepository;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onEventReviewed(EventReviewedEvent ev) {
        notifyOnReview(ev.eventId(), ev.kindergartenId(), ev.resultStatus(),
                ev.roomId(), ev.detectedAt(), ev.affectedChildIds(), ev.notifyGuardians());
    }

    /**
     * Resolve guardian recipients and dispatch a PUSH notification to each. Public + synchronous so
     * tests can invoke it directly; the AFTER_COMMIT @Async listener above is the production trigger.
     */
    public void notifyOnReview(Long eventId, Long kindergartenId, EventStatusEnum resultStatus,
                               Long roomId, OffsetDateTime detectedAt,
                               List<Long> affectedChildIds, Boolean notifyGuardians) {
        boolean shouldNotify = resultStatus == EventStatusEnum.ESCALATED
                || (resultStatus == EventStatusEnum.RESOLVED && Boolean.TRUE.equals(notifyGuardians));
        if (!shouldNotify) {
            return;
        }

        Set<Long> guardianUserIds = resolveRecipients(roomId, detectedAt, kindergartenId, affectedChildIds);
        if (guardianUserIds.isEmpty()) {
            log.warn("Step ③: no guardian recipients resolved for event {} (kindergarten {}, room {}); "
                    + "skipping guardian notification", eventId, kindergartenId, roomId);
            return;
        }

        Kindergarten kindergarten = kindergartenRepository.getReferenceById(kindergartenId);
        String title = "안전 알림";
        String body = "자녀와 관련된 감지 이벤트가 검토 후 "
                + "확인되었습니다. 앱에서 자세한 내용을 "
                + "확인해 주세요.";

        for (Long userId : guardianUserIds) {
            try {
                Notification notification = Notification.builder()
                        .kindergarten(kindergarten)
                        .detectionEvents(detectionEventRepository.getReferenceById(eventId))
                        .recipientUser(userRepository.getReferenceById(userId))
                        .channel(NotificationChannelEnum.PUSH)
                        .title(title)
                        .body(body)
                        .status(NotificationStatusEnum.QUEUED)
                        .dedupeKey("evt-" + eventId + "-u-" + userId + "-guardian")
                        .build();
                notificationRepository.save(notification);   // own (auto) commit, independent of others
                notificationService.dispatch(notification);  // own @Transactional; FAILED on no subscription
            } catch (RuntimeException e) {
                // Best-effort: a dedupe-key clash (already notified) or one recipient's failure must
                // not abort the rest. The review is already committed regardless.
            }
        }
    }

    private Set<Long> resolveRecipients(Long roomId, OffsetDateTime detectedAt, Long kindergartenId,
                                        List<Long> affectedChildIds) {
        LocalDate date = detectedAt.toLocalDate();
        Collection<Long> childIds;
        if (affectedChildIds != null && !affectedChildIds.isEmpty()) {
            childIds = affectedChildIds;
        } else {
            List<Long> classIds = classRoomAssignmentRepository
                    .findActiveClassIds(roomId, kindergartenId, StatusEnum.ACTIVE, detectedAt);
            if (classIds.isEmpty()) {
                return Set.of();
            }
            childIds = childClassAssignmentRepository
                    .findActiveChildIds(classIds, kindergartenId, StatusEnum.ACTIVE, date);
        }
        if (childIds.isEmpty()) {
            return Set.of();
        }
        return new LinkedHashSet<>(childGuardianRelationshipRepository
                .findActiveGuardianUserIds(childIds, kindergartenId, StatusEnum.ACTIVE, date));
    }
}
