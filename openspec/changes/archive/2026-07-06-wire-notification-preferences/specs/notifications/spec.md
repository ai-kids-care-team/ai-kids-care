## ADDED Requirements

### Requirement: Self-service notification preferences

An authenticated user SHALL be able to read and update their own notification preferences — a
**master enable switch** and an optional **per-user quiet-hours window** — via a self-service API.
The preference is persisted as a single canonical `notification_rules` row per
`(kindergarten_id, user_id)` with `target_type = KINDERGARTEN` (reusing the V1-baseline table with
no schema migration); `quiet_hours_json` holds the user's window (`{"start":"HH:mm","end":"HH:mm"}`
or absent) and `enabled` holds the master switch. Every operation derives `user_id` and
`kindergarten_id` from the session identity (never from the request body or a kindergartenId
parameter), and queries are scoped by both — a caller can only read/modify their own preference, and
cross-user or cross-tenant access is hidden (no existence disclosure). The endpoint is gated by a
coarse `NOTIFICATION_PREFERENCE_MANAGE` action available to any authenticated user.

#### Scenario: Read preference with no stored row returns defaults

- **WHEN** a user with no canonical preference row requests their notification preference
- **THEN** the response is `200` with `enabled = true`, `quietHoursStart = null`, `quietHoursEnd = null` (not a 404)

#### Scenario: Upsert creates or updates the caller's canonical row

- **WHEN** a user submits an enabled flag and a valid `HH:mm` quiet-hours window
- **THEN** the caller's `(kindergarten_id, user_id, target_type = KINDERGARTEN)` row is created or
  updated with the assembled `quiet_hours_json` and `enabled`, scoped to the session identity

#### Scenario: Clearing quiet hours

- **WHEN** a user submits both `quietHoursStart` and `quietHoursEnd` as null
- **THEN** the stored `quiet_hours_json` is cleared (null) while `enabled` is still applied

#### Scenario: One-sided quiet-hours window is rejected

- **WHEN** a user submits exactly one of `quietHoursStart` / `quietHoursEnd`
- **THEN** the request is rejected with `400` and no row is written

#### Scenario: Cross-user access is hidden

- **WHEN** a user's request resolves against another user's preference row
- **THEN** the query is user-scoped so it never returns or mutates the other user's row

## MODIFIED Requirements

### Requirement: Quiet-hours deferral of guardian notifications

A guardian notification produced by a `RESOLVED` review confirmation SHALL be deferred when it falls
within the applicable quiet hours, instead of being dispatched immediately: the notification is
persisted with `status = DEFERRED` and `deferred_until` set to the end of the current quiet window,
and a scheduled scanner dispatches it once `deferred_until` has passed. The applicable window is
resolved **per recipient**: a guardian's own quiet-hours preference (their canonical
`notification_rules` row, when `enabled` and a window is set) overrides the kindergarten-wide window
(`kindergartens.notification_quiet_hours_json`); a guardian without a set preference falls back to
the kindergarten-level window. In addition, when a guardian's master switch is `enabled = false`,
their `RESOLVED` (non-critical) notifications are **suppressed entirely** (not sent). `ESCALATED`
notifications are never deferred and never suppressed — they **pierce** both the master switch and
quiet hours and dispatch immediately (the step-③a / child-safety behavior is unchanged). Windows are
`{"start":"HH:mm","end":"HH:mm"}` evaluated in `Asia/Seoul`, supporting cross-midnight windows; an
absent, empty, or unparseable configuration means never deferred.

#### Scenario: Per-guardian quiet window overrides kindergarten window

- **WHEN** a `RESOLVED` confirm would notify a guardian who has an enabled preference with a quiet
  window set, and the current time is within that per-user window
- **THEN** the notification is deferred to the per-user window's end, regardless of the
  kindergarten-level window

#### Scenario: Guardian without preference falls back to kindergarten window

- **WHEN** a `RESOLVED` confirm would notify a guardian who has no preference row (or an enabled
  preference with no quiet window set)
- **THEN** the kindergarten-wide quiet window applies exactly as before

#### Scenario: Master switch off suppresses RESOLVED notifications

- **WHEN** a `RESOLVED` confirm would notify a guardian whose preference has `enabled = false`
- **THEN** that guardian receives no notification for the event (neither immediate nor deferred)

#### Scenario: ESCALATED pierces master switch and quiet hours

- **WHEN** an `ESCALATED` confirm notifies a guardian who has `enabled = false` and/or is within a
  quiet window
- **THEN** the notification (PUSH, plus SMS when a phone is on file) is dispatched immediately,
  ignoring both the master switch and quiet hours

#### Scenario: RESOLVED notification outside quiet hours is immediate

- **WHEN** a `RESOLVED` confirm with guardian notification occurs outside any applicable quiet
  window (and the guardian is not suppressed)
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
