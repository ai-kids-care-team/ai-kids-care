# notifications Specification

## Purpose
定义通知能力：后端拥有的通知派发、规则引擎收件人解析、Pushover 主通道、(kindergarten_id, dedupe_key) 去重与投递生命周期、租户 scoped 只读 API；当前 SMS/规则引擎管道/设备令牌 API 多为未接线状态（见 spec 内如实记录）。
## Requirements
### Requirement: Backend-owned notification dispatch

The backend SHALL be the sole initiator of notifications; the AI inference layer MUST NOT send notifications directly to recipients. All notification dispatch MUST go through the backend notification subsystem, which queries recipients from the database and resolves delivery channels via `notification_rules` and per-user push delivery identities stored in `push_subscriptions`.

#### Scenario: AI detection result triggers backend notification

- **WHEN** a detection event is persisted by the AI inference layer
- **THEN** the backend notification subsystem, not the AI layer, is responsible for determining recipients and dispatching any resulting notifications

#### Scenario: AI-side demo delivery code is absent

- **WHEN** the production system is running
- **THEN** no Pushover or SMS delivery code in `ai/` sends notifications; those files (e.g., `ai/src/ai_app/utils/pushover.py`, `ai/src/ai_app/utils/sms.py`) are demo stubs that MUST be removed before the closed-loop AI detection feature ships

---

### Requirement: Guardian notification gate on human review

Notifications addressed to recipients with role GUARDIAN SHALL only be dispatched after a human reviewer has confirmed the detection event in `event_reviews`. Unreviewed detection events MUST NOT trigger guardian notifications.

#### Scenario: Unreviewed detection event — no guardian notification

- **WHEN** a detection event is created and its associated `event_reviews` record has not been confirmed
- **THEN** no notification is dispatched to a GUARDIAN recipient

#### Scenario: Reviewed detection event — guardian notification allowed

- **WHEN** the `event_reviews` record for a detection event is confirmed by a human reviewer
- **THEN** the notification subsystem MAY dispatch notifications to applicable GUARDIAN recipients according to active `notification_rules`

---

### Requirement: Rule-engine-driven recipient resolution

The notification subsystem SHALL resolve recipients and delivery conditions using `notification_rules`. Each active rule (`enabled = true`) scoped to a kindergarten specifies a user, target type (`ROOM`, `CAMERA`, or `KINDERGARTEN`), target ID, optional event type filter, minimum severity (`min_severity`), and optional quiet hours (`quiet_hours_json`).

#### Scenario: Rule filters by minimum severity

- **WHEN** a detection event's severity is below a rule's `min_severity` value
- **THEN** that rule MUST NOT generate a notification for the associated user

#### Scenario: Disabled rule is skipped

- **WHEN** a `notification_rules` row has `enabled = false`
- **THEN** no notification is dispatched under that rule regardless of event properties

#### Scenario: Target-scoped rule matches detection event

- **WHEN** a detection event originates from a camera or room that matches a rule's `target_type` / `target_id`
- **THEN** the rule is a candidate for generating a notification for the rule's `user_id`

---

### Requirement: Pushover as primary delivery channel

The backend SHALL deliver PUSH channel notifications via the Pushover third-party push service. The Pushover application credential (API token) MUST be supplied by configuration (`pushover.api-token`, sourced from the `PUSHOVER_API_TOKEN` environment variable) and MUST NOT be hard-coded; a blank credential MUST fail fast rather than be silently sent. Each recipient's Pushover delivery address (Pushover user key) SHALL be stored as an `address` row in `push_subscriptions` with `provider = PUSHOVER`; the FCM/APNS-shaped `device_tokens` table is superseded by `push_subscriptions` (see "Push delivery addressing model"). `NotificationChannelEnum` values are `PUSH`, `SMS`, and `EMAIL`; `PUSH` (Pushover, `PushoverService`) and `SMS` (Solapi, `SolapiSmsAdapter` — see "SMS delivery via Solapi adapter") have implemented backend delivery paths driving the delivery lifecycle status transitions; `EMAIL` does not yet.

