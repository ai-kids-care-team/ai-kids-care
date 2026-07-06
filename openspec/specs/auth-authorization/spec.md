# auth-authorization Specification

## Purpose
定义认证与授权能力：服务端会话认证（Spring Session + Redis + httpOnly cookie + CSRF）、按 kindergarten_id 的租户隔离、基于角色的集中式策略（@PreAuthorize 门 + 角色分支 + tenant-scoped JPQL）、关闭路径与认证失败的显式响应契约、前端不得从客户端状态推断租户/角色/身份。
## Requirements
### Requirement: Server-Side Session Authentication

The system SHALL authenticate all browser users via Spring Session backed by Redis. Session identity SHALL be conveyed exclusively through an `httpOnly` cookie named `AI_KIDS_CARE_SESSION`. The session cookie SHALL use `SameSite=Lax`; production deployments MUST set `Secure=true` (controlled by `SESSION_COOKIE_SECURE`). JWT stateless authentication is superseded (ADR-0016 replaces ADR-0007); no access token, refresh token, or bearer credential SHALL appear in login responses.

#### Scenario: Successful login establishes server-side session

- **WHEN** an anonymous user POSTs valid credentials to `POST /api/v1/auth/login` with an ACTIVE user account and exactly one ACTIVE role assignment
- **THEN** the server creates a Redis-backed session, sets the `AI_KIDS_CARE_SESSION` `httpOnly` cookie, and returns a minimal `AuthSessionVO` containing only `userId`, `loginId`, `effectiveRole`, `scopeType`, and `scopeId` — with no access token, refresh token, password hash, RRN, or any credential

#### Scenario: Login fails without exposing account existence

- **WHEN** a login request contains invalid credentials, an INACTIVE user, zero ACTIVE role assignments, or more than one ACTIVE role assignment
- **THEN** the server returns a generic `401 {"error":"Authentication failed"}` without revealing whether the account exists, and does not create any session

#### Scenario: CSRF token is required for write requests

- **WHEN** an authenticated user submits a state-changing request (POST, PUT, DELETE, PATCH) without a valid `X-XSRF-TOKEN` header matching the `XSRF-TOKEN` cookie
- **THEN** the server returns `403` and the operation is not performed

---

### Requirement: Default-Deny Authentication Boundary

All endpoints under `/api/v1/**` MUST require an authenticated session except for an explicit public allowlist. Anonymous requests to any endpoint outside the allowlist SHALL return `401`.

The allowlist SHALL contain only:
- `GET /auth/csrf` (CSRF bootstrap)
- `POST /api/v1/auth/login`
- `POST /api/v1/auth/register`
- `GET /api/v1/auth/register/availability`
- `POST /api/v1/auth/guardian-child-verifications`
- `POST /api/v1/auth/password-reset/request` (enumeration-safe reset initiation; CSRF still enforced)
- `POST /api/v1/auth/password-reset/verify` (reset code verification; CSRF still enforced)
- `POST /api/v1/auth/password-reset/confirm` (reset completion; CSRF still enforced)
- `GET /api/v1/kindergartens/**`, `GET /api/v1/enums/**` (S3 read-only directory/reference required before login)
- `OPTIONS /**` (CORS preflight)
- Swagger/OpenAPI docs (non-production only; MUST be closed or restricted to `PLATFORM_IT_ADMIN` in production)

`POST /api/v1/auth/change-password` MUST NOT be on the allowlist — it is authenticated and protected by default-deny. `POST /api/v1/auth/refresh` MUST remain removed (not whitelist-eligible in session mode). Adding the three `password-reset/**` POST endpoints to the allowlist does NOT weaken CSRF: only `/api/v1/internal/**` is CSRF-exempt, so the reset endpoints still require a valid `X-XSRF-TOKEN`.

#### Scenario: Anonymous access to a business endpoint returns 401

- **WHEN** an unauthenticated request is made to any business endpoint outside the allowlist (e.g., `GET /api/v1/classes`)
- **THEN** the server returns `401` and does not process the request or reveal resource existence

#### Scenario: Public allowlist endpoints are reachable without a session

