# kindergarten-operations Specification (delta)

## ADDED Requirements

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