#### Scenario: PUSH channel notification dispatched via Pushover

- **WHEN** a notification with `channel = PUSH` is dispatched for a recipient who has an `ACTIVE` `push_subscriptions` row with `provider = PUSHOVER`
- **THEN** the backend calls `PushoverService` with the configured API token and the recipient's stored Pushover user-key address plus the notification `title` and `body`, and on success sets `status = SENT` and `sent_at`

#### Scenario: PUSH dispatch with no active Pushover subscription

- **WHEN** a notification with `channel = PUSH` is dispatched for a recipient who has no `ACTIVE` `push_subscriptions` row with `provider = PUSHOVER`
- **THEN** no Pushover call is made and the notification is recorded as `FAILED` with a non-null `fail_reason` (no delivery to an empty/blank address is attempted)

#### Scenario: SMS channel — delivered via Solapi adapter

- **WHEN** a notification is created with `channel = SMS`
- **THEN** the system records it in the `notifications` table and dispatches it to the recipient's `users.phone` through the Solapi adapter (not a `push_subscriptions` row), per the "SMS delivery via Solapi adapter" requirement (SENDING → SENT/`sent_at` on success; FAILED + `fail_reason` on a missing phone or send failure)

#### Scenario: EMAIL channel — not implemented

- **WHEN** a notification is created with `channel = EMAIL`
- **THEN** the system records the notification but no email is dispatched; EMAIL is listed as a future option only (gap: no backend email delivery path exists)

### Requirement: Deduplication of notifications per detection event

The `notifications` table SHALL enforce a unique constraint on `(kindergarten_id, dedupe_key)`. The backend MUST set a `dedupe_key` that ties a notification to its originating detection event, preventing duplicate notifications for the same event.

#### Scenario: Duplicate notification attempt is rejected

- **WHEN** the backend attempts to insert a second `notifications` row with the same `kindergarten_id` and `dedupe_key`
- **THEN** the database constraint `uq_notifications_dedupe` rejects the insert, preventing duplicate notifications

---

### Requirement: Notification delivery lifecycle status

Each `notifications` row SHALL carry a `status` column typed `notification_status_enum` with values: `QUEUED`, `SENDING`, `SENT`, `DELIVERED`, `READ`, `FAILED`, `CANCELED`. The `sent_at` column is nullable (NULL while pending). The `fail_reason` column is nullable (NULL when no failure has occurred). The `retry_count` column is NOT NULL with a database default of `0`.

#### Scenario: New notification starts with nullable delivery fields

- **WHEN** a new notification row is created before any delivery attempt
- **THEN** `sent_at` is NULL and `fail_reason` is NULL; `retry_count` defaults to `0`

#### Scenario: Failed delivery is recorded

- **WHEN** a delivery attempt fails
- **THEN** `fail_reason` is set to a non-null description of the failure and `retry_count` is incremented

#### Scenario: Successful delivery updates sent_at

- **WHEN** a notification is successfully dispatched
- **THEN** `sent_at` is set to the timestamp of the successful send attempt

---

### Requirement: Tenant-scoped notification read API

The backend SHALL expose `GET /api/v1/notifications` and `GET /api/v1/notifications/{id}` for authenticated users. GUARDIAN and TEACHER recipients SHALL only receive their own notifications. KINDERGARTEN_ADMIN SHALL receive all notifications within their kindergarten. Cross-tenant access and access to another user's notifications MUST be denied. The published response shape is `NotificationReadVO` containing fields: `notificationId`, `title`, `body`, `status`, `createdAt`. Internal fields (`channel`, `dedupeKey`, `sentAt`, `failReason`, `retryCount`, `recipientUserId`, `kindergartenId`) MUST NOT appear in read responses.

#### Scenario: GUARDIAN or TEACHER reads own notifications

