## MODIFIED Requirements

### Requirement: Tenant-scoped notification read API

The backend SHALL expose `GET /api/v1/notifications` and `GET /api/v1/notifications/{id}` for authenticated users. GUARDIAN and TEACHER recipients SHALL only receive their own notifications. KINDERGARTEN_ADMIN SHALL receive all notifications within their kindergarten. Cross-tenant access and access to another user's notifications MUST be denied. The published response shape is `NotificationReadVO` containing fields: `notificationId`, `title`, `body`, `status`, `readAt`, `createdAt` (`readAt` is `null` when the recipient has not read the notification). Internal fields (`channel`, `dedupeKey`, `sentAt`, `failReason`, `retryCount`, `recipientUserId`, `kindergartenId`) MUST NOT appear in read responses.

The backend SHALL additionally expose `PATCH /api/v1/notifications/{id}/read` (a CSRF-protected write) and `GET /api/v1/notifications/unread-count`. `PATCH /{id}/read` SHALL set `read_at` for a notification only when its `recipient_user_id` matches the caller; a notification belonging to another user or another kindergarten MUST be hidden as HTTP 404. `GET /unread-count` SHALL return the count of the caller's own notifications (`recipient_user_id` = caller) with `read_at IS NULL`.

#### Scenario: GUARDIAN or TEACHER reads own notifications

- **WHEN** a GUARDIAN or TEACHER user calls `GET /api/v1/notifications`
- **THEN** the response contains only notifications where `recipient_user_id` matches the caller's user ID within the caller's active kindergarten, ordered by `created_at` descending, each including `readAt` (null when unread)

#### Scenario: KINDERGARTEN_ADMIN reads all kindergarten notifications

- **WHEN** a KINDERGARTEN_ADMIN calls `GET /api/v1/notifications`
- **THEN** the response contains all notifications scoped to the admin's active kindergarten, ordered by `created_at` descending

#### Scenario: Access to another user's notification is denied

- **WHEN** a GUARDIAN or TEACHER calls `GET /api/v1/notifications/{id}` for a notification that belongs to a different user or a different kindergarten
- **THEN** the system records an `AUTHORIZATION_DENIED` audit event and returns HTTP 404

#### Scenario: Recipient marks own notification read

- **WHEN** a recipient calls `PATCH /api/v1/notifications/{id}/read` for a notification whose `recipient_user_id` matches the caller
- **THEN** the notification's `read_at` is set to the current time (or left unchanged if already read — the call is idempotent) and the server returns HTTP 200

#### Scenario: Marking another user's notification read is hidden

- **WHEN** a caller sends `PATCH /api/v1/notifications/{id}/read` for a notification that belongs to a different user or kindergarten
- **THEN** the server returns HTTP 404 and does not modify any `read_at`

#### Scenario: Unread count is scoped to the caller's own notifications

- **WHEN** a user calls `GET /api/v1/notifications/unread-count`
- **THEN** the response is the count of notifications where `recipient_user_id` equals the caller and `read_at IS NULL`, within the caller's active kindergarten

#### Scenario: Non-read write operations not published on notification endpoint

- **WHEN** a client sends POST, PUT, or DELETE to `/api/v1/notifications`
- **THEN** the server returns HTTP 405 (only `GET` reads and the `PATCH /{id}/read` write are published)

## ADDED Requirements

### Requirement: Per-user notification read state drives the in-app unread indicator

Each notification row is per-recipient (`recipient_user_id`). The system SHALL track per-user read state via a `read_at` timestamp column (`NULL` = unread) that is orthogonal to the delivery `status` lifecycle. The in-app "unread" determination SHALL be based solely on `read_at IS NULL`, NOT on delivery status. Delivery failure (`status = FAILED`) SHALL be surfaced as a distinct "delivery failed" state and MUST NOT be presented as "unread". The primary navigation SHALL show an ambient unread indicator (badge/count) reflecting the signed-in user's own unread count, refreshed on session start. The delivery `status` enum (including its existing `READ` value) is unchanged and is NOT reused as the read-state source.

#### Scenario: Delivered-but-unopened notification counts as unread

- **WHEN** a notification addressed to the user has been delivered (`status` in a terminal-success state) but the user has never opened it (`read_at IS NULL`)
- **THEN** it is counted as unread and contributes to the navigation unread indicator

#### Scenario: Opening a notification clears its unread state

- **WHEN** the user opens/clicks a notification in the in-app inbox
- **THEN** the frontend calls `PATCH /{id}/read`, the notification's `read_at` becomes non-null, and the navigation unread count decreases accordingly

#### Scenario: Delivery failure is not shown as unread

- **WHEN** a notification has `status = FAILED`
- **THEN** the inbox presents it with a distinct "전송 실패 / delivery failed" treatment and it is not included in the unread indicator on the basis of its delivery status

#### Scenario: Ambient unread badge is present in navigation

- **WHEN** a signed-in recipient (GUARDIAN/TEACHER/KINDERGARTEN_ADMIN) is on any page
- **THEN** the primary navigation shows an unread indicator derived from `GET /unread-count`, without the user having to open the inbox
