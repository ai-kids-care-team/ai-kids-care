# sensitive-data-handling Specification

## Purpose
定义敏感数据处理能力：RRN 单向哈希（HMAC，列名 rrn_encrypted 为历史误名）与 pepper 版本管理、摄像头流口令 AES-256-GCM（仅 Java 侧持钥）加密、S0/S1 敏感值与存储表示不得出现在公开 DTO/VO/OpenAPI schema。
## Requirements
### Requirement: RRN storage is one-way HMAC-SHA-256

RRN (주민등록번호) back-7 digits SHALL be stored as a one-way HMAC-SHA-256 hash using a pepper. The database column `rrn_hash` (formerly misnamed `rrn_encrypted` — the old name is historically misleading; the value has never been reversible encryption) MUST contain `Base64URL-nopad(HMAC-SHA-256(key=pepper, msg=rrn_first6‖rrn_back7))`. The earlier BCrypt algorithm (ADR-0010 baseline) is superseded by HMAC-SHA-256 + pepper per ADR-0024. Raw RRN MUST NOT be stored; reversible encryption of RRN is permanently rejected.

#### Scenario: Guardian registration stores HMAC hash

- **WHEN** a guardian submits registration with `rrnFirst6` and `rrnBack7`
- **THEN** `AuthService` calls `RrnHashUtil.hash(pepper, first6, back7)` and writes the result to `rrn_hash`; `rrn_encrypted` no longer exists (dropped by V6); no plaintext or BCrypt value is written

#### Scenario: Child lookup uses HMAC equality

- **WHEN** `ChildrenService.getChildEntityByRRN(rrnFirst6, rrnBack7)` is called
- **THEN** the service computes `h = RrnHashUtil.hash(pepper, first6, back7)` and executes `repository.findByRrnHash(h)`; a single index lookup returns the matching child without BCrypt candidate-set iteration

#### Scenario: Duplicate RRN is rejected by unique constraint

- **WHEN** a second registration attempt supplies a RRN that already exists in the `children`, `guardians`, or `teachers` table
- **THEN** the `UNIQUE` constraint on `rrn_hash` rejects the insert and the caller receives an error; no duplicate enrollment is created

---

### Requirement: rrn_first6 is stored as plaintext for search

`rrn_first6` (birth date prefix, YYMMDD) SHALL be stored as plaintext in all three tables (`children`, `guardians`, `teachers`). Its exposure of birth date is an accepted product tradeoff documented in ADR-0010.

#### Scenario: Child candidate set filtered by rrn_first6

- **WHEN** the system needs to locate a child by RRN
- **THEN** `rrn_first6` is available as a plain-value column for indexed lookup; the system does NOT use it alone as a unique identifier

#### Scenario: rrn_first6 absent from public API responses

- **WHEN** any public REST endpoint returns child, guardian, or teacher data
- **THEN** `rrnFirst6` MUST NOT appear in any response VO or OpenAPI schema unless explicitly authorized for a specific detail or verification flow with documented business necessity

---

### Requirement: Pepper is injected via environment variable with no default

The RRN hash pepper SHALL be injected as `RRN_HASH_PEPPER` environment variable with no default value (`application.yml` references `${RRN_HASH_PEPPER}` bare, fail-fast). The pepper MUST be stored separately from database backups/snapshots per ADR-0010 security model. A fixed non-secret test pepper MUST be set in `backend/src/test/resources/application-test.yml` so the Spring context starts in tests.

#### Scenario: Application fails fast when pepper is missing

- **WHEN** the backend starts without `RRN_HASH_PEPPER` set in the environment
- **THEN** the application context fails to initialize and refuses to start; no request is served without a valid pepper

#### Scenario: Test profile uses fixed non-secret pepper

- **WHEN** backend tests run with the `test` Spring profile
- **THEN** a fixed, non-secret test pepper is present so `RrnHashUtil` can be invoked; tests do not require a production secret

---

### Requirement: rrn_hash_key_version column tracks pepper version

Per ADR-0025, each of the three tables (`children`, `guardians`, `teachers`) SHALL have an `rrn_hash_key_version` column that records which pepper version produced the stored hash. This version MUST NOT appear in any public API response VO or OpenAPI schema.

#### Scenario: New hash written with current key version

- **WHEN** `RrnHashUtil.hash(...)` writes a new `rrn_hash` row
- **THEN** `rrn_hash_key_version` is set to the current version (e.g., `v1`); both columns are written atomically

#### Scenario: rrn_hash_key_version absent from all public responses

- **WHEN** any public REST endpoint returns child, guardian, or teacher data
- **THEN** `rrn_hash_key_version` MUST NOT appear in the response body or in any published OpenAPI schema

#### Scenario: Lazy re-hash on read during pepper rotation

- **WHEN** `ChildrenService.getChildEntityByRRN` finds a child whose `rrn_hash_key_version` is not the current version
- **THEN** the service re-computes the hash with the current pepper and updates `rrn_hash` and `rrn_hash_key_version` in the same transaction, while the old pepper remains active until all rows are migrated

---

### Requirement: pepper rotation requires hard gate before retiring old pepper

Per ADR-0025, an old pepper version SHALL NOT be retired until `SELECT count(*) FROM children/guardians/teachers WHERE rrn_hash_key_version <> :targetVersion` equals zero for all three tables. Retiring a pepper before this count reaches zero is permanently forbidden.

