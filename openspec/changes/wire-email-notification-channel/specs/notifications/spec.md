## ADDED Requirements

### Requirement: EMAIL delivery via SMTP adapter

The backend SHALL deliver `EMAIL` channel notifications via an SMTP adapter, sending to the
recipient's `users.email` (not a `push_subscriptions` row). This closes the prior gap where `EMAIL`
notifications were recorded but never dispatched. Delivery goes through an `EmailPort` abstraction so
the dispatcher does not depend directly on the mail provider (mirroring `SmsPort`/`PushPort`):
`NotificationService.dispatch` SHALL, for `channel = EMAIL`, read the recipient's email, set
`status = SENDING`, call the email port with the address, a subject, and the notification body, and
on success set `status = SENT` and `sent_at`; a send failure or a missing/blank recipient email SHALL
set `status = FAILED` with a non-null `fail_reason` (and no email is sent to an empty address). The
provider call SHALL run outside any database transaction and under a bounded wall-clock budget
(`notifications.email-send-timeout-ms`, default 5000); a timeout SHALL be recorded as `FAILED`, never
a stuck `SENDING`. The SMTP configuration (`spring.mail.host`, `spring.mail.port`,
`spring.mail.username`, `spring.mail.password`) MUST be supplied by configuration from environment
variables and MUST fail fast on a blank value rather than be silently used, consistent with the
Pushover and Solapi credential posture. SMTP credentials MUST NOT be logged.

#### Scenario: EMAIL notification dispatched to the recipient's email

- **WHEN** a notification with `channel = EMAIL` is dispatched for a recipient whose `users.email` is set
- **THEN** the backend calls the email port with that address, a subject, and the notification body, and on success sets `status = SENT` and `sent_at`

#### Scenario: EMAIL dispatch with no recipient email

- **WHEN** a notification with `channel = EMAIL` is dispatched for a recipient whose `users.email` is null/blank
- **THEN** no email is sent and the notification is recorded `FAILED` with a non-null `fail_reason`

#### Scenario: EMAIL send failure or timeout is recorded FAILED

- **WHEN** the SMTP adapter raises a delivery failure, or the provider call exceeds `notifications.email-send-timeout-ms`
- **THEN** the notification is recorded `FAILED` with a non-null `fail_reason`, never left in `SENDING`, and other recipients' notifications are unaffected

#### Scenario: Blank SMTP configuration fails fast

- **WHEN** the application starts with a blank `spring.mail.host`, `spring.mail.username`, or `spring.mail.password`
- **THEN** startup fails fast rather than silently attempting to send with a blank credential

## MODIFIED Requirements

### Requirement: Pushover as primary delivery channel

The backend SHALL deliver PUSH channel notifications via the Pushover third-party push service. The Pushover application credential (API token) MUST be supplied by configuration (`pushover.api-token`, sourced from the `PUSHOVER_API_TOKEN` environment variable) and MUST NOT be hard-coded; a blank credential MUST fail fast rather than be silently sent. Each recipient's Pushover delivery address (Pushover user key) SHALL be stored as an `address` row in `push_subscriptions` with `provider = PUSHOVER`; the FCM/APNS-shaped `device_tokens` table is superseded by `push_subscriptions` (see "Push delivery addressing model"). `NotificationChannelEnum` values are `PUSH`, `SMS`, and `EMAIL`; all three now have implemented backend delivery paths driving the delivery lifecycle status transitions — `PUSH` (Pushover, `PushoverService`), `SMS` (Solapi, `SolapiSmsAdapter` — see "SMS delivery via Solapi adapter"), and `EMAIL` (SMTP, `SmtpEmailAdapter` via `EmailPort` — see "EMAIL delivery via SMTP adapter").

#### Scenario: PUSH channel notification dispatched via Pushover

- **WHEN** a notification with `channel = PUSH` is dispatched for a recipient who has an `ACTIVE` `push_subscriptions` row with `provider = PUSHOVER`
- **THEN** the backend calls `PushoverService` with the configured API token and the recipient's stored Pushover user-key address plus the notification `title` and `body`, and on success sets `status = SENT` and `sent_at`

#### Scenario: PUSH dispatch with no active Pushover subscription

- **WHEN** a notification with `channel = PUSH` is dispatched for a recipient who has no `ACTIVE` `push_subscriptions` row with `provider = PUSHOVER`
- **THEN** no Pushover call is made and the notification is recorded as `FAILED` with a non-null `fail_reason` (no delivery to an empty/blank address is attempted)