- **WHEN** an anonymous request is made to `POST /api/v1/auth/login`, `GET /api/v1/kindergartens` (S3 directory), or `POST /api/v1/auth/password-reset/request` (with a valid CSRF token)
- **THEN** the server processes the request normally without requiring a session cookie

#### Scenario: Public reset endpoints still require CSRF

- **WHEN** an anonymous request is made to `POST /api/v1/auth/password-reset/request` without a valid `X-XSRF-TOKEN`
- **THEN** the server returns `403` and does not process the request

### Requirement: Effective Authorization Context Per Request

For every authenticated request the backend MUST construct an `EffectiveAuthorizationContext` by authoritative database re-resolution — never from client-supplied values or stale Redis-cached role snapshots. The context SHALL contain: `userId`, `effectiveRole`, `scopeType`, `scopeId`, `activeKindergartenId` (KINDERGARTEN scope only), `roleAssignmentId`, and `membershipId` (KINDERGARTEN scope only).

Resolution MUST enforce:
1. The user is ACTIVE.
2. Exactly one ACTIVE role assignment exists and matches the session principal's stored `roleAssignmentId`.
3. For `KINDERGARTEN` scope: exactly one ACTIVE membership matches `scopeId`; no second-kindergarten membership is ACTIVE.
4. For `PLATFORM` scope: `scopeId` is null; no kindergarten membership exists.
5. Any invalid or drifted state MUST immediately invalidate the session and return `401`.

#### Scenario: Role revocation is enforced on the next request

- **WHEN** an admin revokes a user's ACTIVE role assignment while that user has a valid session, and the user subsequently makes any authenticated request
- **THEN** the `EffectiveAuthorizationContextFilter` resolves no valid ACTIVE role assignment, invalidates the session, and returns `401` — the user cannot continue accessing business APIs

#### Scenario: User disablement is enforced on the next request

- **WHEN** a user account is disabled while the user has a valid session, and the user subsequently makes any authenticated request
- **THEN** the filter resolves the user as INACTIVE, invalidates the session, and returns `401`

---

### Requirement: Tenant Isolation by kindergarten_id

All tenant-scoped read, write, update, and delete operations MUST include the `activeKindergartenId` from the `EffectiveAuthorizationContext` as a mandatory query condition. Tenant-scoped JPQL/SQL queries MUST place the tenant constraint inside the query predicate — not in post-load filtering. Cross-tenant resource IDs (where the resource belongs to a different kindergarten than the effective context) SHALL return `404` to avoid revealing resource existence.

A client-supplied `kindergartenId` in path, query, or request body MAY be used only for consistency validation. If it differs from `activeKindergartenId`, the request MUST be rejected with `404` (not `403`), and the tenant context MUST NOT be switched.

#### Scenario: Cross-tenant GET returns 404

- **WHEN** a `KINDERGARTEN_ADMIN` user of kindergarten A requests `GET /api/v1/rooms/{id}` where `id` belongs to kindergarten B
- **THEN** the tenant-aware repository query finds no row (because the tenant condition eliminates it), and the server returns `404 {"error":"Resource not found"}` without revealing that the room exists in another kindergarten

#### Scenario: Client kindergartenId tampering is rejected

- **WHEN** a `KINDERGARTEN_ADMIN` user of kindergarten A submits a write request with `kindergartenId` set to kindergarten B's ID in the request body
- **THEN** the server detects the mismatch with `activeKindergartenId`, rejects the request with `404`, and does not write any data to kindergarten A or B

#### Scenario: Cross-tenant list returns only own-tenant resources

- **WHEN** an authenticated tenant user requests a list endpoint (e.g., `GET /api/v1/rooms`)
- **THEN** the response contains only resources belonging to the user's effective kindergarten — no resources from any other kindergarten appear in the list

---

### Requirement: Role-Based Access Control with Centralized Policy

All business operations MUST be gated by `@PreAuthorize` using a centralized `AuthorizationPolicy` bean that reads the request-scoped `EffectiveAuthorizationContext`. Role checks MUST NOT be duplicated in individual service methods. Every published Controller operation MUST have an explicit policy classification. The default for any unclassified operation is deny.

