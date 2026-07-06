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

