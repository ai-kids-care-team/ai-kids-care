# admin-management Specification

## Purpose
定义管理与审批能力：园级/平台级管理端点、注册审批流（仅 DIRECTOR/VICE_DIRECTOR 可批、不可自批）、成员停用与会话吊销、审计日志，响应最小化不泄露 S0/S1。
## Requirements
### Requirement: Schema carries REJECTED status and single-ACTIVE integrity constraints
The database `status_enum` SHALL include `REJECTED` as a distinct value (separate from `DISABLED`)
to represent a denied registration. `user_role_assignments` SHALL carry a partial unique index
`UNIQUE (user_id) WHERE status='ACTIVE'` and a CHECK
`(scope_type='PLATFORM' AND scope_id IS NULL) OR (scope_type='KINDERGARTEN' AND scope_id IS NOT NULL)`.
`user_kindergarten_memberships` SHALL carry a partial unique index
`UNIQUE (user_id) WHERE status='ACTIVE'`. These constraints are applied in Flyway migration
`V2__admin_audit_schema.sql`; `db/initdb/01_create_schema.sql` remains the V1 baseline and MUST NOT
include V2 changes.

#### Scenario: Approving a PENDING registration
- **WHEN** a PENDING user is approved and their status is atomically advanced to ACTIVE
- **THEN** the single-ACTIVE partial unique index prevents a second ACTIVE role assignment or
  membership row from being created for that user

#### Scenario: Duplicate approval attempt
- **WHEN** two concurrent requests attempt to approve the same PENDING registration
- **THEN** the conditional UPDATE's `WHERE status='PENDING'` clause affects 0 rows for the second
  request, and the DB constraint provides a secondary guarantee that no duplicate ACTIVE row exists

### Requirement: audit_logs supports platform-scope and anonymous events
The `audit_logs` table SHALL include a `scope_type` column (reusing the role-assignment scope enum),
`kindergarten_id` changed to nullable (with FK preserved), `user_id` changed to nullable, and
supplementary columns `effective_role`, `result`, and `correlation_id`. A CHECK constraint SHALL
enforce `(scope_type='PLATFORM' AND kindergarten_id IS NULL) OR (scope_type='KINDERGARTEN' AND kindergarten_id IS NOT NULL)`.
The system MUST NOT fabricate a `kindergarten_id` to represent a platform-scope event.

#### Scenario: Recording a platform-level audit event
- **WHEN** an audit event is written for a platform-scope action (e.g., SUPERADMIN approval)
- **THEN** `scope_type` is set to `PLATFORM`, `kindergarten_id` is NULL, and the CHECK constraint
  accepts the row

#### Scenario: Recording a login failure with unknown actor
- **WHEN** an audit event must be written for a login failure where the actor is not identified
- **THEN** `user_id` may be NULL and the row is still accepted

### Requirement: Kindergarten admin approval endpoints
An ACTIVE `KINDERGARTEN_ADMIN` whose `teachers.level` is `DIRECTOR` or `VICE_DIRECTOR` SHALL be
able to list, approve, and reject PENDING Guardian/Teacher/admin registrations scoped to their
own kindergarten via:
- `GET /api/v1/admin/kindergarten/registrations?status=PENDING`
- `POST /api/v1/admin/kindergarten/registrations/{userId}/approve`
- `POST /api/v1/admin/kindergarten/registrations/{userId}/reject`

Authorization, resource load, and state write MUST occur within a single `@Transactional` boundary
using conditional updates (`WHERE status='PENDING' AND scope_id=<kg>`) to eliminate TOCTOU. The
actor's `activeKindergartenId` MUST match the target's kindergarten. Self-approval (`context.userId()
== targetUserId`) MUST be rejected with `403`. A 0-row conditional update MUST result in a hidden
`404` (not `409`). Approval advances user + business profile + membership + role assignment atomically
to `ACTIVE`. Rejection advances to `REJECTED`. List responses MUST contain only minimal fields
(userId, requested role, level, submission timestamp) and MUST NOT expose S1 fields such as RRN or
contact details.

