## ADDED Requirements

### Requirement: Kindergarten staff immediate alert on AI alarm (pre-review)

The notification subsystem SHALL dispatch an immediate (pre-review) alert to applicable kindergarten
staff (KINDERGARTEN_ADMIN, TEACHER) when the backend ingests a detection event from the AI. The
alert trigger is the AI persistence-rule `alarm_on` (a debounced sliding-window signal — single-frame
threshold 0.60, ≥30s span, ≥~8 hits, ≥50% hit ratio, hysteresis clear at 40%, 120s cooldown), NOT a
single-inference confidence value; the backend therefore SHALL NOT define its own global confidence
threshold. Which staff/rules receive the alert MAY be further narrowed by `notification_rules`
(including `min_severity`). The staff alert SHALL be delivered over every channel the recipient has
available — Pushover (via the recipient's `push_subscriptions`) and SMS (via `users.phone`): if a
recipient has both, both are used; if only one, that one is used. The subsystem SHALL also record an
in-app notification and surface the event on the real-time frontend dashboard, so staff can quickly
enter the system to review and act. This staff alert does NOT notify parents (see the guardian
review-gate requirement).

#### Scenario: Staff alerted immediately on ingest, across available channels

- **WHEN** the backend ingests an AI detection event (alarm_on)
- **THEN** applicable staff receive an immediate alert via Pushover and/or SMS (whichever they have; both if both), an in-app notification is recorded, and the event appears on the frontend dashboard — without waiting for `event_reviews` confirmation

#### Scenario: No backend confidence threshold is required

- **WHEN** the staff-alert path is enabled in production
- **THEN** it relies on the AI persistence-rule alarm as the debounced trigger and does not gate on a backend-defined confidence threshold; per-rule `min_severity` is the only severity filter

#### Scenario: Staff alert does not reach parents

- **WHEN** a staff immediate alert is dispatched for an ingested detection event
- **THEN** no parent/guardian notification is sent by this path; guardian notification happens only after staff review confirmation

## REMOVED Requirements

### Requirement: Kindergarten staff immediate alert on high-confidence detection

**Reason**: The trigger was reframed — false-positive suppression is done by the AI persistence-rule
(sliding-window hysteresis state machine), so the staff alert fires on the AI `alarm_on` signal, not
on a backend "high-confidence threshold". The prior requirement's open gap ("exact confidence
thresholds MUST be defined") is therefore resolved/removed.

**Migration**: Replaced by "Kindergarten staff immediate alert on AI alarm (pre-review)", which keys
off the AI alarm delivered via backend ingest and specifies the staff delivery channels (Pushover +
SMS + in-app + dashboard).
