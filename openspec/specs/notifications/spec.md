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

### Requirement: Kindergarten staff immediate alert on high-confidence detection

The notification subsystem SHALL allow immediate (pre-review) alert dispatch to kindergarten staff (KINDERGARTEN_ADMIN, TEACHER roles) when a detection event meets a high-confidence threshold. Exact confidence thresholds MUST be defined before this path is enabled in production (gap: thresholds not yet specified).

#### Scenario: High-confidence detection alert to staff

- **WHEN** a detection event's confidence score meets the configured staff-alert threshold
- **THEN** the notification subsystem MAY dispatch an immediate notification to applicable KINDERGARTEN_ADMIN or TEACHER recipients without waiting for `event_reviews` confirmation

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

### Requirement: Push subscription management API not yet published

The push subscription management API SHALL be considered not yet published. The controller is registered (at `/api/v1/push_subscriptions`) but MUST NOT expose handler methods until the subscription management feature is implemented. Pushover user keys are stored in the `push_subscriptions` table, but there is no published API for clients to register, update, or delete their push subscriptions (gap: subscription management API is pending).

#### Scenario: Any request to push subscriptions endpoint

- **WHEN** a client sends any HTTP request to `/api/v1/push_subscriptions`
- **THEN** the server returns HTTP 405 (no handlers are registered)