#### Scenario: SMS channel — delivered via Solapi adapter

- **WHEN** a notification is created with `channel = SMS`
- **THEN** the system records it in the `notifications` table and dispatches it to the recipient's `users.phone` through the Solapi adapter (not a `push_subscriptions` row), per the "SMS delivery via Solapi adapter" requirement (SENDING → SENT/`sent_at` on success; FAILED + `fail_reason` on a missing phone or send failure)

#### Scenario: EMAIL channel — delivered via SMTP adapter

- **WHEN** a notification is created with `channel = EMAIL`
- **THEN** the system records it in the `notifications` table and dispatches it to the recipient's `users.email` through the SMTP `EmailPort` (not a `push_subscriptions` row), per the "EMAIL delivery via SMTP adapter" requirement (SENDING → SENT/`sent_at` on success; FAILED + `fail_reason` on a missing/blank email or send failure)

### Requirement: Guardian notification on review confirmation

The backend SHALL notify the guardians of the affected children when a staff review confirmation
(`POST /api/v1/event_reviews`) sets a detection event's `result_status` to `ESCALATED`, or to
`RESOLVED` with guardian notification opted in. This implements the "reviewed → MAY notify GUARDIAN"
path of the guardian review-gate. Notification is dispatched over the `PUSH` channel via the existing
`NotificationService.dispatch` (Pushover). For an `ESCALATED` confirmation the backend SHALL
**additionally** dispatch the notification over the `SMS` channel to each affected guardian whose
linked `users.phone` is non-blank (via Solapi `SmsPort`), **and over the `EMAIL` channel to each
affected guardian whose linked `users.email` is non-blank (via SMTP `EmailPort`)**, all through the
same `NotificationService.dispatch`; a `RESOLVED` confirmation notifies over `PUSH` only. The PUSH
dispatch is unchanged — SMS and EMAIL are additive, independent channels, and a guardian whose linked
`users.phone` or `users.email` is null/blank still receives the other channels they qualify for.
ESCALATED notifications (PUSH plus any additive SMS and EMAIL) are dispatched immediately and pierce
quiet hours (safety-critical); RESOLVED's per-recipient quiet-hours deferral is governed by a
separate requirement and is unaffected by the EMAIL channel (RESOLVED remains PUSH-only).

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
`dedupe_key = 'evt-{eventId}-u-{guardianUserId}-guardian'`, each guardian SMS notification uses
`dedupe_key = 'evt-{eventId}-u-{guardianUserId}-guardian-sms'`, and each guardian EMAIL notification
uses `dedupe_key = 'evt-{eventId}-u-{guardianUserId}-guardian-email'` (distinct keys so PUSH, SMS,
and EMAIL for the same guardian and event coexist), so repeated confirms of the same event do not
produce duplicate notifications (enforced by `UNIQUE(kindergarten_id, dedupe_key)`). SMS and EMAIL
dispatch is per-recipient and per-channel best-effort: a build or send failure for one guardian on
one channel MUST NOT affect that guardian's other channels or any other guardian's notifications.

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

#### Scenario: ESCALATED also sends EMAIL to guardians with an email

- **WHEN** an `ESCALATED` confirm notifies guardians and an affected guardian's linked `users.email`
  is non-blank
- **THEN** that guardian additionally receives an `EMAIL` `notifications` row (distinct dedupe key
  `-guardian-email`), the email sent to that `users.email`; **and WHEN** a notified guardian's linked
  `users.email` is null/blank, no `EMAIL` row is created (the guardian's PUSH and any SMS are
  unaffected)

#### Scenario: RESOLVED notifies only when opted in, over PUSH only

- **WHEN** a confirm sets `result_status = RESOLVED` with `notifyGuardians = true`
- **THEN** guardians are notified over `PUSH` only and no `SMS` or `EMAIL` notification is created;
  **and WHEN** `notifyGuardians` is absent or false, no guardian notification is created

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

#### Scenario: One guardian's EMAIL failure does not affect other channels or recipients

- **WHEN** the `EMAIL` send for one guardian fails (SMTP failure) or times out
- **THEN** that guardian's `EMAIL` notification is recorded `FAILED` with a non-null `fail_reason`,
  while that guardian's `PUSH` and any `SMS` notification and every other guardian's notifications
  remain unaffected
