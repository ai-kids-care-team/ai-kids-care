## ADDED Requirements

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
