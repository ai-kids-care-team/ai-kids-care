## ADDED Requirements

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

## MODIFIED Requirements

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
