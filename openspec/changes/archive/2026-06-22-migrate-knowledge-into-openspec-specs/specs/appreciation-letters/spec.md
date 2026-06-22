## ADDED Requirements

### Requirement: Appreciation letters API is published at a fixed path
The backend SHALL publish appreciation letter endpoints at `/api/v1/appreciation_letters` (GET list,
GET `/{id}`, POST, PUT `/{id}`, DELETE `/{id}`).

#### Scenario: List endpoint is reachable
- **WHEN** an authenticated GUARDIAN sends `GET /api/v1/appreciation_letters`
- **THEN** the server returns HTTP 200 with a paginated list of letters visible to that caller

#### Scenario: Detail endpoint is reachable
- **WHEN** an authenticated user sends `GET /api/v1/appreciation_letters/{id}` for a letter they may see
- **THEN** the server returns HTTP 200 with the letter VO

### Requirement: Sender identity and tenant are always server-derived
The server SHALL derive `senderUserId` and `kindergartenId` exclusively from the authenticated
session (`EffectiveAuthorizationContext`). Any client-supplied `senderUserId`, `kindergartenId`, or
`status` field MUST be silently ignored.

#### Scenario: Client attempts to forge sender
- **WHEN** a GUARDIAN submits a POST body that includes a `senderUserId` belonging to another user
- **THEN** the letter is created with `senderUserId` set to the authenticated caller, not the supplied value

#### Scenario: Client attempts to forge tenant
- **WHEN** a GUARDIAN submits a POST body that includes a `kindergartenId` belonging to another tenant
- **THEN** the letter is created with `kindergartenId` set to the caller's own tenant, not the supplied value

### Requirement: Create DTO contains no identity fields
`AppreciationLetterCreateDTO` SHALL contain exactly `targetType` (enum string), `targetId` (Long),
`title` (NotBlank), `content` (NotBlank), and `isPublic` (Boolean). It MUST NOT contain
`senderUserId`, `kindergartenId`, or `status`.

#### Scenario: CreateDTO schema is published in OpenAPI
- **WHEN** the OpenAPI document is generated
- **THEN** the component `AppreciationLetterCreateDTO` exists and its schema does not include
  `senderUserId`, `kindergartenId`, or `status` properties

### Requirement: Update DTO is limited to mutable fields
`AppreciationLetterUpdateDTO` SHALL contain exactly `title`, `content`, and `isPublic`. It MUST NOT
contain `senderUserId`, `kindergartenId`, `targetType`, `targetId`, or `status`.

#### Scenario: UpdateDTO schema is published in OpenAPI
- **WHEN** the OpenAPI document is generated
- **THEN** the component `AppreciationLetterUpdateDTO` exists and contains only `title`, `content`,
  and `isPublic`

### Requirement: GUARDIAN may create letters targeting TEACHER or KINDERGARTEN within the same tenant
A GUARDIAN SHALL be permitted to POST a new appreciation letter where `targetType` is `TEACHER` or
`KINDERGARTEN` and `targetId` identifies an entity in the same kindergarten as the sender. Any
`targetId` that does not exist or belongs to a different tenant MUST be rejected with 400 or hidden
404.

#### Scenario: GUARDIAN creates a valid letter
- **WHEN** a GUARDIAN POSTs `{targetType: "TEACHER", targetId: <same-tenant teacher id>, title: "감사합니다", content: "...", isPublic: true}`
- **THEN** the server returns HTTP 201 and the letter is persisted with server-derived `senderUserId` and `kindergartenId`

#### Scenario: GUARDIAN targets a cross-tenant entity
- **WHEN** a GUARDIAN POSTs a letter with `targetId` belonging to a different tenant
- **THEN** the server returns 400 or hidden 404

### Requirement: GUARDIAN may only update or delete their own letters
A GUARDIAN SHALL be permitted to PUT or DELETE only letters where `senderUserId` equals their own
user ID. Any attempt to modify another user's letter MUST return hidden 404 and record an audit
event.

#### Scenario: Author updates their own letter
- **WHEN** a GUARDIAN sends `PUT /api/v1/appreciation_letters/{id}` for a letter they authored
- **THEN** the server applies changes to `title`, `content`, and `isPublic` and returns HTTP 200

#### Scenario: Non-author attempts update
- **WHEN** a GUARDIAN sends `PUT /api/v1/appreciation_letters/{id}` for a letter authored by another user
- **THEN** the server returns HTTP 404 (hidden) and records an audit event

### Requirement: Delete is a soft delete setting status to DISABLED
`DELETE /api/v1/appreciation_letters/{id}` SHALL set the letter's `status` to `DISABLED` and retain
the physical row. The `status` field MUST NOT appear in public read responses.

