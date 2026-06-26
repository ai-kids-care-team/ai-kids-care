## ADDED Requirements

### Requirement: Guardian notification on review confirmation

The backend SHALL notify the guardians of the affected children when a staff review confirmation
(`POST /api/v1/event_reviews`) sets a detection event's `result_status` to `ESCALATED`, or to
`RESOLVED` with guardian notification opted in. This implements the "reviewed → MAY notify GUARDIAN"
path of the guardian review-gate. Notification is dispatched over the `PUSH` channel via the existing
`NotificationService.dispatch` (Pushover). All notifications are dispatched immediately in this
capability slice; quiet-hours deferral is a separate later capability.

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
failure MUST NOT roll back the review. Each guardian notification uses
`dedupe_key = 'evt-{eventId}-u-{guardianUserId}-guardian'`, so repeated confirms of the same event do
not produce duplicate notifications (enforced by `UNIQUE(kindergarten_id, dedupe_key)`).

#### Scenario: ESCALATED confirm notifies guardians of the room's class children

- **WHEN** a staff member confirms a detection event with `result_status = ESCALATED`, and the
  event's room has an active `class_room_assignment` whose class has active children with active
  guardians
- **THEN** one `PUSH` `notifications` row is created and dispatched per distinct guardian `user_id`
  of those children, scoped to the event's kindergarten

#### Scenario: RESOLVED notifies only when opted in

- **WHEN** a confirm sets `result_status = RESOLVED` with `notifyGuardians = true`
- **THEN** guardians are notified; **and WHEN** `notifyGuardians` is absent or false, no guardian
  notification is created

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

#### Scenario: Notification failure does not roll back the review

- **WHEN** guardian notification dispatch fails (resolution error or Pushover failure)
- **THEN** the `event_reviews` row and the `detection_events.status` update remain committed; the
  confirm response is unaffected
