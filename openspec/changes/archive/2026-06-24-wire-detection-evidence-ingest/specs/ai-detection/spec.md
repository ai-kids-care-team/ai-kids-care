## ADDED Requirements

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