Role boundaries SHALL be:
- `GUARDIAN`: own account non-sensitive fields; children with ACTIVE `child_guardian_relationships`; related announcements and own notification settings. Cannot access live CCTV, playback, detection evidence, camera config, or internal storage URIs.
- `TEACHER`: own profile; classes and rooms covered by ACTIVE `class_teacher_assignments` (within valid date window); children reachable via ACTIVE `child_class_assignments` chain; cannot access cameras/streams/detection sessions (`KINDERGARTEN_ADMIN`-only).
- `KINDERGARTEN_ADMIN`: full tenant scope for user approvals, memberships, classes, rooms, camera metadata, detection sessions, and announcements; may approve only roles within own kindergarten; approver level MUST be `DIRECTOR` or `VICE_DIRECTOR`.
- `PLATFORM_IT_ADMIN`: platform operations, service health, AI model metadata, non-sensitive kindergarten directory. MUST NOT read child/guardian/teacher S1 PII, raw evidence, notification bodies, or camera credentials by default.
- `SUPERADMIN`: cross-kindergarten governance and aggregate data; MUST NOT read specific person S1 data, live CCTV, playback, or detection evidence until a future Spec/ADR explicitly permits it.

#### Scenario: Wrong role returns 403

- **WHEN** a `TEACHER` user requests `GET /api/v1/cctv_cameras` (surveillance, `KINDERGARTEN_ADMIN`-only)
- **THEN** the server returns `403` without processing the resource query

#### Scenario: PLATFORM_IT_ADMIN cannot access tenant S1 data

- **WHEN** a `PLATFORM_IT_ADMIN` requests a resource endpoint that returns child or guardian PII classified as S1
- **THEN** the server returns `403` — the platform IT role does not grant access to tenant person data

---

### Requirement: Authz Read-Slice Pattern

Every newly opened or reopened business resource Controller MUST implement the authz read-slice pattern: `@PreAuthorize` coarse gate → role branch in service → tenant- or relationship-scoped JPQL. Tenant filtering MUST be placed inside the SQL/JPQL predicate (not as post-load memory filter). Non-existent or out-of-scope resources MUST return `404` (hidden), not `403`.

#### Scenario: Tenant-scoped JPQL hides cross-tenant resource

- **WHEN** a service method executes `findByIdAndKindergarten_Id(resourceId, activeKindergartenId)` and the resource belongs to a different kindergarten
- **THEN** the repository returns empty, the service throws `EntityNotFoundException`, and the API handler maps it to `404 {"error":"Resource not found"}` — the role boundary check and the tenant boundary check are both enforced inside the same query, not sequentially

---

### Requirement: Guardian Child Relationship Boundary

A `GUARDIAN` principal MUST access children only when an ACTIVE `child_guardian_relationships` row exists linking the guardian's profile to that child within the same kindergarten. Relationship validity is determined by `end_date IS NULL OR end_date >= today` plus ACTIVE guardian profile and ACTIVE membership. No relationship → `404` (hidden, audited as `AUTHORIZATION_DENIED`). The response MUST use a minimal VO (e.g., `GuardianChildVO` with `childId`, `name`, `status`) — full `ChildVO` with RRN, address, or birth date MUST NOT be returned via public endpoints.

#### Scenario: Guardian can access a linked child

- **WHEN** a `GUARDIAN` requests `GET /api/v1/children/{id}` for a child with an ACTIVE guardian relationship
- **THEN** the server returns `200` with the minimal `GuardianChildVO` (no RRN, address, or birth date)

#### Scenario: Guardian cannot access an unlinked child

- **WHEN** a `GUARDIAN` requests `GET /api/v1/children/{id}` for a child in the same kindergarten but without an ACTIVE guardian relationship
- **THEN** the server returns `404 {"error":"Resource not found"}` and writes an `AUTHORIZATION_DENIED` audit record — the child's existence is not revealed

#### Scenario: Guardian is denied surveillance access

- **WHEN** a `GUARDIAN` requests any camera, stream, detection session, or evidence endpoint
- **THEN** the server returns `403` without processing the resource query

---

### Requirement: Teacher Assignment Boundary

