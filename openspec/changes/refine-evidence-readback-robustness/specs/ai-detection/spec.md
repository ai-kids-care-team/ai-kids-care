## MODIFIED Requirements

### Requirement: Detection evidence read-back for staff review

A staff reviewer SHALL be able to view the AI-captured evidence (image frame or video clip) attached
to a detection event during review. Evidence bytes are stored in an internal object store (MinIO);
the backend — never the browser — reads them, so the object store is not exposed publicly. Evidence
access is restricted to staff of the event's own kindergarten: every read is authenticated per
request and tenant-scoped by `kindergarten_id` (joined through `event_evidence_files → detection_events`),
GUARDIAN users have no evidence access, and cross-tenant or non-existent references are hidden (404,
no existence disclosure). No schema migration is introduced — the existing `event_evidence_files`
table (populated by the AI ingest write path) is the source, and `storage_uri` holds the object key.
The `available` flag reflects whether the backend can **currently retrieve** the bytes: a row is
`available` only when its object is confirmed present in the store at list time; legacy or
not-yet-uploaded rows whose `storage_uri` is a local `file://` path, and rows whose object is missing
from the store, are both reported as unavailable (not an error), since the AI-side upload to the
object store is a separate real-stream evolution.

#### Scenario: Staff lists a reviewed event's evidence

- **WHEN** a staff user of the event's kindergarten requests the evidence for a detection event
- **THEN** the response lists each evidence file's metadata (`type`, `mimeType`, `hash`, `createdAt`)
  plus a backend `contentPath` and an `available` flag, scoped to the caller's tenant
- **AND** for each non-`file://` row the backend confirms the object's presence in the store (a
  metadata-only existence check, no byte transfer) so `available = true` means the object was just
  found; a missing object yields `available = false` and `contentPath = null`

#### Scenario: Staff streams evidence content through the backend

- **WHEN** a staff user requests an evidence file's content endpoint
- **THEN** the backend fetches the object from the internal object store and streams the bytes back
  with the file's `mimeType` (supporting HTTP Range for video seeking), without exposing the object
  store to the browser
- **AND** the `ETag` validator is sent as an RFC 7232 quoted-string (`"<hash>"`)

#### Scenario: Unsatisfiable Range request returns 416

- **WHEN** a staff user requests an evidence file's content with a `Range` that cannot be satisfied
  (e.g. a start offset at or beyond the object length)
- **THEN** the backend responds `416 Range Not Satisfiable` with `Content-Range: bytes */<length>`
  and an empty body, rather than falling back to a full `200` response
- **AND** a satisfiable `Range` still yields `206 Partial Content` and a request with no `Range`
  still yields a full `200`

#### Scenario: Guardian or cross-tenant access is denied

- **WHEN** a GUARDIAN user, or a user of a different kindergarten, requests evidence for an event
- **THEN** the request is denied as a hidden 404 (no evidence metadata or bytes are returned, and the
  event's existence is not disclosed)

#### Scenario: Legacy file:// or missing-object evidence is reported unavailable

- **WHEN** an evidence row's `storage_uri` is a local `file://` path (AI-local, not uploaded to the
  object store), or its object is absent from the store
- **THEN** the evidence is reported as `available = false` (metadata still listed) and its content
  endpoint returns 404, rather than raising an error; `file://` rows are judged unavailable without
  issuing a store request

#### Scenario: Event with no evidence

- **WHEN** a staff user requests evidence for an event that has no `event_evidence_files` rows
- **THEN** the response is a 200 with an empty list (not a 404)
