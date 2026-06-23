## ADDED Requirements

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
