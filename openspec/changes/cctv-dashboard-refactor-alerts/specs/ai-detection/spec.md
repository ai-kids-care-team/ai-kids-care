# ai-detection Specification (delta)

## ADDED Requirements

### Requirement: CCTV dashboard surfaces live detection alerts

The CCTV monitoring dashboard SHALL display real detection alerts for the viewer's kindergarten, loading recent alerts via the detection read API and subscribing to the detection SSE stream (`GET /api/v1/detection-events/stream`) for live updates, reusing the shared `useDetectionEventStream` client. The subscription SHALL be enabled only when the viewer is permitted to view live streams (currently `KINDERGARTEN_ADMIN`) and SHALL key its reconnection on the active kindergarten. Incoming events SHALL be de-duplicated by event id. The dashboard SHALL NOT render a permanently empty alert surface from an unconditional reset.

#### Scenario: Dashboard shows live alerts as they arrive

- **WHEN** a permitted viewer has the CCTV dashboard open and the backend emits a `detection-event`
- **THEN** the alert panel prepends the new event (de-duplicated by event id), updating the active alert count and severity badges without a page reload

#### Scenario: Dashboard loads recent alerts on open

- **WHEN** a permitted viewer opens the CCTV dashboard
- **THEN** it loads the kindergarten's recent detection events via the read API rather than resetting the alert list to empty

#### Scenario: Non-permitted role does not open a rejected stream

- **WHEN** a viewer without live-stream permission navigates to the dashboard
- **THEN** no SSE subscription to the detection stream is opened for them

### Requirement: Detection severity presentation is shared, not duplicated per page

The severity level derivation and badge styling for detection events SHALL be provided by a single shared module used by both the CCTV dashboard and the detection events dashboard, so the two surfaces present the same severity consistently.

#### Scenario: Both dashboards render the same severity banding

- **WHEN** a detection event of a given confidence/severity is shown on either the CCTV dashboard or the detection events dashboard
- **THEN** both derive its severity level and badge styling from the same shared module