#### Scenario: Director approves a PENDING teacher in their kindergarten
- **WHEN** an ACTIVE `KINDERGARTEN_ADMIN` with `level=DIRECTOR` calls approve for a PENDING teacher
  in the same kindergarten
- **THEN** the response is `204`, and the teacher's user, business profile, membership, and role
  assignment are all `ACTIVE` in a single transaction

#### Scenario: Admin attempts to approve their own registration
- **WHEN** the caller's userId equals the target userId on an approve or reject request
- **THEN** the response is `403`

#### Scenario: Admin attempts to approve a registration from another kindergarten
- **WHEN** the caller's `activeKindergartenId` does not match the target registration's kindergarten
- **THEN** the response is a hidden `404`

#### Scenario: Non-admin role calls an approval endpoint
- **WHEN** a `TEACHER`, `GUARDIAN`, or unauthenticated caller accesses any kindergarten admin
  approval endpoint
- **THEN** the response is `403` for authenticated callers or `401` for unauthenticated callers

#### Scenario: Reject sets status to REJECTED
- **WHEN** an eligible kindergarten admin rejects a PENDING registration
- **THEN** the target's status becomes `REJECTED` and the account cannot log in

### Requirement: Kindergarten admin member disable endpoint
An ACTIVE `KINDERGARTEN_ADMIN` with `level=DIRECTOR` or `VICE_DIRECTOR` SHALL be able to disable
an ACTIVE member of their own kindergarten via
`POST /api/v1/admin/kindergarten/members/{userId}/disable`. The endpoint MUST atomically set the
target user, membership, and role assignment to `DISABLED` (recording `revoked_at`,
`revoked_by_user_id`, `left_at`) within a single transaction, then call
`SessionRevocationService.revokeAllForUser(targetUserId)` after transaction commit. Self-disable
MUST be rejected with `403`. Cross-kindergarten targets and non-ACTIVE targets MUST return a hidden
`404`.

#### Scenario: Director disables an active teacher in their kindergarten
- **WHEN** an eligible kindergarten admin calls disable for an ACTIVE teacher in their kindergarten
- **THEN** the teacher's user, membership, and role assignment become `DISABLED` in a single
  transaction, and all the teacher's sessions are revoked after commit

#### Scenario: Disabled user attempts to use a prior session
- **WHEN** a disabled user presents a previously valid session token
- **THEN** the session is invalid and the response is `401`

#### Scenario: Disable request targets a non-ACTIVE or cross-kindergarten member
- **WHEN** the target is DISABLED, PENDING, REJECTED, or belongs to a different kindergarten
- **THEN** the response is a hidden `404`

### Requirement: Platform admin approval and disable endpoints
An ACTIVE `PLATFORM_IT_ADMIN` SHALL be able to list, approve, reject, and disable PENDING or ACTIVE
`SUPERADMIN` accounts via:
- `GET /api/v1/admin/platform/superadmin-registrations?status=PENDING`
- `POST /api/v1/admin/platform/superadmin-registrations/{userId}/approve`
- `POST /api/v1/admin/platform/superadmin-registrations/{userId}/reject`
- `POST /api/v1/admin/platform/users/{userId}/disable`

Platform approval activates user + superadmin profile + `PLATFORM` role assignment (with
`scope_id IS NULL` per scope CHECK) in a single transaction. Platform disable advances to `DISABLED`
and calls `SessionRevocationService.revokeAllForUser` after commit. The 0-row conditional update
guard uses `scope_id IS NULL AND role=SUPERADMIN`. Self-approval and self-disable MUST be rejected
with `403`. Non-SUPERADMIN targets for the approval/reject endpoints MUST return a hidden `404`.
The `disable` endpoint is restricted to accounts with `role=SUPERADMIN`; disabling other
`PLATFORM_IT_ADMIN` accounts is out of scope. Platform approval MUST NOT grant kindergarten-level
or S1 data access.

