## ADDED Requirements

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