- **WHEN** a GUARDIAN or TEACHER user calls `GET /api/v1/notifications`
- **THEN** the response contains only notifications where `recipient_user_id` matches the caller's user ID within the caller's active kindergarten, ordered by `created_at` descending

#### Scenario: KINDERGARTEN_ADMIN reads all kindergarten notifications

- **WHEN** a KINDERGARTEN_ADMIN calls `GET /api/v1/notifications`
- **THEN** the response contains all notifications scoped to the admin's active kindergarten, ordered by `created_at` descending

#### Scenario: Access to another user's notification is denied

- **WHEN** a GUARDIAN or TEACHER calls `GET /api/v1/notifications/{id}` for a notification that belongs to a different user or a different kindergarten
- **THEN** the system records an `AUTHORIZATION_DENIED` audit event and returns HTTP 404

#### Scenario: Write operations not published on notification endpoint

- **WHEN** a client sends POST, PUT, or DELETE to `/api/v1/notifications`
- **THEN** the server returns HTTP 405 (no write handler is published in Phase 1A)

---

### Requirement: Notification rule API not yet published

The notification rule management API SHALL be considered not yet published. The `NotificationRuleController` is registered at `/api/v1/notification_rules` but MUST NOT expose handler methods until the rule management feature is implemented. `NotificationRuleService` methods are guarded with `@PreAuthorize("denyAll()")` (gap: rule management API is pending).

#### Scenario: Any request to notification rules endpoint

- **WHEN** a client sends any HTTP request to `/api/v1/notification_rules`
- **THEN** the server returns HTTP 405 (no handlers are registered)

---

### Requirement: Push delivery addressing model

Per-user PUSH delivery identities SHALL be stored in a provider-aware `push_subscriptions` table, not the superseded FCM/APNS-shaped `device_tokens` table. Each row SHALL carry `user_id`, `provider` (`push_provider_enum`, initially `PUSHOVER`), `address` (the provider delivery address — for Pushover, the user key), an optional `device_label`, a `status` (`status_enum`), and SHALL enforce uniqueness on `(user_id, provider, address)`. SMS and EMAIL delivery addresses SHALL NOT be stored here — those reuse `users.phone` and `users.email` respectively. The `address` column SHALL accommodate future providers (e.g., FCM/APNS device tokens) without a schema redesign.

#### Scenario: Recipient Pushover identity resolved for PUSH dispatch

- **WHEN** the backend dispatches a `PUSH` notification to a recipient
- **THEN** it resolves the recipient's `ACTIVE` `push_subscriptions` row with `provider = PUSHOVER` and uses its `address` as the Pushover user key

#### Scenario: Duplicate push subscription rejected

- **WHEN** a second `push_subscriptions` row is inserted with the same `(user_id, provider, address)`
- **THEN** the database uniqueness constraint rejects the insert

### Requirement: Push subscription self-service management API

The system SHALL publish a self-service API for an authenticated user to manage their own push
delivery subscriptions, so that PUSH (Pushover) notifications have a delivery address to target.
The endpoints are `POST /api/v1/push_subscriptions` (register), `GET /api/v1/push_subscriptions`
(list own), `PUT /api/v1/push_subscriptions/{id}` (update own), and `DELETE
/api/v1/push_subscriptions/{id}` (delete own). The `user_id` of a subscription SHALL be taken from
the authenticated session identity and MUST NOT be accepted from the client. Every operation SHALL
be scoped to the caller's own rows: a user MUST NOT read, modify, or delete another user's
subscription, and an attempt to access another user's subscription by id SHALL return `404`
(existence hidden). `provider` SHALL be restricted to `PUSHOVER` (the only implemented push
provider); any other value SHALL be rejected with `400`. A duplicate `(user_id, provider, address)`
SHALL be rejected with `409`. Subscription read responses MUST NOT expose the delivery `address`
(the Pushover user key).

#### Scenario: User registers their own Pushover subscription

