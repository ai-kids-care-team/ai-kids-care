## MODIFIED Requirements

### Requirement: AI-side detection ingest client

The AI detection service SHALL submit detection sessions and events to the backend internal ingest
endpoints over its REST channel, instead of sending Pushover/SMS notifications directly or writing
local CSV files. This implements the AI side of the ADR-0015 V1 closed loop (the backend side is
already in place). On stream connection the AI SHALL create a session via `POST
/api/v1/internal/detection-sessions` with `{streamId, modelId}` and retain the returned `sessionId`;
on a persistence-rule `alarm_on` transition (past cooldown) the AI SHALL submit an event via `POST
/api/v1/internal/detection-events` with `{sessionId, eventType, severity, confidence, startTime,
endTime, dedupKey}` and, when a video evidence clip was captured for the alarm, an `evidence`
descriptor (see below). Both calls SHALL carry `Authorization: Bearer <AI_SERVICE_TOKEN>` and target
the backend at `JAVA_BACKEND_URL`. The AI SHALL map the model's predicted label to a backend
`event_type_enum` value (the VideoMAE label mapping table; any unmapped label maps to `OTHER`) and
SHALL generate the `dedupKey` from the stream/camera plus the alarm-onset time so that a reconnect or
debounce retry of the same alarm yields the same key (the backend deduplicates on `(kindergarten_id,
dedup_key)`). The Pushover/SMS direct-dispatch calls and the per-window/per-alarm CSV outputs SHALL
be removed from the live stream alert service.

The AI SHALL derive the event `severity` (1..5) from the alarm's confidence by a defined bucket rule
(monotonically non-decreasing in confidence, clamped to `[1, 5]`); the rule SHALL be a stable,
documented mapping rather than a placeholder.

On an `alarm_on` transition the AI SHALL capture a short video evidence clip from its frame buffer,
write it to a local filesystem path (a `file://` URI, upgradeable to `s3://` later), compute a
content hash (SHA-256) over the written bytes, and include an `evidence` descriptor
`{uri, hash, type, mimeType}` in the event ingest payload, where `type` is a backend
`evidence_file_type_enum` value (`VIDEO` for a clip) and `mimeType` a `mime_type_enum` value
(`video/mp4`). The descriptor is all-or-nothing — when sent, all four fields SHALL be present (the
backend already accepts the optional descriptor and writes the `event_evidence_files` row). If clip
capture fails, the AI SHALL still submit the event without an `evidence` descriptor rather than drop
the event.

Ingest is best-effort but resilient to transient failures: a failed session or event ingest call
(backend unreachable or non-2xx) SHALL be retried a bounded number of times with backoff; if the
retries are exhausted the failure SHALL be logged and the stream service MUST NOT crash and SHALL
continue processing subsequent windows. No unbounded buffering or cross-process persistence of failed
calls is required in this slice.

#### Scenario: Session created on stream connection

- **WHEN** the AI stream alert service connects to a live stream
- **THEN** it calls `POST /api/v1/internal/detection-sessions` with the configured `streamId` and
  `modelId` and a Bearer `AI_SERVICE_TOKEN`, and retains the returned `sessionId`

#### Scenario: Event submitted on alarm_on

- **WHEN** the persistence rule transitions to `alarm_on` past cooldown
- **THEN** the AI calls `POST /api/v1/internal/detection-events` with the retained `sessionId`, the
  mapped `eventType`, `severity`, `confidence`, `startTime`/`endTime`, an AI-generated `dedupKey`, and
  (when a clip was captured) an `evidence` descriptor `{uri, hash, type: VIDEO, mimeType: video/mp4}`

#### Scenario: Predicted label mapped to event_type, unknown maps to OTHER

- **WHEN** the AI maps a predicted label to a backend event type
- **THEN** a label present in the VideoMAE mapping table maps to its `event_type_enum` value, and a
  label not in the table maps to `OTHER`

#### Scenario: Same alarm yields the same dedup key

- **WHEN** the same alarm is re-triggered or the stream reconnects within the alarm window
- **THEN** the AI generates the same `dedupKey` (from stream/camera + alarm-onset time), so the
  backend deduplicates and does not create a duplicate `detection_events` row

#### Scenario: Severity derived from confidence by defined buckets

- **WHEN** the AI computes the event `severity` from the alarm confidence
- **THEN** the result is an integer in `[1, 5]` produced by the documented bucket rule, is
  non-decreasing as confidence increases, and is identical for identical confidence inputs

#### Scenario: Evidence clip captured and sent on alarm_on

- **WHEN** the AI captures a video evidence clip for an `alarm_on` transition
- **THEN** the AI writes the clip to a `file://` path, computes its SHA-256 hash, and the event ingest
  payload includes `evidence = {uri, hash, type: VIDEO, mimeType: video/mp4}` with all four fields set

#### Scenario: Evidence capture failure still submits the event

- **WHEN** clip capture or hashing fails for an `alarm_on` transition
- **THEN** the AI submits the detection event with no `evidence` descriptor and does not drop the event

#### Scenario: Demo dispatch and CSV removed

- **WHEN** the live stream alert service runs after this change
- **THEN** it makes no direct Pushover or SMS call and writes no `stream_timeline.csv` /
  `stream_alarm_events.csv`; detection results flow only through the backend ingest endpoints

#### Scenario: Transient ingest failure is retried then gives up without crashing

- **WHEN** a session or event ingest call fails transiently and then succeeds within the bounded retry budget
- **THEN** the AI retries with backoff and the call ultimately succeeds without operator intervention

#### Scenario: Exhausted ingest retries do not crash the stream service

- **WHEN** a session or event ingest call keeps failing past the bounded retry budget
- **THEN** the failure is logged, no exception escapes, and the stream alert service continues
  processing subsequent windows
