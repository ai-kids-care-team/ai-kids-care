## ADDED Requirements

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
