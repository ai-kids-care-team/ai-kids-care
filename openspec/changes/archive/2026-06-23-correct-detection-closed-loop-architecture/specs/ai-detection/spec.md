## MODIFIED Requirements

### Requirement: Detection closed-loop target architecture (ADR-0015 V1)

When implemented, the AI subsystem MUST submit detection results to the Java backend via its
internal REST channel — `POST /api/v1/internal/detection-events` authenticated with
`Authorization: Bearer <AI_SERVICE_TOKEN>` (`ROLE_AI_SERVICE`, ADR-0026). The **backend** SHALL be
the sole writer of `detection_sessions`, `detection_events`, and `event_evidence_files`; the AI
subsystem MUST NOT write to PostgreSQL directly and MUST NOT bypass the backend. The AI SHALL
generate the `dedup_key` (from camera + alarm-onset time) and include it in the ingest payload; the
backend SHALL enforce uniqueness so reconnects/debounces do not create duplicate events. Evidence
video MUST NOT enter PostgreSQL: the AI writes the video to the filesystem (scheme `file://`,
upgradeable to `s3://`) and includes `evidence_uri` + `evidence_hash` in the ingest payload, from
which the **backend** writes the `event_evidence_files` row. Parent-facing notifications MUST only be
sent by the backend after a human review record exists in `event_reviews`; the AI MUST NOT send
parent notifications. The Pushover/SMS demo code in the AI service MUST be replaced by this backend
ingest call.

#### Scenario: AI submits detection session on stream start

- **WHEN** a new live stream is consumed by the AI service (V1 implemented)
- **THEN** the AI calls the backend internal ingest endpoint and the backend inserts a row into `detection_sessions` with the correct `kindergarten_id` resolved from the stream/camera configuration

#### Scenario: AI submits detection event with AI-generated dedup key

- **WHEN** the persistence rule triggers `alarm_on` (V1 implemented)
- **THEN** the AI POSTs the event (mapped `event_type_enum`, confidence, AI-generated `dedup_key`, evidence_uri+hash, window stats) to the backend, which inserts a `detection_events` row and rejects a duplicate `dedup_key`

#### Scenario: Evidence stored by URI, backend writes the row

- **WHEN** the AI captures video evidence (V1 implemented)
- **THEN** the AI writes the video to the filesystem/object store and sends `evidence_uri`+`evidence_hash` in the ingest payload; the backend writes `event_evidence_files` — no video binary enters PostgreSQL

#### Scenario: Backend notifies parent only after review

- **WHEN** an `event_reviews` confirmation record is created by a staff member for a `detection_events` row
- **THEN** the backend sends a push notification to the parent via the notification subsystem; no parent notification is sent before review confirmation, and the AI never sends parent notifications

### Requirement: Detection closed-loop database schema readiness

The PostgreSQL schema defined in `db/initdb/01_create_schema.sql` MUST already contain all tables
required for the closed loop: `detection_sessions`, `detection_events`, `event_reviews`,
`event_evidence_files`, `notifications`, `notification_rules`, and `push_subscriptions`. These tables
MUST NOT be created as part of the ADR-0015 V1 implementation; what is missing is the **backend
ingest endpoint** (`POST /api/v1/internal/detection-events` + persistence) and the **AI-side ingest
client** (replacing the demo Pushover/SMS direct dispatch). No `LISTEN/NOTIFY` handler is required
(the backend is the writer).

#### Scenario: Schema tables exist at migration baseline

- **WHEN** the Flyway migration baseline `db/initdb/01_create_schema.sql` is applied to a fresh PostgreSQL instance
- **THEN** tables `detection_sessions`, `detection_events`, `event_reviews`, `event_evidence_files`, `notifications`, `notification_rules`, and `push_subscriptions` all exist

#### Scenario: Detection tables populated only by seed data in current state

- **WHEN** the system is running in the current interim state (AI-to-backend ingest not connected)
- **THEN** all rows visible in `detection_events` and `detection_sessions` via the backend API originate from seed data, not from live AI inference

## ADDED Requirements

### Requirement: Backend pushes detection events to the frontend on ingest

The backend SHALL push real-time detection-event updates to connected frontend clients (SSE or
WebSocket) at ingest time, directly within the ingest handler, without any PostgreSQL
`LISTEN/NOTIFY` mechanism (the backend is the sole writer of `detection_events`, so it knows of a new
event immediately). On startup the backend SHALL perform a catch-up scan for unprocessed detection
events to cover ingests that completed while no client was connected.

#### Scenario: Frontend receives a detection event at ingest

- **WHEN** the AI submits a detection event and the backend persists it (V1 implemented)
- **THEN** the backend pushes the event to the relevant connected frontend clients within low latency, from the ingest handler itself (no LISTEN/NOTIFY)

#### Scenario: Backend catch-up scan on restart

- **WHEN** the backend restarts after a period during which detection events were ingested
- **THEN** the backend scans for detection events not yet delivered to clients and pushes them to connected clients

## REMOVED Requirements

### Requirement: Backend real-time push via LISTEN/NOTIFY

**Reason**: The closed-loop architecture was corrected — the AI submits detections via the backend's
internal REST endpoint and the **backend is the sole DB writer**, so it knows about a new event
immediately and does not need `LISTEN/NOTIFY` to discover writes made by another process.

**Migration**: Replaced by "Backend pushes detection events to the frontend on ingest" — the backend
pushes to the frontend directly within the ingest handler (SSE/WS) plus a startup catch-up scan.
