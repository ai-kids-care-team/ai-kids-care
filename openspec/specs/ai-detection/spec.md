# ai-detection Specification

## Purpose
定义 AI 检测能力：解耦的 VideoMAE 推理服务（FastAPI:8001）、实时流告警状态机（持久化规则 + 黑屏门 + 冷却）、事件类型标签映射，及 ADR-0015 检测闭环目标态与当前 interim（Pushover/SMS/CSV、无 DB 写入）。
## Requirements
### Requirement: VideoMAE-based inference service

The AI inference service SHALL run as an independently deployed FastAPI process (port 8001) backed by a VideoMAE model fine-tuned on AI Hub dataset `이상행동 CCTV 영상` (dataSetSn=171, 12 abnormal-behavior classes), base checkpoint `MCG-NJU/videomae-base-finetuned-kinetics`. The service MUST load the model from `outputs/videomae_baseline/best_model` (or `AI_MODEL_DIR`) at startup via `@lru_cache` singleton and expose it through three HTTP endpoints.

#### Scenario: Health check returns model metadata

- **WHEN** a client sends `GET /health`
- **THEN** the service returns HTTP 200 with a `HealthResponse` body containing `status`, `model_dir`, `device`, `num_frames`, `sampling_rate`, and `labels`

#### Scenario: Path-based video prediction

- **WHEN** a client sends `POST /predict/path` with a JSON body containing `video_path`, optional `top_k`, optional `num_frames`, optional `sampling_rate`
- **THEN** the service returns a `PredictResponse` containing `predicted_id`, `predicted_label`, `confidence`, `scores` (top-k label/probability pairs), `model_dir`, `device`, and `video_path`

#### Scenario: Upload-based video prediction

- **WHEN** a client sends `POST /predict/upload` as `multipart/form-data` with field `file` (video binary) and optional `top_k`, `num_frames`, `sampling_rate`
- **THEN** the service writes a temporary file, runs inference, deletes the temporary file, and returns the same `PredictResponse` structure

#### Scenario: Invalid input returns 400

- **WHEN** a client sends a prediction request with a non-existent path or a file that cannot be decoded
- **THEN** the service returns HTTP 400

### Requirement: Model loading and device selection

The inference service MUST auto-select the compute device: CUDA when available, otherwise CPU. The model directory MUST be resolved from environment variable `AI_MODEL_DIR` (default `outputs/videomae_baseline/best_model`). The parameters `AI_DEVICE`, `AI_NUM_FRAMES`, and `AI_SAMPLING_RATE` MUST override the defaults. The model MUST be warmed up in the FastAPI `lifespan` hook before accepting requests.

#### Scenario: CUDA device used when available

- **WHEN** the host has a CUDA-capable GPU and the service starts
- **THEN** `VideoPredictor` selects `cuda` as its device and `GET /health` reports `device: "cuda"`

#### Scenario: CPU fallback when no GPU

- **WHEN** the host has no CUDA GPU
- **THEN** `VideoPredictor` selects `cpu` and `GET /health` reports `device: "cpu"`

#### Scenario: Custom model directory via env var

- **WHEN** `AI_MODEL_DIR` is set to a non-default path at startup
- **THEN** the service loads the model from that path and `GET /health` returns the custom path in `model_dir`

### Requirement: VideoMAE inference pipeline

The `VideoPredictor` MUST decode video using PyAV (`av`), sample `num_frames` frames (default 16) at `sampling_rate` (default 4), preprocess with `VideoMAEImageProcessor`, run the model forward pass, apply softmax, and return the top-k class probabilities.

#### Scenario: Frame sampling and preprocessing

- **WHEN** `VideoPredictor.predict()` is called with a valid video file
- **THEN** exactly `num_frames` frames are sampled at the configured `sampling_rate`, passed through `VideoMAEImageProcessor`, and fed to the model

#### Scenario: Top-k scores returned

- **WHEN** inference completes on a video
- **THEN** the response `scores` list contains exactly `top_k` entries, each with a `label` string and `probability` float, ordered by descending probability

### Requirement: Event type label mapping

The AI service MUST map VideoMAE output labels to `event_type_enum` values according to the following table derived from the AI Hub dataset (dataSetSn=171). `OTHER` MUST be used as the catch-all for any label not in the mapping.