#### Scenario: Author deletes their letter
- **WHEN** a GUARDIAN sends `DELETE /api/v1/appreciation_letters/{id}` for a letter they authored
- **THEN** the server returns HTTP 204, the row is retained, and `status` is set to `DISABLED`

#### Scenario: Deleted letter is not visible in list
- **WHEN** any user retrieves the letter list after the letter has been soft-deleted
- **THEN** the deleted letter does not appear in the results

### Requirement: Read visibility is role-scoped within the tenant
Read access SHALL follow these rules enforced by Repository SQL:
- GUARDIAN: letters they authored plus same-tenant letters where `is_public = true`
- TEACHER: same-tenant letters where `is_public = true` plus letters addressed to that teacher (`target_type = TEACHER` AND `target_id` = own user ID)
- KINDERGARTEN_ADMIN: all letters in own tenant
- SUPERADMIN / PLATFORM_IT_ADMIN: no business-content access

Any letter not visible to the caller MUST be indistinguishable from a non-existent letter (hidden 404) and MUST trigger an audit event.

#### Scenario: GUARDIAN reads a public letter from a peer
- **WHEN** a GUARDIAN requests a letter authored by another GUARDIAN in the same tenant with `is_public = true`
- **THEN** the server returns HTTP 200

#### Scenario: GUARDIAN is denied a private letter from a peer
- **WHEN** a GUARDIAN requests a letter authored by another GUARDIAN in the same tenant with `is_public = false` where they are not the author
- **THEN** the server returns HTTP 404

#### Scenario: TEACHER reads a letter addressed to them
- **WHEN** a TEACHER requests a letter where `target_type = TEACHER` and `target_id` equals their own user ID
- **THEN** the server returns HTTP 200

#### Scenario: KINDERGARTEN_ADMIN reads all tenant letters
- **WHEN** a KINDERGARTEN_ADMIN lists letters for their tenant
- **THEN** all non-deleted letters in that tenant are returned

#### Scenario: Cross-tenant read is hidden
- **WHEN** any authenticated user requests a letter that belongs to a different tenant
- **THEN** the server returns HTTP 404

#### Scenario: Unauthenticated access is rejected
- **WHEN** an unauthenticated request is sent to any appreciation letters endpoint
- **THEN** the server returns HTTP 401

### Requirement: Response VO exposes no raw internal identifiers
`AppreciationLetterVO` MUST NOT include `senderUserId`, `kindergartenId`, or raw `targetId` as
top-level fields. It SHALL expose `senderName` (resolved display name) and `targetName` (resolved
display name) in place of the raw IDs. `status` MUST NOT appear in public responses.

#### Scenario: VO field set is locked in contract test
- **WHEN** `PublishedOpenApiContractTest` runs
- **THEN** the `AppreciationLetterVO` component schema does not include `senderUserId`,
  `kindergartenId`, or `status`

### Requirement: Response VO includes an editable flag derived server-side
`AppreciationLetterVO` SHALL include a boolean field `editable` that is `true` if and only if the
authenticated caller is the author of the letter. This field MUST be derived server-side and MUST
be included in the locked VO field set of `PublishedOpenApiContractTest`.

#### Scenario: Author receives editable=true
- **WHEN** a GUARDIAN retrieves a letter they authored
- **THEN** the response VO contains `editable: true`

#### Scenario: Non-author receives editable=false
- **WHEN** any user who is not the author retrieves a letter
- **THEN** the response VO contains `editable: false`

### Requirement: List endpoint supports pagination and keyword filtering
`GET /api/v1/appreciation_letters` SHALL accept pagination parameters and an optional `keyword`
parameter. When `keyword` is supplied the server SHALL filter within the caller's visible set,
matching against `title` and `content`.

#### Scenario: Keyword filters on title and content
- **WHEN** a GUARDIAN sends `GET /api/v1/appreciation_letters?keyword=감사` 
- **THEN** only letters visible to that caller whose `title` or `content` contains `감사` are returned

### Requirement: Contract guard tests are flipped to positive assertions
`SensitiveWriteContractTest` and `PublishedOpenApiContractTest` SHALL assert presence (not absence)
of `AppreciationLetterCreateDTO`, `AppreciationLetterUpdateDTO`, the three Service write methods,
the two Mapper methods, and the two API paths. All five previously-absent assertions MUST be
replaced with positive existence assertions.

#### Scenario: Contract tests pass after implementation
- **WHEN** `SensitiveWriteContractTest` and `PublishedOpenApiContractTest` are executed
- **THEN** all assertions related to appreciation letters pass green
