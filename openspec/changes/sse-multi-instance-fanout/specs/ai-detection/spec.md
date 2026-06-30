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
all) remains out of scope for this change (tracked as follow-up). Cross-instance live fanout — pushing
an event ingested on one backend instance to clients connected to another instance — IS now in scope and
is specified by «Cross-instance SSE fanout via Redis pub/sub»; the per-instance emitter registry, push
path, and tenant scoping described here are unchanged, with the cross-instance delivery layered on top.

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

### Requirement: Cross-instance SSE fanout via Redis pub/sub

When the backend runs as more than one instance, a detection event ingested on any instance SHALL be
delivered over SSE to every connected staff client of that event's kindergarten, regardless of which
instance holds the client's open emitter. On a newly persisted (non-duplicate) detection event the
ingesting instance SHALL publish a minimal envelope — containing the event's `kindergartenId` and
`eventId` and no PII — to a shared Redis pub/sub channel. Every backend instance SHALL subscribe to
that channel; on receiving an envelope an instance SHALL re-read the tenant-scoped detection-event VO
and write it only to its own locally registered emitters for that envelope's `kindergartenId`, reusing
the same per-emitter push and dead-connection eviction as the in-process path. This relaxes the prior
single-instance assumption (the in-process `ConcurrentHashMap` emitter registry) while keeping that
registry per-instance; Redis pub/sub is the only cross-instance transport and no new message broker is
introduced. Fanout publish/receive SHALL NOT block the ingest response and SHALL be best-effort: a
Redis publish or delivery failure SHALL be handled without failing ingest, and clients recover any
missed event on reconnect via the existing `Last-Event-ID` replay from PostgreSQL.

#### Scenario: Event ingested on instance A reaches a client on instance B

- **WHEN** the backend runs two instances, a staff client's SSE stream is held by instance B, and a detection event for that client's kindergarten is ingested and persisted on instance A
- **THEN** instance A publishes the `{kindergartenId, eventId}` envelope to the shared Redis channel, instance B receives it, re-reads the tenant-scoped VO, and pushes the `detection-event` frame to that client — without the client reconnecting

#### Scenario: Cross-instance fanout preserves tenant isolation

- **WHEN** an envelope for kindergarten A is published and an instance holds open emitters for both kindergarten A and kindergarten B
- **THEN** that instance writes the event only to kindergarten A's emitters and never to any kindergarten B emitter, because the receiving instance routes solely by the envelope's `kindergartenId` and re-reads the VO scoped to that kindergarten

#### Scenario: Self-delivery on the ingesting instance is not duplicated

- **WHEN** a detection event is ingested on an instance that also holds open emitters for that kindergarten
- **THEN** the event is delivered to those local clients exactly once via the pub/sub receive path (the ingest path publishes rather than also fanning out locally), so no client on the ingesting instance receives the same event twice

#### Scenario: Redis unavailable degrades to reconnect replay

- **WHEN** the Redis publish or the cross-instance delivery fails because Redis is unavailable
- **THEN** the ingest response still succeeds, the failure is logged without leaking PII, and a client recovers the missed event on its next SSE reconnect via `Last-Event-ID` replay read from PostgreSQL

#### Scenario: Single-instance deployment behavior is unchanged

- **WHEN** the backend runs as exactly one instance
- **THEN** the ingesting instance publishes and the same instance receives and fans out to its local emitters, delivering each event once with the same wire contract (event name `detection-event`, `id:` = `event_id`, 25s heartbeat, 30min stream lifetime, replay cap) as before this change
