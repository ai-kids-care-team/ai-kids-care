package com.ai_kids_care.v1.service;

import com.ai_kids_care.v1.entity.Kindergarten;
import com.ai_kids_care.v1.entity.Notification;
import com.ai_kids_care.v1.entity.User;
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
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Closed-loop step ③a: on review confirmation, notify the guardians of the affected children via
 * PUSH. ESCALATED forces notification; RESOLVED only when notifyGuardians is true; other result
 * statuses notify nobody. Recipients come from the relationship graph (room + detected_at → active
 * class_room_assignment → class → active child_class_assignment → children → active
 * child_guardian_relationship → guardian's user), or from explicit affectedChildIds for public-space
 * events. Listens AFTER_COMMIT + @Async so a dispatch failure never rolls back the authoritative
 * review; each recipient is independent and best-effort (mirrors StaffAlertService).
 *
 * UX-08: RESOLVED (non-urgent) quiet-hours/enabled deferral is decided per-recipient — each
 * guardian's own {@code notification_rules} canonical row (via
 * {@link NotificationPreferenceService#findCanonical}) overrides the kindergarten-wide default
 * when present and {@code enabled}; a guardian with {@code enabled=false} is skipped entirely for
 * RESOLVED. ESCALATED is a safety-critical path and is completely unaffected by this: it never
 * consults preferences, is never deferred, and always pierces quiet hours (plus the additive SMS
 * channel) — see notifyOnReview below.
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
    private final QuietHoursService quietHoursService;
    private final NotificationPreferenceService notificationPreferenceService;

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

        OffsetDateTime now = OffsetDateTime.now();
        boolean resolved = resultStatus == EventStatusEnum.RESOLVED;
        // UX-08: the kindergarten-wide fallback window (raw json, not yet parsed) — only needed for
        // RESOLVED's per-recipient defer decision below; a real query (not getReferenceById), safe on
        // the AFTER_COMMIT @Async thread with no open session. ESCALATED never reads this.
        String kindergartenQuietJson = resolved
                ? kindergartenRepository.findById(kindergartenId).map(Kindergarten::getQuietHoursJson).orElse(null)
                : null;

        // SMS is an additive channel only for ESCALATED (dual-channel PUSH+SMS); RESOLVED stays
        // PUSH-only. To create an SMS row only for guardians who actually have a phone (mirrors the
        // staff SMS gate, avoiding FAILED noise for phoneless guardians), batch-load the resolved
        // guardian users once and map user_id → phone. RESOLVED skips the load entirely.
        boolean escalated = resultStatus == EventStatusEnum.ESCALATED;
        // For ESCALATED, eagerly load the full guardian User entities up front. The SMS path reads
        // users.phone in NotificationService.dispatchSms, but this method runs on the @Async
        // AFTER_COMMIT listener with no open persistence session — a lazy getReferenceById proxy
        // would throw LazyInitializationException when phone is first touched there (swallowed by
        // deliver()'s best-effort catch, leaving the SMS row stuck at QUEUED). A loaded entity
        // carries the phone scalar, so getPhone() is safe on it even once detached. RESOLVED is
        // PUSH-only and only needs the user id, where a proxy is fine.
        Map<Long, User> guardianUsers = escalated ? loadUsers(guardianUserIds) : Map.of();

        for (Long userId : guardianUserIds) {
            boolean defer = false;
            OffsetDateTime deferUntil = null;
            if (resolved) {
                // UX-08: RESOLVED only — never consulted for ESCALATED (safety-critical pierce).
                DeferralDecision decision =
                        resolveDeferralForRecipient(userId, kindergartenId, kindergartenQuietJson, now);
                if (decision.skip()) {
                    continue; // guardian turned off notifications entirely; RESOLVED is non-urgent → skip
                }
                defer = decision.defer();
                deferUntil = decision.deferUntil();
            }

            User recipient = guardianUsers.get(userId);
            if (recipient == null) {
                recipient = userRepository.getReferenceById(userId);
            }
            // PUSH — unchanged behavior (every guardian; honors quiet-hours deferral for RESOLVED).
            deliver(kindergarten, eventId, recipient,
                    NotificationChannelEnum.PUSH, title, body,
                    "evt-" + eventId + "-u-" + userId + "-guardian",
                    defer, deferUntil);
            // SMS — only for ESCALATED, only when this guardian's users.phone is non-blank. ESCALATED
            // pierces quiet hours, so the SMS is always immediate (never deferred).
            if (escalated) {
                String phone = recipient.getPhone();
                if (phone != null && !phone.isBlank()) {
                    deliver(kindergarten, eventId, recipient,
                            NotificationChannelEnum.SMS, title, body,
                            "evt-" + eventId + "-u-" + userId + "-guardian-sms",
                            false, null);
                }
            }
        }
    }

    /**
     * UX-08: resolve the RESOLVED-only per-recipient defer decision — whether to skip this guardian
     * entirely (they disabled notifications) or, if not skipped, whether their own quiet-hours window
     * (falling back to the kindergarten-wide default) currently defers the PUSH and until when. Only
     * ever invoked for RESOLVED (see caller); ESCALATED never consults preferences. Exactly one
     * {@link NotificationPreferenceService#findCanonical} call per invocation — same per-recipient
     * query count as before this method was extracted.
     */
    private DeferralDecision resolveDeferralForRecipient(Long userId, Long kindergartenId,
                                                         String kindergartenQuietJson, OffsetDateTime now) {
        NotificationPreferenceService.NotificationPreferenceSnapshot canonical =
                notificationPreferenceService.findCanonical(kindergartenId, userId);
        if (canonical != null && !canonical.enabled()) {
            return DeferralDecision.skipped();
        }
        boolean useOwnWindow = canonical != null && canonical.enabled()
                && StringUtils.hasText(canonical.quietHoursJson());
        String effectiveJson = useOwnWindow ? canonical.quietHoursJson() : kindergartenQuietJson;
        Optional<QuietHoursService.QuietWindow> quietWindow = quietHoursService.parse(effectiveJson);
        boolean defer = quietWindow.isPresent() && quietHoursService.isWithinQuietHours(quietWindow.get(), now);
        OffsetDateTime deferUntil = defer ? quietHoursService.nextEndInstant(quietWindow.get(), now) : null;
        return DeferralDecision.proceed(defer, deferUntil);
    }

    /**
     * Result of {@link #resolveDeferralForRecipient}: either {@link #skipped} the guardian entirely
     * (notifications disabled), or {@link #proceed} with a defer flag and (if deferred) the instant
     * quiet hours end.
     */
    private record DeferralDecision(boolean skip, boolean defer, OffsetDateTime deferUntil) {
        static DeferralDecision skipped() {
            return new DeferralDecision(true, false, null);
        }

        static DeferralDecision proceed(boolean defer, OffsetDateTime deferUntil) {
            return new DeferralDecision(false, defer, deferUntil);
        }
    }

    /**
     * Batch-load the resolved guardian users (used to gate SMS creation and to supply a session-safe
     * recipient entity whose phone scalar is already initialized). One query for the whole recipient
     * set; users without a row simply have no entry (treated as no phone).
     */
    private Map<Long, User> loadUsers(Set<Long> guardianUserIds) {
        Map<Long, User> users = new HashMap<>();
        for (User u : userRepository.findAllById(guardianUserIds)) {
            users.put(u.getId(), u);
        }
        return users;
    }

    /**
     * Build + persist + dispatch a single guardian notification on one channel. Each channel call is
     * wrapped in its own try/catch so a per-channel failure — a dedupe-key clash (already notified),
     * a missing phone, or a delivery error — neither rolls back nor skips the guardian's other channel
     * or any other guardian. PUSH and SMS use distinct dedupe keys, so {@code (kindergarten_id,
     * dedupe_key)} stays unique across both rows for the same event + guardian. The review is already
     * committed (AFTER_COMMIT) regardless. Deferred PUSH rows wait for the scanner; SMS is never
     * deferred in this slice (ESCALATED only).
     */
    private void deliver(Kindergarten kindergarten, Long eventId, User recipient,
                         NotificationChannelEnum channel, String title, String body,
                         String dedupeKey, boolean defer, OffsetDateTime deferUntil) {
        try {
            Notification notification = Notification.builder()
                    .kindergarten(kindergarten)
                    .detectionEvents(detectionEventRepository.getReferenceById(eventId))
                    .recipientUser(recipient)
                    .channel(channel)
                    .title(title)
                    .body(body)
                    .status(defer ? NotificationStatusEnum.DEFERRED : NotificationStatusEnum.QUEUED)
                    .deferredUntil(deferUntil)
                    .dedupeKey(dedupeKey)
                    .build();
            notificationRepository.save(notification);    // own (auto) commit, independent of others
            if (!defer) {
                notificationService.dispatch(notification);  // immediate; deferred ones wait for the scanner
            }
        } catch (RuntimeException e) {
            // Best-effort (per-recipient + per-channel): a dedupe-key clash or one channel's failure
            // must not abort the guardian's other channel or the rest of the recipients.
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