#### Scenario: Platform IT admin approves a PENDING SUPERADMIN
- **WHEN** an ACTIVE `PLATFORM_IT_ADMIN` calls approve for a PENDING SUPERADMIN registration
- **THEN** the SUPERADMIN's user, superadmin profile, and PLATFORM role assignment (scope_id NULL)
  become `ACTIVE` in a single transaction

#### Scenario: Non-platform-admin calls a platform endpoint
- **WHEN** a `KINDERGARTEN_ADMIN`, `TEACHER`, `GUARDIAN`, or unauthenticated caller accesses any
  platform admin endpoint
- **THEN** the response is `403` or `401`

#### Scenario: Platform admin attempts to approve a non-SUPERADMIN target
- **WHEN** the target userId's role is not `SUPERADMIN`
- **THEN** the response is a hidden `404`

#### Scenario: Platform admin disables an active SUPERADMIN
- **WHEN** an eligible `PLATFORM_IT_ADMIN` calls disable for an ACTIVE SUPERADMIN
- **THEN** the target becomes `DISABLED` and all their sessions are revoked after transaction commit

### Requirement: Authorization actions and policy classes for admin endpoints
The authorization framework SHALL define six `AuthorizationAction` values:
`KINDERGARTEN_ADMIN_APPROVAL_READ`, `KINDERGARTEN_ADMIN_APPROVAL_WRITE`,
`KINDERGARTEN_ADMIN_MEMBER_WRITE`, `PLATFORM_SUPERADMIN_APPROVAL_READ`,
`PLATFORM_SUPERADMIN_APPROVAL_WRITE`, `PLATFORM_USER_WRITE`.
`KindergartenAdminPolicy` SHALL enforce same-kindergarten scope, level check
(`DIRECTOR`/`VICE_DIRECTOR`), and self-action prohibition inside the transaction.
`PlatformPolicy` SHALL enforce `PLATFORM_IT_ADMIN` role (via `scopeType==PLATFORM`) and
self-action prohibition. `@PreAuthorize` provides coarse role/capability gating; fine-grained
resource checks (same-kindergarten, level, target status) MUST be performed within
`KindergartenAdminPolicy`/`PlatformPolicy` inside the transaction per ADR-0019 §2.

#### Scenario: Coarse pre-authorize rejects wrong role before service is entered
- **WHEN** a `TEACHER` calls an endpoint annotated with `KINDERGARTEN_ADMIN_APPROVAL_WRITE`
- **THEN** `@PreAuthorize` rejects with `403` before the service method executes

#### Scenario: Fine-grained policy enforces level inside transaction
- **WHEN** an ACTIVE `KINDERGARTEN_ADMIN` with `level=STAFF` (not DIRECTOR/VICE_DIRECTOR) calls
  an approval endpoint
- **THEN** `KindergartenAdminPolicy` denies the request with `403` inside the transaction

### Requirement: Admin endpoints do not expose S0 or S1 fields
Admin list and detail responses MUST NOT include S0 fields (raw RRN, biometric source) or S1
fields such as full contact details. Minimal response fields are: `userId`, requested role, level,
and submission timestamp.

#### Scenario: List pending registrations response shape
- **WHEN** a kindergarten admin calls `GET /api/v1/admin/kindergarten/registrations?status=PENDING`
- **THEN** each item contains only `userId`, requested role, level, and submission timestamp — no
  RRN, phone number, or other S1 data

### Requirement: Audit hook points reserved in admin endpoints
Each admin write endpoint SHALL contain a reserved audit hook point (e.g., `TODO(SPEC-0002 #1)`)
that records actor, target, kindergarten/platform scope, and result. The audit writer is out of
scope for this capability and SHALL be connected when the audit-writer capability is implemented.

#### Scenario: Approve endpoint reaches audit hook point
- **WHEN** an approval or rejection action completes inside the transaction
- **THEN** the code reaches the reserved audit hook point; no audit write occurs until the writer
  is implemented
