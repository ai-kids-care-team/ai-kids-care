## MODIFIED Requirements

### Requirement: Backend pushes detection events to the frontend on ingest

The backend SHALL push real-time detection-event updates to connected staff frontend clients over
**Server-Sent Events (SSE)** at ingest time, directly from the ingest path, without any PostgreSQL
`LISTEN/NOTIFY` mechanism (the backend is the sole writer of `detection_events`, so it knows of a new
event immediately). The SSE stream SHALL be tenant-scoped and staff-only: a client subscribes over its
authenticated session (cookie), and the backend SHALL push to a client only the detection events of
that client's own active kindergarten; non-staff (`GUARDIAN`) or unauthenticated subscribers SHALL be
rejected. Push SHALL occur only for a newly persisted (non-duplicate) event and SHALL NOT block the
ingest response.

To cover events ingested while a client was not connected, a client SHALL receive recent detection
history on connect: the frontend dashboard loads the most recent N events via the read API before (or
while) establishing the SSE stream, and de-duplicates by event id. A persistent "delivered-cursor"
catch-up scan that replays events missed while *all* clients were offline is out of scope for this
change (tracked as follow-up).

#### Scenario: Staff client receives a detection event at ingest

- **WHEN** the AI submits a detection event, the backend persists it as a new (non-duplicate) row, and a staff member of that event's kindergarten has an open SSE subscription
- **THEN** the backend pushes the event over SSE to that staff client within low latency, from the ingest path itself (no LISTEN/NOTIFY), without blocking the ingest response

#### Scenario: Client receives recent history on connect

- **WHEN** a staff client opens the dashboard after detection events were ingested while it was not connected
- **THEN** the client loads the most recent N detection events of its kindergarten via the read API and then receives subsequent events over the live SSE stream, de-duplicated by event id

#### Scenario: Non-staff or cross-tenant subscription is rejected

- **WHEN** an unauthenticated caller, a `GUARDIAN`, or a user with no active kindergarten attempts to open the detection-event SSE stream
- **THEN** the backend rejects the subscription (401/403) and pushes no events; an authenticated staff member only ever receives events of their own active kindergarten

## ADDED Requirements

### Requirement: Detection event read API for staff

The backend SHALL publish a tenant-scoped, staff-only read API for detection events, replacing the
prior `denyAll()` gap (the service query methods existed but were unreachable). `GET /api/v1/detection-events`
SHALL return the authenticated staff member's own kindergarten's detection events (most-recent-first,
paginated / bounded to a recent window for the dashboard), and `GET /api/v1/detection-events/{id}`
SHALL return a single event of that kindergarten. Both SHALL be restricted to `KINDERGARTEN_ADMIN` /
`TEACHER` and SHALL scope to the caller's active kindergarten via the effective authorization context
(the caller does not pass a kindergarten id); a non-staff caller or a cross-tenant id SHALL be denied
(403 / hidden 404). This read API is the dashboard's history/initial data source and the payload shape
SHALL match the event pushed over SSE.

#### Scenario: Staff lists detection events for own kindergarten

- **WHEN** an authenticated `KINDERGARTEN_ADMIN` or `TEACHER` calls `GET /api/v1/detection-events`
- **THEN** the backend returns that kindergarten's detection events (most-recent-first), scoped to the caller's active kindergarten without the caller supplying a kindergarten id

#### Scenario: Non-staff or cross-tenant read is denied

- **WHEN** a `GUARDIAN` or unauthenticated caller calls the detection-event read API, or a staff member requests an event id belonging to another kindergarten
- **THEN** the backend denies the read (403, or a hidden 404 for the foreign id) and returns no detection data
