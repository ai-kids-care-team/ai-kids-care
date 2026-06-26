## ADDED Requirements

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
