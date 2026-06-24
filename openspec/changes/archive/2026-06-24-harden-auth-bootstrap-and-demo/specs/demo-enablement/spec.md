## ADDED Requirements

### Requirement: Documented demo accounts for end-to-end demonstration

The project SHALL provide documentation of the demo/CI seed accounts usable to walk the end-to-end
flow (login → detection dashboard → realtime event → review → guardian notification), listing for each
account its `login_id`, role, tenant (kindergarten), and intended demonstration step. The documented
accounts SHALL correspond to the actual `db/initdb` demo seed (e.g. per-kindergarten KINDERGARTEN_ADMIN
/ TEACHER / GUARDIAN accounts), and the documentation SHALL state explicitly that these accounts are
demo/CI/local-only and never exist in a seed-free production database.

#### Scenario: Demo accounts documented with role, tenant, and step

- **WHEN** a demonstrator consults the demo documentation
- **THEN** it lists each demo account's `login_id`, role, kindergarten, and which demo step it is used for, and states the accounts are not present in production

### Requirement: Manual detection-event injection for demonstration

To demonstrate the realtime detection loop without running the AI inference service, the project SHALL
provide a standalone script that injects a detection session and one or more detection events through
the backend internal ingest endpoints (`POST /api/v1/internal/detection-sessions` and
`/api/v1/internal/detection-events`) using a Bearer `AI_SERVICE_TOKEN`. The script SHALL accept the
backend URL, the token, and the target stream/model identifiers as parameters, and SHALL drive a
visible event onto the realtime dashboard (SSE) for the corresponding kindergarten.

#### Scenario: Injection script pushes a visible detection event

- **WHEN** the demonstrator runs the injection script with a valid `AI_SERVICE_TOKEN`, backend URL, and seed stream/model ids
- **THEN** the backend persists a detection session and event and pushes the event over SSE to that kindergarten's connected dashboard, exactly as a real AI ingest would

#### Scenario: Injection rejected without a valid token

- **WHEN** the injection script targets the internal ingest endpoints without a valid `AI_SERVICE_TOKEN`
- **THEN** the backend rejects the request (no event is created), matching the existing internal-ingest authentication contract

### Requirement: Demo credential hints never render in production

The frontend demo credential hints (gated by `NEXT_PUBLIC_SHOW_DEMO_HINTS`) SHALL be disabled by
default and SHALL NOT render in a production build/deployment. When enabled for demo/local use, the
hint text SHALL match the actual demo seed accounts. A production deployment SHALL NOT expose any
credential hint in the login UI.

#### Scenario: Production login UI shows no credential hints

- **WHEN** the frontend runs in a production deployment (with `NEXT_PUBLIC_SHOW_DEMO_HINTS` unset or false)
- **THEN** the login page renders no demo account/password hint

#### Scenario: Enabled hints match real demo accounts

- **WHEN** demo hints are enabled for a demo/local environment
- **THEN** the displayed login ids correspond to accounts that actually exist in the demo seed
