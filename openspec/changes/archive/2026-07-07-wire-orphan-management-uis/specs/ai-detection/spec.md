## ADDED Requirements

### Requirement: Platform admin manages the AI model catalog via UI

Platform IT admins (and superadmins) SHALL be able to list, create, edit, and deactivate AI model catalog entries — limited to metadata (`name` / `version` / `status`) — through a navigation-reachable UI wired to the existing `/api/v1/ai_models` endpoints, gated by `PLATFORM_METADATA_READ` / `PLATFORM_METADATA_WRITE`. The UI SHALL manage catalog metadata only; it SHALL NOT trigger model training, upload weights, or bind a model into the live detection pipeline. No backend endpoint or contract is added or changed by this requirement.

#### Scenario: Platform admin sees a model management entry

- **WHEN** a `PLATFORM_IT_ADMIN` (or `SUPERADMIN`) views the navigation
- **THEN** an AI model management entry is present and links to the model management page

#### Scenario: Model catalog is listed from the existing endpoint

- **WHEN** the platform admin opens the model management page
- **THEN** it lists existing entries via `GET /api/v1/ai_models` (session cookie + tenant-agnostic platform scope)

#### Scenario: Create / edit / deactivate call the existing write endpoints with CSRF

- **WHEN** the platform admin creates, edits, or deactivates a model entry
- **THEN** the corresponding `POST` / `PUT` / `DELETE /api/v1/ai_models` request is sent with the `X-XSRF-TOKEN` header and the list refreshes on success

#### Scenario: Non-platform roles have no entry

- **WHEN** a role without platform metadata authority views the navigation
- **THEN** no model management entry is shown, and the backend authorization gate continues to reject direct calls
