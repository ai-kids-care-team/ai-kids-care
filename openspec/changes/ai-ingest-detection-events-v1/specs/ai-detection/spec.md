## ADDED Requirements

### Requirement: Deployed live-detection worker and multi-camera supervisor

The AI subsystem SHALL run the detection→ingest loop as a long-lived process in the deployed
stack, alongside (not replacing) the FastAPI inference endpoint on port 8001. A **supervisor**
SHALL enumerate the active camera streams this AI deployment is responsible for and, for each
stream, run one detection **worker** (the existing single-stream live alert service) that consumes
the stream, applies the persistence-rule state machine, and submits sessions/events to the backend
internal ingest endpoints. The supervisor SHALL restart a worker that exits (with backoff) and
SHALL add/remove workers as the active-stream set changes, so that detection runs continuously
without manual per-stream startup. Each worker SHALL continue to obtain its stream URL via the
existing credential path (`STREAM_ID` → `GET /api/v1/internal/streams/{id}/credentials`) and SHALL
authenticate ingest with `Authorization: Bearer <AI_SERVICE_TOKEN>`; the AI SHALL NOT connect to
PostgreSQL directly and SHALL NOT bypass the backend. Tenant attribution remains server-side: the
worker sends only `streamId`/`modelId` and the backend derives `kindergarten_id`/`camera_id`. The
supervisor SHALL enforce a configured maximum concurrent worker count.

#### Scenario: Supervisor runs one worker per active stream

- **WHEN** the AI live-detection supervisor starts with N active streams in its responsibility set
- **THEN** it launches one detection worker per stream (up to the configured maximum), each of
  which creates a detection session and submits events on `alarm_on` through the backend internal
  ingest endpoints

#### Scenario: Crashed worker is restarted without stopping the others

- **WHEN** one stream's worker process exits or crashes
- **THEN** the supervisor restarts that worker after a backoff while the other streams' workers keep
  running, and no exception from one worker terminates the supervisor

#### Scenario: Active-stream set change adds and removes workers

- **WHEN** the set of active streams this deployment is responsible for changes (a camera is added
  or removed)
- **THEN** the supervisor starts a worker for each newly active stream and stops the worker for each
  removed stream, without restarting the unaffected workers

#### Scenario: Worker and inference endpoint coexist

- **WHEN** the AI deployment is running the live-detection supervisor
- **THEN** the FastAPI inference endpoint on port 8001 (`/health`, `/predict/upload`) remains
  available and is neither replaced nor blocked by the detection workers

#### Scenario: Deployed stack produces real detection events

- **WHEN** the supervisor and at least one worker are running against a live stream and a persistence
  alarm triggers
- **THEN** a `detection_events` row is written by the backend from live AI inference (not seed),
  closing the CCTV→AI→ingest path in the running stack

### Requirement: Detection event dedup key and time window reflect the real alarm window

On an `alarm_on` transition the AI SHALL capture the **wall-clock alarm-onset instant** at the moment
of the transition and SHALL derive the event `dedupKey` from `(streamId, alarm-onset second)` using
that captured onset — not the event-submission instant — so that a debounce or in-process re-trigger
of the same alarm yields the same `dedupKey` and the backend deduplicates on
`(kindergarten_id, dedup_key)`. The AI SHALL also send `startTime` = the alarm-onset wall-clock
instant and `endTime` = the alarm window's end (the current evaluation window's wall-clock instant),
rather than filling both with the submission `now`. The dedup-key string contract
(`"{streamId}-{epochSec}"`, second precision) is unchanged; only the time fed into it is corrected.

#### Scenario: Dedup key uses the captured alarm-onset instant

- **WHEN** the persistence rule transitions to `alarm_on` and the AI submits the detection event
- **THEN** the `dedupKey` is built from the wall-clock onset instant captured at the transition (not
  the submission time), so the same alarm re-submitted within its window produces the same `dedupKey`
  and the backend returns the existing event id idempotently

#### Scenario: Event carries the real alarm time window

- **WHEN** the AI submits a detection event for an `alarm_on` transition
- **THEN** `startTime` equals the captured alarm-onset instant and `endTime` equals the alarm
  window's end instant, so the event is not a zero-duration record at the submission time

## MODIFIED Requirements

### Requirement: Current alert output (interim state)

The real-time stream alert service SHALL output detections **only** to the backend via its internal
ingest endpoints (`POST /api/v1/internal/detection-sessions` then `POST /api/v1/internal/detection-events`,
Bearer `AI_SERVICE_TOKEN`); the backend is the sole writer of the detection tables and is responsible
for staff alerts and (post-review) guardian notifications. The service SHALL NOT send Pushover or SMS
notifications directly and SHALL NOT write local CSV files (`stream_timeline.csv`,
`stream_alarm_events.csv`); those interim demo outputs have been removed. The service SHALL NOT write
to PostgreSQL `detection_events`, `detection_sessions`, or any backend table directly — all writes go
through the backend ingest endpoints. (Historical note: the prior interim behavior of direct
Pushover/SMS dispatch plus local CSV, with no backend write, no longer applies; the loop is wired and,
per «Deployed live-detection worker and multi-camera supervisor», runs in the deployed stack.)

#### Scenario: Alarm is submitted to the backend, not Pushover/SMS

- **WHEN** the persistence rule transitions to `alarm_on` past cooldown
- **THEN** the service submits a detection event to `POST /api/v1/internal/detection-events` and makes
  no direct Pushover or SMS call

#### Scenario: No local CSV is written

- **WHEN** the stream alert service processes video windows and alarms
- **THEN** it writes no `stream_timeline.csv` and no `stream_alarm_events.csv`; per-window/per-alarm
  state is not persisted to local CSV

#### Scenario: AI does not write the database directly

- **WHEN** the stream alert service is running
- **THEN** it inserts no rows into `detection_sessions`, `detection_events`, or `event_evidence_files`
  directly; every such row is written by the backend from the ingest call