A `TEACHER` principal MUST access classes, rooms, and children only when covered by an ACTIVE `class_teacher_assignments` row (with a valid date window: `start_date <= today AND (end_date IS NULL OR end_date > today)`). Access to a room requires an additional ACTIVE `class_room_assignments` chain. Access to a child requires an ACTIVE `child_class_assignments` chain to an assigned class. Cameras, streams, and detection sessions are `KINDERGARTEN_ADMIN`-only; teacher requests to those endpoints SHALL return `403`.

#### Scenario: Teacher sees only assigned classes

- **WHEN** a `TEACHER` requests `GET /api/v1/classes`
- **THEN** only classes covered by at least one valid `class_teacher_assignments` row are returned; classes in the same kindergarten but not assigned return `404` on direct access

#### Scenario: Teacher with expired assignment loses access

- **WHEN** a `TEACHER`'s `class_teacher_assignments` row has `end_date` in the past, and the teacher requests the formerly-assigned class
- **THEN** the server returns `404` (the assignment is no longer valid) and writes an `AUTHORIZATION_DENIED` audit record

---

### Requirement: Anonymous Registration Produces PENDING-Only Records

The public `POST /api/v1/auth/register` endpoint MUST create user, role assignment, business profile, and kindergarten membership records with status `PENDING` for allowed roles (`GUARDIAN`, `TEACHER`, `KINDERGARTEN_ADMIN`, `SUPERADMIN`). Registration for `PLATFORM_IT_ADMIN` MUST be rejected before any persistence with `400`. Client-supplied `status`, `scopeType`, `scopeId`, or approval-granting fields MUST be ignored or rejected. A PENDING registration MUST NOT establish a business session.

#### Scenario: PLATFORM_IT_ADMIN public registration is rejected

- **WHEN** an anonymous user submits a registration request with `role=PLATFORM_IT_ADMIN`
- **THEN** the server returns `400` and creates no user, profile, role assignment, or membership record

#### Scenario: Allowed role registration creates PENDING records

- **WHEN** an anonymous user submits a valid registration for `TEACHER`
- **THEN** the server creates the user, teacher profile, role assignment, and membership all with status `PENDING`, and the user cannot login until approval is complete

#### Scenario: Client status escalation is ignored

- **WHEN** a registration request body includes `"status":"ACTIVE"` for any field
- **THEN** the server ignores the value and persists all records as `PENDING`

---

### Requirement: Approval Requires Authorized KINDERGARTEN_ADMIN Approver

Guardian, teacher, and director/vice-director applications MUST be approved only by an ACTIVE `KINDERGARTEN_ADMIN` principal whose `teachers.level` is `DIRECTOR` or `VICE_DIRECTOR`, within the same kindergarten, and who MUST NOT approve their own application. Approval MUST activate user, profile, membership, and role assignment in a single transaction. `DIRECTOR` and `VICE_DIRECTOR` levels both result in `KINDERGARTEN_ADMIN` role; level alone MUST NOT confer approval authority.

#### Scenario: Only a director-level admin can approve

- **WHEN** a `KINDERGARTEN_ADMIN` with `teachers.level = TEACHER` attempts to approve a pending application
- **THEN** the server returns `403` and the application status remains `PENDING`

#### Scenario: Self-approval is rejected

- **WHEN** a `KINDERGARTEN_ADMIN` approver attempts to approve their own pending application
- **THEN** the server returns `403` and the application is not activated

---

### Requirement: Single Account Single Role

Each user account MUST have at most one ACTIVE role assignment. A `KINDERGARTEN`-scoped account MUST belong to exactly one kindergarten. A `PLATFORM`-scoped account MUST NOT have any kindergarten membership. Database constraints or equivalent strong-consistency mechanisms MUST enforce these invariants. Login MUST NOT silently fall back to `GUARDIAN` or pick the most-recent role when multiple ACTIVE role assignments exist.

#### Scenario: Multiple ACTIVE role assignments block login

- **WHEN** a user account has two ACTIVE role assignments (a data integrity violation) and attempts to login
- **THEN** the server returns generic `401` without establishing a session and without revealing the reason

#### Scenario: Kindergarten account cannot gain a second kindergarten