- **WHEN** an authenticated user POSTs `/api/v1/push_subscriptions` with `provider=PUSHOVER`, an `address` (Pushover user key), and an optional `device_label`
- **THEN** the server creates an `ACTIVE` `push_subscriptions` row owned by the caller's `user_id` (ignoring any client-supplied user id) and returns `201` with a view that does not echo the `address`

#### Scenario: List returns only the caller's subscriptions

- **WHEN** an authenticated user GETs `/api/v1/push_subscriptions`
- **THEN** the response contains only subscriptions whose `user_id` is the caller's; no other user's subscription appears

#### Scenario: Accessing another user's subscription is hidden

- **WHEN** an authenticated user sends `PUT` or `DELETE` to `/api/v1/push_subscriptions/{id}` for a subscription owned by a different user
- **THEN** the server returns `404` and does not modify or delete the row

#### Scenario: Unsupported provider is rejected

- **WHEN** a register request uses a `provider` other than `PUSHOVER`
- **THEN** the server returns `400` and creates no row

#### Scenario: Duplicate subscription is rejected

- **WHEN** a register request repeats an existing `(user_id, provider, address)`
- **THEN** the server returns `409` and does not create a duplicate row

#### Scenario: Anonymous access is rejected

- **WHEN** an unauthenticated request is made to any `/api/v1/push_subscriptions` endpoint
- **THEN** the server returns `401`

#### Scenario: Registered subscription makes PUSH delivery deliverable

- **WHEN** a user has an `ACTIVE` `PUSHOVER` subscription and a `PUSH` notification for that user is dispatched
- **THEN** `NotificationService.dispatch` resolves the registered address and sends via Pushover (status `SENT`), rather than failing with "no active Pushover subscription"

### Requirement: Kindergarten staff immediate alert on AI alarm (pre-review)