| AI model output label | `event_type_enum` value |
|---|---|
| assault | `ASSAULT` |
| fight | `FIGHT` |
| burglary | `BURGLARY` |
| vandalism | `VANDALISM` |
| swoon | `SWOON` |
| wander | `WANDER` |
| trespass | `TRESPASS` |
| dump | `DUMP` |
| robbery | `ROBBERY` |
| datefight | `DATEFIGHT` |
| kidnap | `KIDNAP` |
| drunken | `DRUNKEN` |
| (no match) | `OTHER` |

The mapping MUST be centralised in a single module/function so that Phase 2 migration to the Java backend is a single controlled move.

#### Scenario: Known label maps to enum value

- **WHEN** the model outputs a label present in the mapping table (e.g., `assault`)
- **THEN** the detection-sink module maps it to the corresponding `event_type_enum` value (`ASSAULT`)

#### Scenario: Unknown label falls back to OTHER

- **WHEN** the model outputs a label not present in the mapping table
- **THEN** the detection-sink module maps it to `event_type_enum` value `OTHER`

### Requirement: Real-time stream alert service

The `stream_live_alert_service.py` script MUST consume a single FLV/RTSP stream URL via PyAV, apply a 5-second sliding window (step 2 seconds), gate out invalid windows via black-screen detection (mean brightness / standard deviation below threshold), run VideoMAE inference per window, and apply a persistence rule state machine before triggering an alert.

#### Scenario: Persistence rule triggers alarm

- **WHEN** within a 60-second sliding window the `target_label` (`assault`) probability meets `clip_positive_threshold` (0.60) in at least 8 clips AND the hit ratio is >= 0.50 AND the history span is >= 30 seconds
- **THEN** the state machine transitions to `alarm_on` and dispatches an alert

#### Scenario: Alarm clears on low hit ratio

- **WHEN** the hit ratio within the 60-second window drops to <= `clear_hit_ratio` (0.40) or the window expires
- **THEN** the state machine transitions to `alarm_off`

#### Scenario: Notification cooldown prevents alert flood

- **WHEN** an alert was dispatched within the last `notification_cooldown_sec` (120 seconds)
- **THEN** no additional alert is dispatched even if the persistence rule re-triggers

#### Scenario: Black-screen window skipped

- **WHEN** a video window has mean brightness and standard deviation below the black-screen gate thresholds
- **THEN** the window is marked invalid and skipped without running VideoMAE inference

#### Scenario: Stream reconnect on disconnect

- **WHEN** the live stream URL becomes unavailable or the connection drops
- **THEN** the script waits `reconnect_wait_sec` and retries the connection without exiting

### Requirement: Current alert output (interim state)

In the current as-built state, the real-time stream alert service MUST output alerts only to Pushover push notifications and optional SMS batch notifications plus local CSV files (`stream_timeline.csv` per-window, `stream_alarm_events.csv` per-alarm). The service MUST NOT write to PostgreSQL `detection_events`, `detection_sessions`, or any backend table in this interim state. This constraint is explicitly acknowledged as a temporary demo state pending ADR-0015 implementation.

#### Scenario: Alert dispatched to Pushover on alarm

- **WHEN** the persistence rule transitions to `alarm_on`
- **THEN** a Pushover push notification is sent (subject to cooldown)

#### Scenario: Local CSV records every window

- **WHEN** the stream alert service processes a video window
- **THEN** an entry is appended to `stream_timeline.csv` with per-window data

#### Scenario: No detection data written to database

- **WHEN** the stream alert service is running
- **THEN** no rows are inserted into `detection_sessions`, `detection_events`, or `event_evidence_files`; the detection tables contain only seed data

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

### Requirement: Independent deployment of AI subsystem

The AI subsystem MUST be independently deployable using its own `ai/docker-compose.yml` with service name `ai-inference` exposing port 8001. The AI service MUST NOT be included in the root `docker-compose.yml`. Model weights MUST be provided via a read-only Docker volume mount at `./outputs:/app/outputs:ro` rather than baked into the image.

#### Scenario: AI service starts independently