- **WHEN** any operation would create a second ACTIVE kindergarten membership for a `KINDERGARTEN`-scoped user
- **THEN** the operation is rejected by a database unique constraint or equivalent mechanism before the record is written

---

### Requirement: Session Revocation on State Changes

Logout (`POST /api/v1/auth/logout`) MUST delete the current Redis session and clear the session cookie, returning `204`. `POST /api/v1/auth/logout-all` MUST delete all sessions for the authenticated user via `SessionRevocationService`. Password change, account disablement, role revocation, membership termination, and admin force-logout MUST invalidate all affected sessions. Invalidated sessions MUST return `401` on subsequent requests and MUST NOT degrade to anonymous business access.

#### Scenario: Logout invalidates the session

- **WHEN** an authenticated user calls `POST /api/v1/auth/logout`
- **THEN** the Redis session is deleted, the session cookie is cleared, and a subsequent request with the old cookie returns `401`

#### Scenario: Membership termination triggers 401 on next request

- **WHEN** a user's kindergarten membership is terminated while the user has a valid session, and the user subsequently makes an authenticated request
- **THEN** the `EffectiveAuthorizationContextFilter` finds no valid ACTIVE membership, invalidates the session, and returns `401`

---

### Requirement: Platform Role Tenant Context Selection

Only `PLATFORM`-scoped principals MAY call `POST /api/v1/auth/session/tenant-context` to select a target kindergarten for management operations. The server MUST validate that the kindergarten exists and is accessible before writing `selectedKindergartenId` to the session. Selecting a tenant MUST NOT change `scopeType` from `PLATFORM`, create a membership, grant a kindergarten role, or expand the platform role's data classification permissions. `KINDERGARTEN`-scoped users calling this endpoint SHALL receive `403`.

#### Scenario: Platform role selects a tenant context

- **WHEN** a `SUPERADMIN` calls `POST /api/v1/auth/session/tenant-context` with a valid kindergarten ID
- **THEN** the server validates the kindergarten, writes `selectedKindergartenId` to the session, records an audit event with `scope_type=PLATFORM` and `kindergarten_id=NULL` (not the selected ID), and returns success — the user's role remains `SUPERADMIN` with `scopeType=PLATFORM`

#### Scenario: Kindergarten role is denied tenant context selection

- **WHEN** a `KINDERGARTEN_ADMIN` calls `POST /api/v1/auth/session/tenant-context`
- **THEN** the server returns `403` and does not modify the session

#### Scenario: Platform role cannot access S1 data after tenant selection

- **WHEN** a `PLATFORM_IT_ADMIN` has selected a tenant context and requests a child or guardian detail endpoint
- **THEN** the server returns `403` — tenant context selection does not expand the platform role's data classification permissions

---

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

### Requirement: Security Audit Logging

The system MUST write audit records for security-relevant events using `SecurityAuditWriter` (direct `audit_logs` insert, `REQUIRES_NEW` transaction, best-effort). Audit records MUST NOT contain passwords, RRN values, session IDs, tokens, credentials, or request body content. Platform-level events MUST be recorded with `scope_type=PLATFORM` and `kindergarten_id=NULL` — a platform kindergarten ID MUST NOT be fabricated to represent platform events.

Required audit events:
- Login success, login failure, logout, `logout-all` session revocation
- Platform tenant context selection and switching
- User, role, and membership creation, approval, granting, revocation, and status changes
- S1 data reads: child detail, guardian/teacher detail, detection evidence access (pre-reserved as `AuditAction.S1_EVIDENCE_READ`)
- Cross-tenant access attempts, authorization denials (`AUTHORIZATION_DENIED`), and privileged role registration attempts
- `SUPERADMIN` entering a specific tenant context, reason, and resources accessed

Each audit record MUST include: actor `userId`, effective role/scope, action, resource type/id, tenant id (or null for platform events), result, timestamp, and correlation ID (from `CorrelationIdFilter`, not session ID).

#### Scenario: Login audit records are written

- **WHEN** a login attempt succeeds or fails
- **THEN** `SecurityAuditWriter` inserts an `audit_logs` row with the correct `action` (`LOGIN_SUCCESS` or `LOGIN_FAILURE`), `result`, actor information, correlation ID, and no S0 data

