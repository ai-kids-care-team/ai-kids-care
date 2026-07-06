# ai-detection Specification (delta)

## ADDED Requirements

### Requirement: Internal stream credential reads and claims are audited

Reading a decrypted camera stream credential and claiming a stream lease via the internal AI-service endpoints SHALL emit a security audit record (`S1_EVIDENCE_READ`) capturing the calling deployment id, stream id, and outcome. The audit record MUST NOT contain the plaintext credential, the shared service token, or any PII. This provides forensic visibility for the accepted shared-token blast-radius risk (defense-in-depth for OQ-3=B).

#### Scenario: Credential decrypt is audited

- **WHEN** an authenticated AI service reads a stream credential via the internal endpoint
- **THEN** an `S1_EVIDENCE_READ` audit record is written with the deployment id, stream id, and outcome, and without the plaintext password

#### Scenario: Stream claim is audited

- **WHEN** an AI deployment claims a stream lease it did not previously hold
- **THEN** an `S1_EVIDENCE_READ` audit record is written for the newly claimed stream

### Requirement: Claim capacity is bounded

The internal stream claim request SHALL bound `capacity` with both a lower (`@Min(0)`) and an upper (`@Max`) limit so a single call cannot claim an unbounded number of streams. A request exceeding the upper bound MUST be rejected with HTTP 400 by request validation before any lease is claimed.

#### Scenario: Over-capacity claim is rejected

- **WHEN** an AI deployment submits a claim request whose `capacity` exceeds the configured upper bound
- **THEN** the request is rejected with HTTP 400 and no lease is claimed

### Requirement: AI-internal stream methods are separated from tenant CRUD

The AI-internal stream methods (credential read, active-stream listing, claim) SHALL reside in a dedicated internal service class separate from the tenant-scoped `@PreAuthorize` CRUD service, so that session-authorized tenant methods and AI-internal methods (authorized only at the HTTP layer via `hasRole("AI_SERVICE")`) are not co-located. The HTTP-layer authorization and the wire contract SHALL remain unchanged by this separation.

#### Scenario: AI-internal methods live in an internal service

- **WHEN** the codebase exposes internal stream credential, active-list, or claim operations
- **THEN** they are implemented in a dedicated internal service class, not alongside tenant `@PreAuthorize` CRUD methods

#### Scenario: Wire contract preserved after separation

- **WHEN** an AI deployment calls the credential, active-streams, or claim endpoints after the refactor
- **THEN** the request/response shape and `ROLE_AI_SERVICE` Bearer authorization are identical to before