- **WHEN** `docker compose -f ai/docker-compose.yml up` is run with model weights present at `./outputs/videomae_baseline/best_model`
- **THEN** the `ai-inference` container starts, loads the model at `lifespan`, and `GET http://localhost:8001/health` returns HTTP 200

#### Scenario: Root compose does not start AI service

- **WHEN** `docker compose up` is run from the repository root (without the ai compose file)
- **THEN** no AI inference container is started

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

### Requirement: Detection ingest REST endpoints (V1)

The backend SHALL expose two internal ingest endpoints under `/api/v1/internal/**`, authenticated as
`ROLE_AI_SERVICE` (Bearer `AI_SERVICE_TOKEN`): `POST /api/v1/internal/detection-sessions` (AI creates a
session on stream start, returns its `session_id`) and `POST /api/v1/internal/detection-events` (AI
submits an event on `alarm_on`, referencing an existing `session_id`). The backend SHALL be the sole
writer of `detection_sessions`, `detection_events`, and `event_evidence_files` rows. The event request
SHALL carry the AI-generated `dedup_key`; the backend SHALL treat a duplicate
`(kindergarten_id, dedup_key)` idempotently — returning the existing event rather than creating a
duplicate. An event request whose `session_id` does not exist SHALL be rejected. `event_type` SHALL be
an `event_type_enum` value (the AI sends the already-mapped enum; an unknown value is rejected with
`400`).

#### Scenario: AI-authenticated session and event ingest

- **WHEN** the AI calls `POST /api/v1/internal/detection-sessions` then `POST /api/v1/internal/detection-events` with a valid `AI_SERVICE_TOKEN`
- **THEN** the backend persists a `detection_sessions` row and a `detection_events` row referencing it, and returns the generated `session_id` / `event_id`

#### Scenario: Duplicate dedup_key is idempotent

- **WHEN** the AI submits a detection event whose `(kindergarten_id, dedup_key)` already exists (e.g. a reconnect/debounce retry)
- **THEN** the backend does not create a duplicate event and returns the existing event's `event_id`

#### Scenario: Unauthenticated ingest is rejected

- **WHEN** a request to either ingest endpoint omits or presents an invalid `AI_SERVICE_TOKEN`
- **THEN** the backend rejects it (401/403) and writes no detection rows

### Requirement: Event review confirmation workflow

The backend SHALL publish a staff workflow to review a detection event: `POST /api/v1/event_reviews`
(confirm) and `GET /api/v1/event_reviews` / `GET /api/v1/event_reviews/{id}` (read review history).
A confirm request SHALL write an append-only `event_reviews` row (event, kindergarten, reviewer =
the authenticated user, `from_status` = the event's current status, `result_status`, optional
comment) and SHALL update `detection_events.status` to `result_status`. `result_status` SHALL be one
of `ACKNOWLEDGED`, `IN_REVIEW`, `RESOLVED`, `DISMISSED`, `ESCALATED` (not `OPEN`); any other value is
rejected with `400`. The workflow is restricted to `KINDERGARTEN_ADMIN` and `TEACHER` with a valid
tenant identity (`EVENT_REVIEW_WRITE` / `EVENT_REVIEW_READ`), and every operation is tenant-scoped:
a detection event or review outside the caller's active kindergarten is hidden as `404`. The confirm
operation itself does NOT send any notification (guardian notification is a separate review-gated
step).

#### Scenario: Staff confirms a detection event review

