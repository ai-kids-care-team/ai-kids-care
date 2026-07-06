# testing-and-ci Specification (delta)

## ADDED Requirements

### Requirement: Frontend has a unit test harness for pure logic and components
The frontend SHALL provide a local unit test harness (Vitest + React Testing Library +
jsdom) enabling per-capability unit tests of pure logic (API helpers, enum/status
derivations, pagination mapping) and lightweight component rendering, authored via TDD as
capabilities evolve. The harness SHALL run via an `npm run test:run` script and SHALL NOT
alter the existing `lint`/`build` static-export pipeline.

#### Scenario: Running the frontend unit test suite
- **WHEN** a contributor runs `npm run test:run` in `frontend/`
- **THEN** Vitest executes all `src/**/*.{test,spec}.{ts,tsx}` files in a jsdom environment and reports pass/fail

#### Scenario: Notification unread derivation is regression-guarded
- **WHEN** the notification status enum allowlist in `notifications.api.ts` is changed
- **THEN** a unit test asserting the unread/read classification of every status value fails if the allowlist regresses

#### Scenario: Test harness does not affect the production build
- **WHEN** the frontend is built via `npm run build`
- **THEN** the static export succeeds unchanged and no test-only dependency is bundled into the output
