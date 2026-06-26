## MODIFIED Requirements

### Requirement: Backend-owned notification dispatch

The backend SHALL be the sole initiator of notifications; the AI inference layer MUST NOT send notifications directly to recipients. All notification dispatch MUST go through the backend notification subsystem, which queries recipients from the database and resolves delivery channels via `notification_rules` and per-user push delivery identities stored in `push_subscriptions`.

#### Scenario: AI detection result triggers backend notification

- **WHEN** a detection event is persisted by the AI inference layer
- **THEN** the backend notification subsystem, not the AI layer, is responsible for determining recipients and dispatching any resulting notifications

#### Scenario: AI-side demo delivery code is absent

- **WHEN** the production system is running
- **THEN** no Pushover or SMS delivery code in `ai/` sends notifications; those files (e.g., `ai/src/ai_app/utils/pushover.py`, `ai/src/ai_app/utils/sms.py`) are demo stubs that MUST be removed before the closed-loop AI detection feature ships

---

### Requirement: Pushover as primary delivery channel

The backend SHALL deliver PUSH channel notifications via the Pushover third-party push service. The Pushover application credential (API token) MUST be supplied by configuration (`pushover.api-token`, sourced from the `PUSHOVER_API_TOKEN` environment variable) and MUST NOT be hard-coded; a blank credential MUST fail fast rather than be silently sent. Each recipient's Pushover delivery address (Pushover user key) SHALL be stored as an `address` row in `push_subscriptions` with `provider = PUSHOVER`; the FCM/APNS-shaped `device_tokens` table is superseded by `push_subscriptions` (see "Push delivery addressing model"). `NotificationChannelEnum` values are `PUSH`, `SMS`, and `EMAIL`; `PUSH` (Pushover) is the channel with an implemented backend delivery path (`PushoverService`), driving the delivery lifecycle status transitions.

#### Scenario: PUSH channel notification dispatched via Pushover

- **WHEN** a notification with `channel = PUSH` is dispatched for a recipient who has an `ACTIVE` `push_subscriptions` row with `provider = PUSHOVER`
- **THEN** the backend calls `PushoverService` with the configured API token and the recipient's stored Pushover user-key address plus the notification `title` and `body`, and on success sets `status = SENT` and `sent_at`

#### Scenario: PUSH dispatch with no active Pushover subscription

- **WHEN** a notification with `channel = PUSH` is dispatched for a recipient who has no `ACTIVE` `push_subscriptions` row with `provider = PUSHOVER`
- **THEN** no Pushover call is made and the notification is recorded as `FAILED` with a non-null `fail_reason` (no delivery to an empty/blank address is attempted)

#### Scenario: SMS channel — delivery not fully implemented

- **WHEN** a notification is created with `channel = SMS`
- **THEN** the system records the notification in the `notifications` table; however, automated SMS delivery is not yet implemented in the backend dispatch pipeline (gap: SMS delivery path pending rebuild per ADR-0018; the SMS address is the recipient's `users.phone`, not a `push_subscriptions` row)

#### Scenario: EMAIL channel — not implemented

- **WHEN** a notification is created with `channel = EMAIL`
- **THEN** the system records the notification but no email is dispatched; EMAIL is listed as a future option only (gap: no backend email delivery path exists)

## ADDED Requirements

### Requirement: Push delivery addressing model

Per-user PUSH delivery identities SHALL be stored in a provider-aware `push_subscriptions` table, not the superseded FCM/APNS-shaped `device_tokens` table. Each row SHALL carry `user_id`, `provider` (`push_provider_enum`, initially `PUSHOVER`), `address` (the provider delivery address — for Pushover, the user key), an optional `device_label`, a `status` (`status_enum`), and SHALL enforce uniqueness on `(user_id, provider, address)`. SMS and EMAIL delivery addresses SHALL NOT be stored here — those reuse `users.phone` and `users.email` respectively. The `address` column SHALL accommodate future providers (e.g., FCM/APNS device tokens) without a schema redesign.

#### Scenario: Recipient Pushover identity resolved for PUSH dispatch

- **WHEN** the backend dispatches a `PUSH` notification to a recipient
- **THEN** it resolves the recipient's `ACTIVE` `push_subscriptions` row with `provider = PUSHOVER` and uses its `address` as the Pushover user key

#### Scenario: Duplicate push subscription rejected

- **WHEN** a second `push_subscriptions` row is inserted with the same `(user_id, provider, address)`
- **THEN** the database uniqueness constraint rejects the insert

### Requirement: Push subscription management API not yet published

The push subscription management API SHALL be considered not yet published. The controller is registered (at `/api/v1/push_subscriptions`) but MUST NOT expose handler methods until the subscription management feature is implemented. Pushover user keys are stored in the `push_subscriptions` table, but there is no published API for clients to register, update, or delete their push subscriptions (gap: subscription management API is pending).

#### Scenario: Any request to push subscriptions endpoint

- **WHEN** a client sends any HTTP request to `/api/v1/push_subscriptions`
- **THEN** the server returns HTTP 405 (no handlers are registered)

## REMOVED Requirements

### Requirement: Device token API not yet published

**Reason**: The FCM/APNS-shaped `device_tokens` table and its `DeviceTokenController` are superseded by the provider-aware `push_subscriptions` model (Pushover uses per-user keys, not per-device platform tokens). Replaced by "Push subscription management API not yet published".

**Migration**: `device_tokens` (empty in production — no publish API ever existed to populate it) is dropped and replaced by `push_subscriptions` via Flyway V7; the unpublished management controller is renamed accordingly and continues to return 405.