#### Scenario: Platform audit event does not fabricate kindergarten_id

- **WHEN** a `SUPERADMIN` switches tenant context to kindergarten 5
- **THEN** the audit record has `scope_type=PLATFORM`, `kindergarten_id=NULL`, and the selected kindergarten ID stored in `resource_id` — not in `kindergarten_id`

#### Scenario: Audit records are append-only via business API

- **WHEN** any authenticated user calls any published endpoint and attempts to create, update, or delete an audit log entry
- **THEN** the server returns `405` or `404` — no public create/update/delete operation is published for the `audit_logs` resource

---

### Requirement: Frontend Must Not Infer Tenant or Role from Client State

The frontend MUST NOT derive `kindergartenId`, role, or user identity from JWT parsing, `localStorage` persistence, demo user IDs, Redux state that survives page refresh, or any value not obtained from the server-authoritative session profile. All authentication state MUST be held in Redux memory only and cleared on `401` or page refresh. The login response and `GET /api/v1/auth/session` are the only authoritative sources of role and scope information for UI routing decisions.

Frontend route guards and hidden menus are UX conveniences only; they MUST NOT be treated as security controls.

#### Scenario: Page refresh clears all client auth state

- **WHEN** a user refreshes the browser page
- **THEN** the frontend clears all in-memory session state and re-hydrates solely from `GET /api/v1/auth/session` (which reads the `httpOnly` cookie) — no role, kindergartenId, or token is read from `localStorage` or the URL

#### Scenario: 401 clears in-memory session and redirects to login

- **WHEN** the backend returns `401` on any API call
- **THEN** the frontend clears all in-memory auth state and redirects the user to the login page — no fallback to a cached role or demo ID occurs

---

### Requirement: Production Session Hardening Prerequisites

A production deployment MUST NOT serve session-authenticated traffic until all of the following are simultaneously active:
- HTTPS termination (Caddy edge TLS with valid certificate per ADR-0017)
- `SESSION_COOKIE_SECURE=true` (enforces `Secure` attribute on `AI_KIDS_CARE_SESSION`)
- CSRF protection enabled
- Default-deny `authenticated()` on `/api/v1/**`

#### Scenario: Production compose enforces Secure cookie and HTTPS

- **WHEN** the merged production compose configuration (`docker-compose.yml` + `docker-compose.prod.yml`) is resolved
- **THEN** `SESSION_COOKIE_SECURE` is `"true"` for the backend service, Caddy occupies host ports 80/443, and the frontend does not publish any host port directly

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

### Requirement: Authenticated Self-Service Password Change

The system SHALL let an authenticated user change their own password via `POST /api/v1/auth/change-password`. The endpoint SHALL require an authenticated session and a valid `X-XSRF-TOKEN` (it is NOT on the public allowlist; default-deny protects it). The request body SHALL contain `currentPassword` and `newPassword`. The target user SHALL be resolved from the server-side authorization context (`EffectiveAuthorizationContextHolder`), never from the request body. The server SHALL verify `currentPassword` against the stored bcrypt hash, SHALL validate `newPassword` against the existing `@ValidPassword` complexity policy, and SHALL persist the new password as a bcrypt hash. On success the server SHALL revoke all server sessions for that user (all devices, including the current session) so every device must re-authenticate with the new password. The current password, new password, and password hashes SHALL NEVER be logged.

#### Scenario: Authenticated user changes password with correct current password

- **WHEN** an authenticated user POSTs a correct `currentPassword` and a policy-compliant `newPassword` to `POST /api/v1/auth/change-password` with a valid CSRF token
- **THEN** the server stores the new bcrypt hash, returns `204`, and revokes all of that user's server sessions (the next request on any prior session returns `401`)

#### Scenario: Wrong current password is rejected

- **WHEN** an authenticated user POSTs an incorrect `currentPassword`
- **THEN** the server returns `400 {"error": ...}`, does not change the stored password, and does not reveal any credential

#### Scenario: New password failing complexity is rejected

