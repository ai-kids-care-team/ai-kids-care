## ADDED Requirements

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

## REMOVED Requirements

### Requirement: Push subscription management API not yet published

**Reason**: The push subscription management API is now published as a self-service API (see "Push subscription self-service management API"), closing the gap that left the PUSH delivery primitive with no way to obtain recipient addresses.

**Migration**: `PushSubscriptionController` now exposes POST/GET/PUT/DELETE handlers scoped to the authenticated caller; `/api/v1/push_subscriptions` no longer returns 405 for those methods.