#### Scenario: Hard gate prevents premature pepper retirement

- **WHEN** an operator attempts to remove an old pepper version from the active pepper map
- **THEN** the system or runbook check confirms that no rows in any of the three tables still reference the old version; retirement is blocked until count is zero

---

### Requirement: S0 data absent from all public DTO, VO, and OpenAPI schemas

S0 fields — `passwordHash`, `rrnEncrypted` (old column, now dropped), `rrnHash`, camera stream ciphertext (`streamPasswordCiphertext`), camera IV (`streamPasswordIv`), camera key version (`streamPasswordKeyVersion`), complete `pushToken`, and internal `storageUri` — SHALL NOT appear in any public response VO, Create/Update DTO, or published OpenAPI schema. Service internals MAY hold these values; they MUST NOT cross the REST boundary.

#### Scenario: OpenAPI schema contains no S0 fields

- **WHEN** the backend publishes its OpenAPI spec at `/v3/api-docs`
- **THEN** none of `passwordHash`, `rrnEncrypted`, `rrnHash`, `streamPasswordCiphertext`, `streamPasswordIv`, `streamPasswordKeyVersion`, `pushToken`, `storageUri` appear in any response schema

#### Scenario: Create/Update DTOs reject S0 write fields

- **WHEN** a client submits a Create or Update request body containing `passwordHash`, `rrnHash`, or any camera ciphertext field
- **THEN** the backend ignores or rejects those fields; they are never written to the database via a public endpoint

#### Scenario: S1 rrnFirst6 absent from list responses

- **WHEN** a list endpoint returns multiple child, guardian, or teacher records
- **THEN** `rrnFirst6` MUST NOT appear in any element of the list response

---

### Requirement: Camera stream password is encrypted at rest with AES-256-GCM via Java backend only

Per ADR-0026, camera stream passwords SHALL be encrypted by the Java backend using `AesGcmCryptoUtil` (AES-256-GCM, 12-byte SecureRandom IV, 128-bit auth tag, Base64). The ciphertext is stored in `stream_password_ciphertext`, IV in `stream_password_iv`, and key version in `stream_password_key_version`. The Python AI service MUST NOT hold the AES key, MUST NOT connect to the database for credentials, and MUST NOT perform decryption. The AES key is injected as `CAMERA_STREAM_AES_KEY_V1` environment variable, only available to the Java process.

#### Scenario: Write path encrypts password before persisting

- **WHEN** an authorized `KINDERGARTEN_ADMIN` submits a camera stream write request containing `streamPassword`
- **THEN** `CameraStreamService` calls `AesGcmCryptoUtil.encrypt(plainPassword, activeKey)` and writes `stream_password_ciphertext`, `stream_password_iv`, and `stream_password_key_version`; the plaintext is never persisted

#### Scenario: Public CameraStreamVO contains only hasPassword

- **WHEN** any public endpoint returns camera stream data
- **THEN** the response MUST NOT contain `streamPasswordCiphertext`, `streamPasswordIv`, `streamPasswordKeyVersion`, `sourceUrl`, or `streamUser`; only `hasPassword` (boolean) is permitted to indicate credential presence

#### Scenario: Credentials endpoint hidden from OpenAPI

- **WHEN** the backend publishes its OpenAPI spec
- **THEN** `GET /api/v1/internal/streams/{id}/credentials` is annotated `@Hidden` and does NOT appear in the published OpenAPI document

#### Scenario: AI service retrieves credentials via authenticated internal endpoint

- **WHEN** the Python AI service needs RTSP credentials for a stream
- **THEN** it calls `GET /api/v1/internal/streams/{id}/credentials` with `Authorization: Bearer <AI_SERVICE_TOKEN>`; the Java backend decrypts and returns `StreamCredentialDTO { streamId, sourceUrl, streamUser, streamPassword }` over the internal HTTP channel; the AES key is never sent to Python

#### Scenario: Unauthenticated access to credentials endpoint is rejected

- **WHEN** a request reaches `GET /api/v1/internal/streams/{id}/credentials` without a valid `AI_SERVICE_TOKEN` Bearer header
- **THEN** the `AiServiceTokenAuthenticationFilter` returns `401`; no credential is returned

---

### Requirement: S0 and S1 classification governs all data handling

Data is classified into four levels. S0 (Secret/Credential) and S1 (Restricted PII/Safety Evidence) MUST receive the strictest controls. S0 fields SHALL never appear in generic API responses, lists, logs, or audit detail. S1 fields SHALL only be accessible to authorized roles with a verified resource relationship, with minimum necessary fields returned.

#### Scenario: S0 absent from error responses and logs

- **WHEN** the backend returns any error response (400, 401, 403, 404, 500) or writes any log line
- **THEN** the response body and log entry MUST NOT contain any S0 value including `passwordHash`, `rrnHash`, camera ciphertext, `pushToken`, session IDs, or DB/Redis secrets

#### Scenario: S1 fields restricted to authorized access

- **WHEN** a `GUARDIAN` accesses child data for a child with whom they have an ACTIVE relationship
- **THEN** only minimum necessary fields are returned (e.g., `GuardianChildVO` with `childId`, `name`, `status`); birth date, address, and `rrnFirst6` are excluded from list and standard detail responses