- **WHEN** the `newPassword` violates the `@ValidPassword` complexity policy
- **THEN** the server returns `400 {"error": ...}` (the localized validation message) and does not change the stored password

---

### Requirement: Self-Service Password Reset via SMS (Enumeration-Safe)

The system SHALL provide an unauthenticated self-service password reset over SMS as the only channel (email and push are out of scope). It SHALL be a three-step flow — `POST /api/v1/auth/password-reset/request`, `POST /api/v1/auth/password-reset/verify`, `POST /api/v1/auth/password-reset/confirm` — all three added to the public POST allowlist yet still protected by CSRF (only `/api/v1/internal/**` is CSRF-exempt). All temporary state (verification code, single-use reset token, throttle counters) SHALL be stored in Redis with a TTL; no database table or schema migration SHALL be introduced. Verification codes (plaintext), reset tokens, `users.phone`, and RRN SHALL NEVER appear in logs, audit records, or exception messages.

Account enumeration SHALL be prevented: `password-reset/request` SHALL ALWAYS return `200` with a structurally identical body `{ challengeId, expiresAt }` (an opaque random `challengeId`) regardless of whether the account exists or has a phone number. An SMS SHALL be sent only when the account exists AND `users.phone` is non-blank; otherwise a dummy challenge is stored so verification fails uniformly. `password-reset/verify` SHALL return a uniform `400` for a wrong code, an expired/unknown challenge, a dummy challenge, or an exceeded attempt cap — never differentiating by account existence. SMS send failures SHALL NOT alter the client-visible response.

The reset code store SHALL cap verification attempts per challenge (e.g. 5) and then lock/invalidate the challenge. The `password-reset/request` endpoint SHALL be rate-limited per hashed `loginId` (reusing the `LoginThrottleService` pattern: Redis counter + TTL lock, key = SHA-256, never storing the raw identifier), returning a generic `429` when throttled (existing and non-existing accounts are throttled identically). A successful `verify` SHALL issue a short-lived, single-use `resetToken`; `password-reset/confirm` SHALL consume it exactly once (deleting it), persist the new bcrypt password (validated by `@ValidPassword`), and revoke all server sessions for that user.

#### Scenario: Reset request does not reveal account existence

- **WHEN** `POST /api/v1/auth/password-reset/request` is called for a non-existent `loginId`, an existing account without a phone, or an existing account with a phone
- **THEN** the server returns `200` with a structurally identical `{ challengeId, expiresAt }` body in all three cases, and sends an SMS only in the last case

#### Scenario: Verify returns a uniform 400 on any failure

- **WHEN** `POST /api/v1/auth/password-reset/verify` is called with a wrong code, an unknown or expired `challengeId`, or a dummy challenge for a non-existent account
- **THEN** the server returns the same `400 {"error": ...}` in every case, without revealing whether the account or a real code existed

#### Scenario: Verify caps attempts and locks the challenge

- **WHEN** `verify` is called with a wrong code more than the allowed number of attempts for one `challengeId`
- **THEN** the challenge is invalidated and all subsequent `verify` calls for it return `400`

#### Scenario: Successful verify yields a single-use reset token

- **WHEN** `verify` is called with the correct code for a live challenge of an existing account
- **THEN** the server returns `200` with `{ resetToken, expiresAt }`, and a subsequent replay of the same `resetToken` at `confirm` (after it is consumed) returns `400`

#### Scenario: Confirm sets the new password and revokes sessions

- **WHEN** `POST /api/v1/auth/password-reset/confirm` is called with a valid single-use `resetToken` and a policy-compliant `newPassword`
- **THEN** the server persists the new bcrypt password, deletes the `resetToken`, revokes all server sessions for that user, and returns `200`

#### Scenario: Confirm rejects invalid or reused reset token

- **WHEN** `confirm` is called with an expired, unknown, or already-consumed `resetToken`
- **THEN** the server returns `400 {"error": ...}` and does not change any password

#### Scenario: Reset secrets never appear in logs

- **WHEN** any step of the reset flow executes (including SMS send failures)
- **THEN** the verification code, reset token, `users.phone`, and RRN never appear in logs, audit records, or exception messages

---