The notification subsystem SHALL dispatch an immediate (pre-review) alert to applicable kindergarten
staff (KINDERGARTEN_ADMIN, TEACHER) when the backend ingests a detection event from the AI. The
alert trigger is the AI persistence-rule `alarm_on` (a debounced sliding-window signal — single-frame
threshold 0.60, ≥30s span, ≥~8 hits, ≥50% hit ratio, hysteresis clear at 40%, 120s cooldown), NOT a
single-inference confidence value; the backend therefore SHALL NOT define its own global confidence
threshold. Which staff/rules receive the alert MAY be further narrowed by `notification_rules`
(including `min_severity`). The staff alert SHALL be delivered over every channel the recipient has
available — Pushover (via the recipient's `push_subscriptions`) and SMS (via `users.phone`): if a
recipient has both, both are used; if only one, that one is used. The subsystem SHALL also record an
in-app notification and surface the event on the real-time frontend dashboard, so staff can quickly
enter the system to review and act. This staff alert does NOT notify parents (see the guardian
review-gate requirement).

#### Scenario: Staff alerted immediately on ingest, across available channels

- **WHEN** the backend ingests an AI detection event (alarm_on)
- **THEN** applicable staff receive an immediate alert via Pushover and/or SMS (whichever they have; both if both), an in-app notification is recorded, and the event appears on the frontend dashboard — without waiting for `event_reviews` confirmation

#### Scenario: No backend confidence threshold is required

- **WHEN** the staff-alert path is enabled in production
- **THEN** it relies on the AI persistence-rule alarm as the debounced trigger and does not gate on a backend-defined confidence threshold; per-rule `min_severity` is the only severity filter

#### Scenario: Staff alert does not reach parents

- **WHEN** a staff immediate alert is dispatched for an ingested detection event
- **THEN** no parent/guardian notification is sent by this path; guardian notification happens only after staff review confirmation

### Requirement: Guardian notification on review confirmation

The backend SHALL notify the guardians of the affected children when a staff review confirmation
(`POST /api/v1/event_reviews`) sets a detection event's `result_status` to `ESCALATED`, or to
`RESOLVED` with guardian notification opted in. This implements the "reviewed → MAY notify GUARDIAN"
path of the guardian review-gate. Notification is dispatched over the `PUSH` channel via the existing
`NotificationService.dispatch` (Pushover). For an `ESCALATED` confirmation the backend SHALL
**additionally** dispatch the notification over the `SMS` channel to each affected guardian whose
linked `users.phone` is non-blank, via the same `NotificationService.dispatch` (Solapi `SmsPort`); a
`RESOLVED` confirmation notifies over `PUSH` only. The PUSH dispatch is unchanged — SMS is an
additive, independent channel, and a guardian whose linked `users.phone` is null/blank still receives
the `PUSH` notification only. All notifications are dispatched immediately in this capability slice;
quiet-hours deferral is a separate later capability.

Recipient resolution SHALL be relationship-graph based (PostgreSQL, not Neo4j): the event's `room_id`
at its `detected_at` instant resolves to the active `class_room_assignment`(s) (`start_at <=
detected_at AND (end_at IS NULL OR end_at > detected_at) AND status = ACTIVE`), then to the active
`child_class_assignment` children of those classes, then to each child's active
`child_guardian_relationship` guardians, then to each guardian's `user_id`. For a public-space event
(a room with no active `class_room_assignment`), the confirm request MAY supply explicit
`affectedChildIds`; when present, recipients are resolved directly from those children's active
guardian relationships, bypassing the automatic room→class chain. Recipients are restricted to the
caller's kindergarten (the graph is filtered by `kindergarten_id`).

The trigger matrix is: `ESCALATED` always notifies; `RESOLVED` notifies only when `notifyGuardians`
is true; `ACKNOWLEDGED`, `IN_REVIEW`, and `DISMISSED` never notify guardians. Notification dispatch
is a side effect of confirm executed after the review transaction commits; a dispatch or resolution
failure MUST NOT roll back the review. Each guardian PUSH notification uses
`dedupe_key = 'evt-{eventId}-u-{guardianUserId}-guardian'` and each guardian SMS notification uses
`dedupe_key = 'evt-{eventId}-u-{guardianUserId}-guardian-sms'` (distinct keys so PUSH and SMS for the
same guardian and event coexist), so repeated confirms of the same event do not produce duplicate
notifications (enforced by `UNIQUE(kindergarten_id, dedupe_key)`). SMS dispatch is per-recipient and
per-channel best-effort: an SMS build or send failure for one guardian MUST NOT affect that guardian's
PUSH notification or any other guardian's notifications.

#### Scenario: ESCALATED confirm notifies guardians of the room's class children

- **WHEN** a staff member confirms a detection event with `result_status = ESCALATED`, and the
  event's room has an active `class_room_assignment` whose class has active children with active
  guardians
- **THEN** one `PUSH` `notifications` row is created and dispatched per distinct guardian `user_id`
  of those children, scoped to the event's kindergarten

#### Scenario: ESCALATED also sends SMS to guardians with a phone

- **WHEN** an `ESCALATED` confirm notifies guardians and an affected guardian's linked `users.phone`
  is non-blank
- **THEN** that guardian receives both a `PUSH` and an `SMS` `notifications` row (distinct dedupe
  keys `-guardian` and `-guardian-sms`), the SMS sent to that `users.phone`; **and WHEN** a notified
  guardian's linked `users.phone` is null/blank, only the `PUSH` row is created (no SMS)

#### Scenario: RESOLVED notifies only when opted in, over PUSH only

- **WHEN** a confirm sets `result_status = RESOLVED` with `notifyGuardians = true`
- **THEN** guardians are notified over `PUSH` only and no `SMS` notification is created; **and WHEN**
  `notifyGuardians` is absent or false, no guardian notification is created

#### Scenario: Non-notifying result statuses

- **WHEN** a confirm sets `result_status` to `ACKNOWLEDGED`, `IN_REVIEW`, or `DISMISSED`
- **THEN** no guardian notification is created

#### Scenario: Public-space event resolved via explicit affectedChildIds

- **WHEN** a confirm for an event whose room has no active `class_room_assignment` supplies
  `affectedChildIds`
- **THEN** recipients are the active guardians of those specified children; **and WHEN** such an
  event supplies no `affectedChildIds`, no guardian notification is created (the confirm still
  succeeds)

#### Scenario: Cross-tenant guardians are never notified

- **WHEN** recipient resolution runs
- **THEN** only guardians within the event's kindergarten are notified; a guardian of another
  kindergarten is never a recipient

#### Scenario: Duplicate confirm does not duplicate notifications

- **WHEN** the same event is confirmed again such that the same guardian would be notified
- **THEN** the `UNIQUE(kindergarten_id, dedupe_key)` constraint prevents a duplicate notification row

#### Scenario: Guardian without an active push subscription

- **WHEN** a resolved guardian recipient has no `ACTIVE` `PUSHOVER` `push_subscriptions` row
- **THEN** that guardian's notification is recorded `FAILED` with a non-null `fail_reason`, and other
  guardians' notifications are unaffected

#### Scenario: One guardian's SMS failure does not affect other channels or recipients

- **WHEN** the `SMS` send for one guardian fails (Solapi failure)
- **THEN** that guardian's `SMS` notification is recorded `FAILED` with a non-null `fail_reason`,
  while that guardian's `PUSH` notification and every other guardian's notifications remain unaffected

#### Scenario: Notification failure does not roll back the review

- **WHEN** guardian notification dispatch fails (resolution error or Pushover failure)
- **THEN** the `event_reviews` row and the `detection_events.status` update remain committed; the
  confirm response is unaffected

### Requirement: Quiet-hours deferral of guardian notifications

A guardian notification produced by a `RESOLVED` review confirmation SHALL be deferred when it falls
within the kindergarten's quiet hours, instead of being dispatched immediately: the notification is
persisted with `status = DEFERRED` and `deferred_until` set to the end of the current quiet window,
and a scheduled scanner dispatches it once `deferred_until` has passed. `ESCALATED` notifications are
never deferred — they pierce quiet hours and dispatch immediately (the step-③a behavior is
unchanged). Quiet hours are a kindergarten-wide window stored as `kindergartens
.notification_quiet_hours_json` (`{"start":"HH:mm","end":"HH:mm"}`, evaluated in the `Asia/Seoul`
time zone, supporting windows that cross midnight); an absent, empty, or unparseable configuration
means never deferred. Per-guardian quiet hours is a future evolution — the resolution carries a
guardian parameter but this slice resolves only the kindergarten-level window.

#### Scenario: RESOLVED notification within quiet hours is deferred

- **WHEN** a `RESOLVED` confirm would notify a guardian and the current time is within the
  kindergarten's quiet window
- **THEN** the notification is saved with `status = DEFERRED` and `deferred_until` = the quiet
  window's end (Asia/Seoul), and is NOT dispatched at confirm time

#### Scenario: ESCALATED notification pierces quiet hours

- **WHEN** an `ESCALATED` confirm notifies a guardian during the kindergarten's quiet window
- **THEN** the notification is dispatched immediately (not deferred), regardless of quiet hours

#### Scenario: RESOLVED notification outside quiet hours is immediate

- **WHEN** a `RESOLVED` confirm with guardian notification occurs outside any quiet window (or the
  kindergarten has no quiet-hours configuration)
- **THEN** the notification is dispatched immediately, exactly as in step ③a

#### Scenario: Scheduled scanner dispatches a due deferred notification

- **WHEN** a `DEFERRED` notification's `deferred_until` has passed
- **THEN** the scheduled scanner dispatches it (transitioning through `SENDING` to `SENT`/`FAILED`),
  and a not-yet-due deferred notification is left untouched

#### Scenario: Cross-midnight quiet window

- **WHEN** the quiet window is `22:00`–`07:00` and the current Asia/Seoul local time is `23:30` or
  `02:00`
- **THEN** the time is treated as within quiet hours and `deferred_until` resolves to the upcoming
  `07:00` Asia/Seoul instant

### Requirement: SMS delivery via Solapi adapter

The backend SHALL deliver `SMS` channel notifications via a Solapi adapter, sending to the
recipient's `users.phone` (not a `push_subscriptions` row). This replaces the prior gap where
`SMS`/`EMAIL` notifications were recorded but never dispatched. Delivery goes through an `SmsPort`
abstraction so the dispatcher does not depend directly on Solapi: `NotificationService.dispatch`
SHALL, for `channel = SMS`, read the recipient's phone, set `status = SENDING`, call the SMS port
with the phone and the notification body, and on success set `status = SENT` and `sent_at`; a send
failure or a missing recipient phone SHALL set `status = FAILED` with a non-null `fail_reason` (and
no SMS is sent to an empty number). The Solapi credentials (`solapi.api-key`, `solapi.api-secret`,
`solapi.sender`) MUST be supplied by configuration and MUST fail fast on a blank value rather than be
silently used.

The kindergarten staff immediate alert SHALL use this SMS path in addition to Pushover: for each
applicable staff recipient, the alert is delivered over Pushover (when the recipient has a
`push_subscriptions` row) and over SMS (when the recipient has a `users.phone`); a staff member with
both receives both, with distinct dedupe keys per channel.

#### Scenario: SMS notification dispatched to the recipient's phone

- **WHEN** a notification with `channel = SMS` is dispatched for a recipient whose `users.phone` is set
- **THEN** the backend calls the SMS port with that phone and the notification body, and on success
  sets `status = SENT` and `sent_at`

#### Scenario: SMS dispatch with no recipient phone

- **WHEN** a notification with `channel = SMS` is dispatched for a recipient whose `users.phone` is null/blank
- **THEN** no SMS is sent and the notification is recorded `FAILED` with a non-null `fail_reason`

#### Scenario: SMS send failure is recorded FAILED

- **WHEN** the Solapi adapter raises a delivery failure
- **THEN** the notification is recorded `FAILED` with a non-null `fail_reason`, and other
  recipients' notifications are unaffected

#### Scenario: Blank Solapi credentials fail fast

- **WHEN** the application starts with a blank `solapi.api-key`, `solapi.api-secret`, or `solapi.sender`
- **THEN** startup fails fast rather than silently attempting to send with a blank credential

#### Scenario: Staff alert delivered over both Pushover and SMS

- **WHEN** the backend dispatches the immediate staff alert for an ingested detection event, and an
  applicable staff member has both an active Pushover subscription and a `users.phone`
- **THEN** that staff member receives both a `PUSH` and an `SMS` notification (distinct dedupe keys);
  a staff member with only one channel available receives only that one

### Requirement: Review-confirmation guardian notification is verified end-to-end

The system SHALL have integration test coverage proving that an authenticated review confirmation submitted over HTTP results in guardian notification rows reaching their correct terminal state through the asynchronous `AFTER_COMMIT` transactional event path — exercising the real `@Async @TransactionalEventListener` boundary rather than calling the dispatch service directly.

#### Scenario: ESCALATED confirmation delivers immediately

- **WHEN** an authorized KINDERGARTEN_ADMIN confirms a review with `resultStatus = ESCALATED` for a classroom detection event via `POST /api/v1/event_reviews`
- **THEN** a notification row for each resolved guardian SHALL eventually reach status `SENT`, observed by polling the notifications table after the asynchronous listener completes

#### Scenario: RESOLVED confirmation during quiet hours is deferred

- **WHEN** a review is confirmed with `resultStatus = RESOLVED` while the kindergarten is within its configured quiet-hours window
- **THEN** the guardian notification row SHALL be persisted with status `DEFERRED` and immediate dispatch SHALL be skipped

#### Scenario: Public-space event resolves recipients from affectedChildIds

- **WHEN** a review is confirmed for a public-space detection event (no active classroom assignment) with an explicit `affectedChildIds` list
- **THEN** guardians SHALL be resolved from `affectedChildIds` rather than the room→class→child graph, and the resulting notification rows SHALL be persisted

