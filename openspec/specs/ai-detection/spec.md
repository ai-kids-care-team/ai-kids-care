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