- **WHEN** a `KINDERGARTEN_ADMIN` or `TEACHER` POSTs `/api/v1/event_reviews` with an `eventId` in their kindergarten, a valid `result_status` (e.g. `RESOLVED`), and an optional comment
- **THEN** the backend appends an `event_reviews` row (reviewer = the caller, `from_status` = the event's prior status) and updates `detection_events.status` to `result_status`

#### Scenario: Cross-tenant event is hidden

- **WHEN** a staff member confirms or reads a review for a detection event that belongs to a different kindergarten
- **THEN** the backend returns `404` and does not write a review or update any status

#### Scenario: Invalid result status is rejected

- **WHEN** a confirm request sets `result_status` to `OPEN` or a non-enum value
- **THEN** the backend returns `400` and writes no review

#### Scenario: Wrong role is rejected

- **WHEN** a `GUARDIAN` (or any non KINDERGARTEN_ADMIN/TEACHER role) attempts to confirm or read event reviews
- **THEN** the backend returns `403`

#### Scenario: Review history is readable per event (tenant-scoped)

- **WHEN** a `KINDERGARTEN_ADMIN`/`TEACHER` GETs `/api/v1/event_reviews?eventId={id}` for an event in their kindergarten
- **THEN** the response lists that event's review rows, scoped to the caller's kindergarten

### Requirement: AI-side detection ingest client

The AI detection service SHALL submit detection sessions and events to the backend internal ingest
endpoints over its REST channel, instead of sending Pushover/SMS notifications directly or writing
local CSV files. This implements the AI side of the ADR-0015 V1 closed loop (the backend side is
already in place). On stream connection the AI SHALL create a session via `POST
/api/v1/internal/detection-sessions` with `{streamId, modelId}` and retain the returned `sessionId`;
on a persistence-rule `alarm_on` transition (past cooldown) the AI SHALL submit an event via `POST
/api/v1/internal/detection-events` with `{sessionId, eventType, severity, confidence, startTime,
endTime, dedupKey}`. Both calls SHALL carry `Authorization: Bearer <AI_SERVICE_TOKEN>` and target the
backend at `JAVA_BACKEND_URL`. The AI SHALL map the model's predicted label to a backend
`event_type_enum` value (the VideoMAE label mapping table; any unmapped label maps to `OTHER`) and
SHALL generate the `dedupKey` from the stream/camera plus the alarm-onset time so that a reconnect or
debounce retry of the same alarm yields the same key (the backend deduplicates on `(kindergarten_id,
dedup_key)`). The Pushover/SMS direct-dispatch calls and the per-window/per-alarm CSV outputs SHALL
be removed from the live stream alert service. Ingest is best-effort: a backend failure SHALL be
logged and MUST NOT crash the stream service. Evidence (`evidence_uri`/`evidence_hash` and the
`event_evidence_files` write) is out of scope for this slice.

#### Scenario: Session created on stream connection

- **WHEN** the AI stream alert service connects to a live stream
- **THEN** it calls `POST /api/v1/internal/detection-sessions` with the configured `streamId` and
  `modelId` and a Bearer `AI_SERVICE_TOKEN`, and retains the returned `sessionId`

#### Scenario: Event submitted on alarm_on

- **WHEN** the persistence rule transitions to `alarm_on` past cooldown
- **THEN** the AI calls `POST /api/v1/internal/detection-events` with the retained `sessionId`, the
  mapped `eventType`, `severity`, `confidence`, `startTime`/`endTime`, and an AI-generated `dedupKey`

#### Scenario: Predicted label mapped to event_type, unknown maps to OTHER

- **WHEN** the AI maps a predicted label to a backend event type
- **THEN** a label present in the VideoMAE mapping table maps to its `event_type_enum` value, and a
  label not in the table maps to `OTHER`

#### Scenario: Same alarm yields the same dedup key

- **WHEN** the same alarm is re-triggered or the stream reconnects within the alarm window
- **THEN** the AI generates the same `dedupKey` (from stream/camera + alarm-onset time), so the
  backend deduplicates and does not create a duplicate `detection_events` row

#### Scenario: Demo dispatch and CSV removed

- **WHEN** the live stream alert service runs after this change
- **THEN** it makes no direct Pushover or SMS call and writes no `stream_timeline.csv` /
  `stream_alarm_events.csv`; detection results flow only through the backend ingest endpoints

#### Scenario: Backend unreachable does not crash the stream service

- **WHEN** a session or event ingest call fails (backend unreachable or non-2xx)
- **THEN** the failure is logged and the stream alert service continues processing subsequent windows

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

### Requirement: Detection SSE connection keepalive

The realtime detection event stream SHALL emit periodic keepalive frames to every registered SSE connection so that idle connections stay alive across intermediate proxy/NAT timeouts and dead connections are detected and evicted promptly, rather than persisting until the full stream timeout elapses.

#### Scenario: Periodic heartbeat sent to live connections

- **WHEN** the configured heartbeat interval elapses
- **THEN** the service SHALL send a keepalive frame to every currently registered emitter

#### Scenario: Failed heartbeat evicts a dead connection

- **WHEN** sending a heartbeat frame to an emitter throws
- **THEN** that emitter SHALL be removed from the registry immediately, without waiting for the stream timeout

#### Scenario: Client data stream is unaffected by heartbeats

- **WHEN** keepalive frames are emitted as SSE comment frames
- **THEN** the browser `EventSource` SHALL NOT surface them as data events to application code

### Requirement: Staff can confirm a detection review from the realtime dashboard

Staff with review authority SHALL be able to confirm a detection event review directly from the realtime dashboard, reusing the existing event review workflow API, with the dashboard reflecting the resulting status immediately rather than waiting for a new ingest push.

#### Scenario: Authorized staff confirms a review inline

- **WHEN** a KINDERGARTEN_ADMIN or TEACHER selects a result status on a dashboard event card
- **THEN** the dashboard SHALL submit to the existing `POST /api/v1/event_reviews` endpoint and optimistically update that card's status to the chosen result status

#### Scenario: Review actions hidden for unauthorized roles

- **WHEN** the current user's role is neither KINDERGARTEN_ADMIN nor TEACHER
- **THEN** inline review actions SHALL NOT be rendered on dashboard cards

#### Scenario: Failed confirmation rolls back the optimistic update

- **WHEN** the review submission fails
- **THEN** the card SHALL revert to its prior status and an error SHALL be surfaced to the user

### Requirement: Backend accepts detection evidence on ingest and writes event_evidence_files

The backend SHALL accept an **optional** evidence descriptor on the detection-event ingest request
(`POST /api/v1/internal/detection-events`) and, when present, SHALL write exactly one
`event_evidence_files` row for the newly persisted detection event. The evidence descriptor SHALL
carry `uri` (stored as `storage_uri`), `hash` (stored as `hash`), `type` (an
`evidence_file_type_enum` value: `IMAGE`/`VIDEO`) and `mimeType` (a `mime_type_enum` value); the
written row SHALL set `hold = false` and leave `retention_until` null. The evidence descriptor is
all-or-nothing: when supplied, all of `uri`/`hash`/`type`/`mimeType` SHALL be present, otherwise the
request is rejected with `400`; an unknown `type`/`mimeType` enum value SHALL also be rejected with
`400`. Because the AI does not yet send evidence, the field SHALL be optional and a request without it
SHALL persist the detection event exactly as before with no evidence row. Evidence SHALL be written
only on the fresh-event path: when ingest is an idempotent duplicate (`(kindergarten_id, dedup_key)`
already exists), no second `event_evidence_files` row SHALL be written. The backend does not verify
that the file at `uri` exists or that `hash` matches its contents (the caller is the trusted
`ROLE_AI_SERVICE`); an evidence-write failure SHALL NOT roll back or fail the already-persisted
detection event.

#### Scenario: Ingest with evidence writes an event_evidence_files row

- **WHEN** the AI submits a new (non-duplicate) detection event whose request includes an evidence descriptor `{uri, hash, type: VIDEO, mimeType: video/mp4}`
- **THEN** the backend persists the detection event and writes exactly one `event_evidence_files` row for that `event_id` with `storage_uri = uri`, `hash = hash`, `type = VIDEO`, `mime_type = video/mp4`, `hold = false`, and `retention_until` null

#### Scenario: Ingest without evidence persists the event with no evidence row

- **WHEN** the AI submits a detection event whose request has no evidence descriptor
- **THEN** the backend persists the detection event normally and writes no `event_evidence_files` row

#### Scenario: Duplicate ingest does not write a second evidence row

- **WHEN** a detection event whose `(kindergarten_id, dedup_key)` already exists is re-submitted with an evidence descriptor
- **THEN** the backend returns the existing `event_id` idempotently and writes no additional `event_evidence_files` row

#### Scenario: Partial or unknown-enum evidence is rejected

- **WHEN** an ingest request supplies an evidence descriptor that is missing one of `uri`/`hash`/`type`/`mimeType`, or carries a `type`/`mimeType` value outside the database enum
- **THEN** the backend rejects the request with `400` and writes neither a detection event nor an evidence row

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

