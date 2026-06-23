## ADDED Requirements

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
