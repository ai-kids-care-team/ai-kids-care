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
while) establishing the SSE stream, and de-duplicates by event id. In addition, on an SSE **reconnect**
the backend SHALL replay the events the client missed during the disconnect, keyed on the client's
`Last-Event-ID` (see «Detection SSE reconnect replay via Last-Event-ID»). A server-persisted
per-subscriber delivered-cursor (durable across a full backend restart with no client connected at
all) and cross-instance live fanout remain out of scope for this change (tracked as follow-up).

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

### Requirement: Detection SSE reconnect replay via Last-Event-ID

The detection-event SSE stream SHALL set each pushed frame's SSE `id:` to the event's `event_id`, and
on a new stream connection that presents a `Last-Event-ID` request header SHALL replay to that
connection the detection events of the client's active kindergarten whose `event_id` is greater than
the presented `Last-Event-ID`, in ascending `event_id` order, as normal `detection-event` frames
(same `id:` and payload as a live push), before live pushes resume on that connection. The replay
SHALL be tenant-scoped — filtered by the connecting client's active kindergarten; the `Last-Event-ID`
is only a numeric lower bound and SHALL NOT widen tenant scope — and bounded by a configured maximum:
when the missed range exceeds the bound, the backend SHALL replay only the most recent `max` missed
events (older history is covered by the read-API load). A missing or non-numeric `Last-Event-ID`
SHALL be treated as no replay, and the connection SHALL behave exactly as the pre-existing connect.
No database schema and no server-persisted cursor are introduced: the `event_id` itself is the cursor
and the client's `EventSource` supplies `Last-Event-ID` automatically across reconnects.

#### Scenario: Reconnect replays missed events by Last-Event-ID

- **WHEN** a staff client reconnects to the SSE stream presenting `Last-Event-ID: {n}` and its active kindergarten has detection events with `event_id > n`
- **THEN** the backend replays those events to that connection in ascending `event_id` order as `detection-event` frames whose `id:` equals `event_id`, before resuming live pushes

#### Scenario: Replay is tenant-scoped

- **WHEN** a client reconnects with any `Last-Event-ID` value
- **THEN** only detection events of that client's own active kindergarten are replayed; an event of another kindergarten is never replayed regardless of the numeric `Last-Event-ID`

#### Scenario: Replay is bounded by the configured maximum

- **WHEN** the number of missed events (`event_id` greater than `Last-Event-ID`) exceeds the configured maximum
- **THEN** the backend replays at most the configured maximum most-recent missed events, relying on the read-API history load for anything older

#### Scenario: Missing or non-numeric Last-Event-ID replays nothing

- **WHEN** a client connects with no `Last-Event-ID` header or a non-numeric value
- **THEN** no replay occurs and the connection behaves exactly as the pre-existing connect (recent-history via the read API plus the live stream)

#### Scenario: Replayed and history-loaded events de-duplicate by id

- **WHEN** a reconnecting client both loads recent history via the read API and receives replayed SSE frames for overlapping events
- **THEN** the client de-duplicates by event id (the SSE `id:` equals the read-API `event_id`), so no event is rendered twice
