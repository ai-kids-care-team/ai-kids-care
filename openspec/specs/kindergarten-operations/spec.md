# kindergarten-operations Specification

## Purpose
TBD - created by archiving change director-operations-ui. Update Purpose after archive.
## Requirements
### Requirement: Director can manage classes, rooms, and camera streams in the product UI

A kindergarten administrator (`KINDERGARTEN_ADMIN` / 원장) SHALL be able to create, view, update, and (for classes and rooms) delete their kindergarten's operational structure — classes, rooms, and camera streams — through the product UI, wired to the existing tenant-scoped backend endpoints. The UI SHALL NOT send a kindergarten id (tenant is derived server-side), SHALL filter list views via the server-side `keyword` parameter rather than client-side filtering, and SHALL surface a not-found state (not a distinct 403) for cross-tenant or invisible resources. Camera stream passwords SHALL be write-only in the UI (submitted, never echoed back).

#### Scenario: Director lists and searches classes

- **WHEN** a `KINDERGARTEN_ADMIN` opens the class management page and enters a keyword
- **THEN** the UI calls `GET /api/v1/classes?keyword=<term>` with pagination and renders only that kindergarten's matching classes, without the client supplying a kindergarten id

#### Scenario: Director creates and edits a room

- **WHEN** a `KINDERGARTEN_ADMIN` submits the create/edit room form
- **THEN** the UI calls `POST /api/v1/rooms` or `PUT /api/v1/rooms/{id}` and reflects the created/updated room, showing the server error message on validation failure

#### Scenario: Director registers a camera stream without echoing the password

- **WHEN** a `KINDERGARTEN_ADMIN` creates or edits a camera stream with a stream password
- **THEN** the UI submits the password to `POST/PUT /api/v1/camera_streams` and never displays the stored password back (the VO does not return plaintext)

#### Scenario: Operations menu is restricted to the director role

- **WHEN** a non-`KINDERGARTEN_ADMIN` user views the navigation
- **THEN** the operations-management entries (classes / rooms / camera streams) are not shown, and the backend `@PreAuthorize` remains the authoritative gate

### Requirement: Camera list endpoints derive tenant from session, not a client parameter

The camera stream and CCTV camera list endpoints (`GET /api/v1/camera_streams`, `GET /api/v1/cctv_cameras`) SHALL derive the tenant from the session's active kindergarten (ThreadLocal), and the frontend SHALL NOT send a `kindergartenId` parameter to them. The endpoints SHALL continue to return only the caller's active kindergarten's rows regardless of any client-supplied value.

#### Scenario: Camera list without a client kindergarten id

- **WHEN** a `KINDERGARTEN_ADMIN` requests the camera stream or CCTV camera list without supplying a kindergarten id
- **THEN** the backend returns that kindergarten's rows, scoped by the session's active kindergarten

### Requirement: Camera stream type and protocol enums are served from the enum endpoint

The `camera_stream_type` and `protocol` enums SHALL be resolvable via `GET /api/v1/enums/{name}`, and the frontend camera stream form SHALL source its type/protocol options from that endpoint rather than hardcoding them, preserving the single-source-of-truth for enum values (labels remain frontend i18n).

#### Scenario: Frontend fetches camera enum options from the enum endpoint

- **WHEN** the camera stream management form renders its stream-type and protocol selectors
- **THEN** the options are fetched from `GET /api/v1/enums/camera_stream_type` and `GET /api/v1/enums/protocol`, with a static fallback only if the fetch fails

#### Scenario: Enum endpoint returns camera enums in declaration order

- **WHEN** `GET /api/v1/enums/camera_stream_type` or `GET /api/v1/enums/protocol` is called
- **THEN** it returns the enum constant names in declaration order, consistent with the DB and backend `type.*` definitions

