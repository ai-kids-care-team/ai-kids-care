## ADDED Requirements

### Requirement: Production cold-start admin bootstrap

The backend SHALL provide an environment-driven cold-start bootstrap that creates exactly one initial
`SUPERADMIN` account when, and only when, the database has no users — because a production database is
schema-only (Flyway, `Dockerfile.prod`) with no business seed and therefore no accounts at all. The
bootstrap SHALL read `BOOTSTRAP_ADMIN_LOGIN_ID` and `BOOTSTRAP_ADMIN_PASSWORD` from the environment;
when both are present and the `users` table is empty, it SHALL create a single `ACTIVE` `SUPERADMIN`
user with a bcrypt-hashed password and its `SUPERADMIN` role assignment, using the operator-supplied
password as-is (no schema-backed forced password change; credential rotation is handled out-of-band
per the production reset runbook). The bootstrap MUST be idempotent (skip when any user already exists)
and MUST NOT log the password.
When the environment variables are unset, the bootstrap SHALL do nothing (no default/guessable account
is ever created). The bootstrap login id MUST be operator-chosen and SHALL NOT default to `admin`.

#### Scenario: Empty database with bootstrap env creates one admin

- **WHEN** the backend starts against a database with zero `users` rows and `BOOTSTRAP_ADMIN_LOGIN_ID` + `BOOTSTRAP_ADMIN_PASSWORD` are set
- **THEN** exactly one `ACTIVE` `SUPERADMIN` user is created with the given login id, a bcrypt password hash, and a `SUPERADMIN` role assignment, using the operator-supplied password as-is

#### Scenario: Bootstrap is idempotent when users already exist

- **WHEN** the backend restarts against a database that already has at least one user
- **THEN** the bootstrap creates no account and makes no change, regardless of the env variables

#### Scenario: No env means no default account

- **WHEN** the backend starts with `BOOTSTRAP_ADMIN_LOGIN_ID`/`BOOTSTRAP_ADMIN_PASSWORD` unset
- **THEN** no user is created and no default or guessable credential exists in the database

### Requirement: No default or guessable production credentials

A production deployment SHALL NOT contain any default, guessable, or shared-across-roles credential.
The demo/CI business seed accounts under `db/initdb/` — including the test-anchor `login_id='admin'`,
`user_id=1` and the per-role demo accounts — are demo/CI/local-only and SHALL NOT be present in a
production database (which is seed-free per the data-platform `Production does not depend on seed`
requirement). The only path to a production credential is the operator-driven cold-start bootstrap,
whose login id is operator-chosen (not `admin`) and whose password is an operator-set strong secret.

#### Scenario: Robot scan of admin/admin123 fails on production

- **WHEN** an unauthenticated client attempts to log in to a production deployment with `admin`/`admin123` (or any demo seed account)
- **THEN** authentication fails with `401` because no such account exists in the seed-free production database

#### Scenario: Demo seed remains test/CI-only

- **WHEN** the production image (`Dockerfile.prod`) is built and the production stack starts
- **THEN** no `db/initdb` business seed (including the `admin`/`user_id=1` anchor) is applied, while the integration-test container and demo/local images still load the full seed for their fixtures

### Requirement: Login attempt throttling and lockout

The backend SHALL throttle repeated failed login attempts to resist online brute-force/credential
stuffing. Failed attempts SHALL be counted per login identifier (and SHOULD additionally consider the
client IP), using the existing Redis infrastructure with a bounded time window. After a configurable
threshold of consecutive failures the account/identifier SHALL be temporarily locked: further login
attempts SHALL be rejected with `429 Too Many Requests` until a configurable cooldown elapses, even if
the supplied password is correct. A successful login SHALL reset the failure counter. Throttling and
lockout events SHALL be audited via `SecurityAuditWriter` without recording the submitted password.
Lockout state MAY be Redis-only (TTL-based) and need not be persisted in PostgreSQL.

#### Scenario: Repeated failures trigger lockout

- **WHEN** a client submits the configured threshold of consecutive failed logins for one identifier within the window
- **THEN** subsequent login attempts for that identifier return `429` until the cooldown elapses, and an audit record of the lockout is written

#### Scenario: Locked identifier rejects even correct password

- **WHEN** an identifier is in the locked state and a request presents that identifier with the correct password
- **THEN** the response is `429` (not a successful `200`) until the cooldown elapses

#### Scenario: Successful login resets the failure counter

- **WHEN** a login succeeds before the lockout threshold is reached
- **THEN** the failure counter for that identifier is cleared so prior failures do not accumulate toward a later lockout

## MODIFIED Requirements

### Requirement: Authentication Failure Response Contracts

Authentication and authorization failure responses MUST use explicit HTTP semantics, not accidental Spring `/error` dispatch. Error response bodies MUST NOT contain stack traces, SQL, internal class names, secrets, or PII.

| Condition | HTTP Status |
|---|---|
| No session, expired session, invalid credentials, user/role/membership invalid | `401` |
| Authenticated but role or action not permitted | `403` |
| CSRF token missing or invalid | `403` |
| Resource not in effective tenant or relationship absent (existence must be hidden) | `404` |
| Request field invalid | `400` |
| Duplicate or conflicting resource | `409` |
| Login throttled / identifier temporarily locked after repeated failures | `429` |

#### Scenario: 401 response contains no sensitive data

- **WHEN** any unauthenticated or invalidly authenticated request triggers a `401`
- **THEN** the response body is empty or contains only `{"error":"Authentication failed"}` — no password hash, RRN, session ID, stack trace, or internal field name is present

#### Scenario: Error responses do not echo submitted sensitive values

- **WHEN** a `400` validation failure occurs on a registration request that included a password or RRN field
- **THEN** the error response does not reflect back the submitted password or RRN values

#### Scenario: Throttled login returns 429 without leaking why

- **WHEN** a login attempt is rejected because the identifier is temporarily locked after repeated failures
- **THEN** the response status is `429` and the body contains no password, hash, or internal field name
