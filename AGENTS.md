# AI Kids Care Agent Development Guide

Last reviewed: 2026-05-11

This document is the shared redevelopment guide for future Codex sessions working on this repository. It intentionally lives in `AGENTS.md` instead of the README files:

- README files remain user-facing project introduction and startup documentation.
- This file is engineering-facing and agent-facing: it records current implementation reality, known smells, redevelopment direction, parallel work boundaries, and acceptance criteria.
- Future agents should read this file before making changes, then inspect the relevant code paths again because the repository may have changed.

## Project Snapshot

AI Kids Care is a monorepo for a kindergarten safety management platform. The intended product loop is:

1. Manage kindergarten operational data: kindergartens, classes, rooms, children, guardians, teachers, cameras, and camera streams.
2. Run AI inference on CCTV video or live streams.
3. Persist AI-detected safety events as detection sessions, detection events, evidence files, reviews, and notifications.
4. Let staff review, resolve, dismiss, or escalate events through the frontend.
5. Communicate important information to guardians and staff through announcements, appreciation letters, notifications, Pushover, or SMS.

Repository layout:

- `frontend/`: Next.js 16, React 19, TypeScript UI, Redux Toolkit, Axios/RTK Query clients, CCTV dashboard, detection-event review pages, auth, announcements, appreciation letters.
- `backend/`: Java 21 Spring Boot 3.2 API server with Spring Security, Spring Data JPA, MapStruct, Springdoc OpenAPI, PostgreSQL, Neo4j driver.
- `ai/`: Python FastAPI serving, VideoMAE training/inference utilities, path/upload prediction API, live-stream alert experiment scripts.
- `db/`: PostgreSQL schema and seed SQL, DBML, Neo4j loading scripts and seed data.
- `docs/`: ERD diagrams and rendered database documentation.
- `scripts/codegen/`: PostgreSQL introspection and Java code generation templates.
- `jenkins/`, `Jenkinsfile`, `docker-compose.yml`: deployment and local orchestration assets.

Current state is not a greenfield project. Many modules exist, but the most important product loop is incomplete: AI inference, backend event persistence, and frontend event review are not fully integrated into one reliable flow.

## Current Implementation Map

### Backend

- Main API prefix is `/api/v1`.
- CRUD-style controllers and services exist for auth, users, kindergartens, classes, rooms, children, teachers, guardians, cameras, streams, AI models, detection sessions, detection events, evidence files, event reviews, notifications, announcements, appreciation letters, common codes, menus, and graph queries.
- JWT helper classes exist under `backend/src/main/java/com/ai_kids_care/v1/security/`.
- `SecurityConfig` currently configures CORS and stateless security, but all `/api/v1/**` routes are permitted and the JWT filter is commented out.
- Neo4j relationship graph support exists through `GraphController` / `GraphService`.
- Many generated or CRUD services still contain placeholder keyword filtering.
- Several auth endpoints still throw `Not implemented`.

Important backend areas to inspect before edits:

- `backend/src/main/java/com/ai_kids_care/v1/config/SecurityConfig.java`
- `backend/src/main/java/com/ai_kids_care/v1/security/JwtUtil.java`
- `backend/src/main/java/com/ai_kids_care/v1/security/JwtAuthenticationFilter.java`
- `backend/src/main/java/com/ai_kids_care/v1/controller/AuthController.java`
- `backend/src/main/java/com/ai_kids_care/v1/service/AuthService.java`
- `backend/src/main/java/com/ai_kids_care/v1/controller/DetectionEventController.java`
- `backend/src/main/java/com/ai_kids_care/v1/service/DetectionEventService.java`
- `backend/src/main/java/com/ai_kids_care/v1/controller/CameraStreamController.java`
- `backend/src/main/java/com/ai_kids_care/v1/vo/CameraStreamVO.java`

### Frontend

- Next.js App Router routes exist for home, signup, forgot/reset password, announcements, appreciation letters, CCTV, detection events, and letters.
- The CCTV dashboard is relatively feature-rich and attempts to render camera grids, stream iframes, event overlays, role-specific labels, and selected camera detail.
- Detection-event pages include list/detail/review flow and a child relationship graph modal backed by Neo4j data.
- API clients are split across `frontend/src/services/apis/`.
- Auth state is stored in Redux and mirrored in `localStorage`.
- Some pages include temporary fallback logic to compensate for backend/API gaps.

Important frontend areas to inspect before edits:

- `frontend/src/services/apis/apiClient.ts`
- `frontend/src/services/apis/base.api.ts`
- `frontend/src/services/apis/auth.api.ts`
- `frontend/src/services/apis/cctv.api.ts`
- `frontend/src/services/apis/detectionEvents.api.ts`
- `frontend/src/layout/AppLayout.tsx`
- `frontend/src/layout/ProtectedRoute.tsx`
- `frontend/src/layout/TopBar.tsx`
- `frontend/src/components/cctv/CctvDashboardPage.tsx`
- `frontend/src/components/detectionEvents/DetectionEventsListPage.tsx`
- `frontend/src/components/detectionEvents/DetectionEventsDetailPage.tsx`

### AI

- FastAPI serving exists in `ai/src/ai_app/serving/app.py`.
- Existing AI endpoints:
  - `GET /health`
  - `POST /predict/path`
  - `POST /predict/upload`
- `VideoPredictor` loads a VideoMAE model from `AI_MODEL_DIR`.
- `ai/docker-compose.yml` can run an independent `ai-inference` service on port `8001`.
- `ai/scripts/stream_live_alert_service.py` contains live-stream sliding-window inference, persistence logic, Pushover notification, optional SMS notification, and CSV output.
- The live-stream script is still experiment-like: it has hardcoded stream URL and Pushover user keys, writes CSV files, and does not persist detection events through the backend.

Important AI areas to inspect before edits:

- `ai/src/ai_app/serving/app.py`
- `ai/src/ai_app/serving/schemas.py`
- `ai/src/ai_app/serving/deps.py`
- `ai/src/ai_app/inference/predictor.py`
- `ai/src/ai_app/inference/pipeline.py`
- `ai/scripts/serve.py`
- `ai/scripts/stream_live_alert_service.py`
- `ai/docker-compose.yml`
- `ai/Dockerfile`

### Database And DevOps

- PostgreSQL schema and seed scripts live under `db/initdb/`.
- Neo4j data loader lives under `db/ne4j_kindergartens/`.
- Root `docker-compose.yml` starts PostgreSQL, Neo4j, Neo4j data loader, backend, and frontend.
- Root `docker-compose.yml` does not currently include the AI inference service.
- README documents the AI service as a separately runnable stack under `ai/`.

## Known Smells And Risks

These are based on current code inspection, not ideal architecture guesses.

### Security And JWT

- `SecurityConfig` permits all `/api/v1/**` requests, so backend business APIs are effectively public.
- `JwtAuthenticationFilter` exists but is not registered in the Spring Security filter chain.
- Access token and refresh token are generated by the same `JwtUtil.generateToken` method and use the same expiration configuration.
- There is no persisted refresh-token/session model, no token rotation, no token revocation, and no implemented logout behavior.
- JWT claims do not currently provide enough trusted server-side scope information for authorization decisions such as role, kindergarten scope, or token type.
- Frontend route protection is client-side only and cannot be treated as security.

### Auth Gaps

- `AuthController` still has multiple `Not implemented` paths: logout, change password, password reset completion, verification code creation, verification code checking.
- Frontend `auth.api.ts` calls routes such as `/auth/password/forgot`, `/auth/password/reset`, and `/auth/verification-codes/verify`, while backend exposes `/auth/password-resets` and `/auth/verification-codes/{challengeId}/verifications`.
- Validation annotations exist on some DTOs, but controllers do not consistently use `@Valid`.
- `AuthRegisterDTO` has role-dependent fields, but validation is not clearly modeled by role-specific request types or validation groups.

### Backend API And Data Contract Drift

- `CameraStreamVO` exposes `sourceUrl`, `sourceProtocol`, `playbackUrl`, and `playbackProtocol`, but frontend CCTV code still expects `streamUrl` and `protocol`.
- Frontend calls `/common_codes/code_group/detection_events`, but backend `CommonCodeController` exposes `/common_codes` with query parameters.
- Several services accept `keyword` but ignore it and return `findAll(pageable)`.
- Some endpoints require parameters too strictly for frontend usage, such as children list requiring `keyword`.
- Many CRUD endpoints accept raw IDs and DTOs without obvious authorization or scope checks.

### AI Integration

- AI inference is not part of the root compose stack.
- Backend does not currently provide a protected AI prediction proxy API.
- FastAPI prediction results are not automatically converted into `detection_sessions`, `detection_events`, `event_evidence_files`, or `event_reviews`.
- Realtime inference emits CSV and notifications, but not durable backend business records.
- Live-stream service includes hardcoded URL and notification recipients.
- Model files are expected under `AI_MODEL_DIR`, but the absence of model files is not surfaced in the frontend as a first-class operational state.

### Frontend Product Coverage

- Existing frontend does not expose most backend management capabilities.
- There are no complete admin/workbench pages for many README domains: kindergartens, classes, rooms, children, teachers, guardians, AI models, detection sessions, notification rules, device tokens, and evidence files.
- CCTV and detection-event pages contain useful but temporary fallback logic, including client-side role/session inference and hardcoded assumptions.
- Token storage is `localStorage` based; this is convenient for MVP but increases XSS impact and should be revisited during security hardening.
- Some imports/dependencies may be inconsistent. For example, `DetectionEventsDetailPage` imports `@react-three/drei`, while dependency availability should be verified before frontend build work.

### Testing And Build Verification

- Backend declares Spring Boot test dependencies, but meaningful security/API tests need to be added.
- Frontend has lint/build scripts, but local `node_modules` may not be installed in a fresh checkout.
- AI has no obvious test suite for serving schemas, predictor behavior, or realtime persistence logic.
- Prior compile/build checks may be blocked by local dependency or Gradle wrapper state; future agents should verify again instead of assuming current status.

## Redevelopment Direction

Default priority is MVP closed loop first.

### Priority 1: MVP Closed Loop

Goal: make the backend's AI/CCTV/event functionality visible and usable from the frontend.

Target flow:

1. A camera has a playable `camera_streams.playbackUrl`.
2. A detection session can be created or selected for a camera.
3. AI path/upload prediction can be invoked through the backend.
4. Prediction results can create or update detection events.
5. Events appear in the frontend event list and CCTV dashboard.
6. Staff can open an event detail page and move it through review states.

Minimum backend capabilities:

- Protected AI proxy endpoints.
- Event ingest endpoint for AI service.
- Correct camera stream response contract.
- Basic detection session/event creation flow.

Minimum frontend capabilities:

- CCTV stream list correctly reads backend `playbackUrl`.
- AI service health indicator.
- Manual path/upload prediction trigger for demo/MVP.
- Detection session/event visibility.
- Review status transition flow.

Minimum AI capabilities:

- FastAPI service included in root local compose or clearly callable by backend.
- Health response distinguishes model-ready from model-missing.
- Prediction response is stable and mappable to backend event fields.

### Priority 2: Security And Data Scope

Goal: make MVP behavior safe enough to continue development without masking security bugs.

Key requirements:

- Enable JWT filter.
- Require authentication for non-public APIs.
- Add role and kindergarten scope claims or lookup-backed authorization.
- Separate access and refresh token semantics.
- Implement logout and refresh token revocation/rotation.
- Remove client-side kindergarten inference once backend scope is available.
- Hide or remove sensitive fields from public VO responses.
- Add consistent 401/403/404/400 error response format.

### Priority 3: Operational Admin Expansion

Goal: expose more backend capabilities through usable frontend pages.

Add admin pages in this order:

1. Camera and stream configuration.
2. AI models and detection sessions.
3. Detection events, evidence files, and event reviews.
4. Notification rules and device tokens.
5. Kindergarten, class, room, child, guardian, teacher management.

Each new page should have:

- List view with pagination/filtering.
- Detail or edit view where the backend supports it.
- Clear loading/error/empty states.
- Role/scope-aware access.

## Public Interface Direction

Future work should converge toward these interfaces. Exact DTO names can follow backend conventions, but behavior should remain stable.

### Auth

- `POST /api/v1/auth/login`
  - Public.
  - Returns access token, refresh token, user id, login id, role, and scope summary.
- `POST /api/v1/auth/refresh`
  - Public but requires refresh token.
  - Rotates refresh token and returns new access/refresh token pair.
- `POST /api/v1/auth/logout`
  - Authenticated or refresh-token based.
  - Revokes current refresh token/session.
- `GET /api/v1/auth/me`
  - Authenticated.
  - Returns server-trusted user, role, scope type, kindergarten ids, and menu identity.

### AI Prediction Proxy

- `GET /api/v1/ai/health`
  - Authenticated for app users.
  - Proxies FastAPI health and returns backend-readable service status.
- `POST /api/v1/ai/predictions/path`
  - Authenticated.
  - Input includes `cameraId`, optional `sessionId`, `videoPath`, `topK`, and optional create-event flag.
  - Calls FastAPI `/predict/path`.
- `POST /api/v1/ai/predictions/upload`
  - Authenticated multipart endpoint.
  - Calls FastAPI `/predict/upload`.

### Event Ingest

- `POST /api/v1/detection_events/ingest`
  - Intended for backend-internal or AI-service calls, not general frontend users.
  - Requires internal service authentication or a tightly scoped token.
  - Creates or updates detection session/event/evidence records from realtime inference output.

### Camera Streams

- `GET /api/v1/camera_streams`
  - Supports `kindergartenId`, `cameraId`, `enabled`, `isPrimary`, pagination.
  - Frontend playback should use `playbackUrl`.
  - Source acquisition should use `sourceUrl`.
  - Password values must not be returned; expose only `hasPassword`.

### Common Codes

- `GET /api/v1/common_codes?codeGroup=&parentCode=&isActive=&page=&size=&sort=`
  - This should be the canonical frontend path.
  - Remove frontend calls to nonexistent shorthand routes unless backend intentionally adds them.

## Parallel Codex Workstreams

Use these workstreams to split future conversations. Avoid assigning two agents to the same files at the same time unless a coordinator is merging changes.

### Agent A: JWT And Backend Security

Goal:

- Turn backend auth from decorative token issuance into enforced API security.

Primary ownership:

- `backend/src/main/java/com/ai_kids_care/v1/config/SecurityConfig.java`
- `backend/src/main/java/com/ai_kids_care/v1/security/`
- `backend/src/main/java/com/ai_kids_care/v1/service/AuthService.java`
- `backend/src/main/java/com/ai_kids_care/v1/controller/AuthController.java`
- Auth DTO/VO classes as needed.
- Security tests under backend test source.

Boundaries:

- Do not redesign unrelated CRUD APIs.
- Do not change frontend UI except a small contract adjustment if required for login/me.
- Do not remove existing roles.

Acceptance:

- Public auth routes work without token.
- Non-public `/api/v1/**` routes reject missing/invalid access token with 401.
- Role/scope failures return 403.
- Access and refresh tokens have distinct type and expiration.
- Logout revokes refresh token or session.
- `/auth/me` returns server-trusted identity and scope.
- Backend tests cover login, protected route, refresh, logout, expired/invalid token behavior.

### Agent B: AI Service Productization And Backend AI Proxy

Goal:

- Make AI inference callable through the backend and visible to the application.

Primary ownership:

- `ai/src/ai_app/serving/`
- `ai/src/ai_app/inference/`
- `ai/scripts/serve.py`
- `ai/docker-compose.yml`
- `ai/Dockerfile`
- Backend AI client/proxy package to be created under `backend/src/main/java/com/ai_kids_care/v1/`.
- Root `docker-compose.yml` AI service addition.

Boundaries:

- Do not rewrite model training.
- Do not change detection-event persistence deeply; coordinate with Agent C for ingest.
- Do not commit large model files.

Acceptance:

- Root compose can include AI inference service with configurable `AI_MODEL_DIR`.
- Backend has configurable `ai.service.base-url`.
- Backend `GET /api/v1/ai/health` reports AI ready/model-missing/offline.
- Backend path/upload proxy endpoints call FastAPI and return stable JSON.
- AI service has tests or a mock mode that can run without real model files.

### Agent C: Realtime Inference Event Persistence

Goal:

- Convert realtime inference alerts into durable backend events.

Primary ownership:

- `ai/scripts/stream_live_alert_service.py`
- Shared AI realtime config/helpers under `ai/src/ai_app/` if created.
- Backend event ingest controller/service/DTOs.
- Detection session/event/evidence/review service integration.

Boundaries:

- Do not take over JWT/security infrastructure; consume whatever Agent A exposes.
- Do not redesign frontend event pages; coordinate with Agent D for display needs.
- Remove hardcoded secrets and recipients rather than preserving them.

Acceptance:

- Realtime script accepts stream URL, camera id, session id, thresholds, backend URL, and service token from env/CLI config.
- On alarm-on, it sends one backend ingest payload.
- Backend creates a detection event with correct kindergarten/camera/room/session/status/confidence/time fields.
- Optional evidence metadata is stored if provided.
- Duplicate or cooldown behavior is deterministic and documented.
- The event appears in existing detection-event list APIs.

### Agent D: Frontend CCTV And Detection Events Contract Fixes

Goal:

- Make existing CCTV and detection-event frontend accurately consume backend contracts.

Primary ownership:

- `frontend/src/services/apis/cctv.api.ts`
- `frontend/src/services/apis/detectionEvents.api.ts`
- `frontend/src/services/apis/commonCodes.api.ts`
- `frontend/src/components/cctv/CctvDashboardPage.tsx`
- `frontend/src/components/detectionEvents/`
- Related frontend types under `frontend/src/types/`.

Boundaries:

- Do not build large new admin modules; this workstream fixes current MVP screens.
- Do not implement backend security.
- Do not keep client-side kindergarten inference once `/auth/me` or token scope is available.

Acceptance:

- CCTV stream playback uses `playbackUrl`, not nonexistent `streamUrl`.
- Camera stream detail labels use `sourceProtocol/playbackProtocol`.
- Detection event type labels use existing `/common_codes` query contract.
- Events render from backend VO fields without fallback schema confusion.
- Existing event status update flow works against backend API.
- Frontend build/lint passes after dependency issues are resolved.

### Agent E: Operational Admin Frontend Expansion

Goal:

- Add missing frontend surfaces so backend capabilities are visible.

Primary ownership:

- New pages under `frontend/src/app/`.
- New feature components under `frontend/src/components/`.
- New API clients/types under `frontend/src/services/apis/` and `frontend/src/types/`.
- Menu integration if required.

Boundaries:

- Start after Agent A and D stabilize auth and API contracts.
- Prefer existing UI components in `frontend/src/components/shared/ui/`.
- Do not introduce a separate design system.

Acceptance:

- Initial admin pages exist for camera streams, AI models, detection sessions, notification rules, and core kindergarten operations.
- Each page supports list, loading, error, empty state, and basic create/update when backend supports it.
- Routes are menu-accessible for appropriate roles.
- Pages do not expose actions that backend authorization rejects for the current user.

### Agent F: Tests, Build, And Compose Verification

Goal:

- Make the repo verifiable and safe for parallel development.

Primary ownership:

- Backend tests.
- Frontend lint/build setup and smoke tests.
- AI tests.
- Docker compose verification docs/scripts.
- CI/Jenkins adjustments if needed.

Boundaries:

- Do not make unrelated feature changes to force tests through.
- Coordinate with feature agents when tests reveal real bugs.

Acceptance:

- Backend `compileJava` and tests run in a clean environment.
- Frontend install, lint, and build are documented and reproducible.
- AI service tests can run without GPU/model through mocks.
- Compose smoke test confirms db, neo4j, backend, frontend, and AI health.
- A concise verification checklist is added or updated for future agents.

## Cross-Agent Coordination Rules

- Always inspect the current file before editing; this document may be stale.
- Do not revert unrelated user or agent changes.
- Keep changes scoped to the assigned workstream.
- If two workstreams need the same file, one agent should own the edit and the other should document requirements.
- Prefer existing repository patterns over new frameworks.
- Preserve README user-facing content unless explicitly assigned to documentation work.
- Do not commit model weights, secrets, generated local caches, or large runtime outputs.
- For frontend changes, use existing shared UI primitives and icons where possible.
- For backend changes, add tests when changing security, persistence, or API contracts.
- For AI changes, separate deterministic core logic from scripts so it can be tested.

## Acceptance Criteria For The MVP Closed Loop

The MVP redevelopment is considered complete when all of the following are true:

- Login returns usable access and refresh tokens.
- Protected backend APIs reject missing or invalid access tokens.
- User role and kindergarten scope come from backend-trusted state, not frontend inference.
- Camera stream configuration can be listed and displayed in the CCTV dashboard.
- CCTV playback uses backend `playbackUrl`.
- AI service health is visible through the backend.
- AI path/upload prediction can be invoked through a protected backend endpoint.
- A prediction can create a detection event, either manually or through realtime ingest.
- Realtime inference can persist an alarm event into backend tables.
- Persisted events appear in the frontend detection-event list and CCTV dashboard.
- Event detail page can move an event through review status transitions.
- Docker compose can run PostgreSQL, Neo4j, backend, frontend, and AI for a local smoke test.
- README startup instructions and this AGENTS.md guide do not contradict each other.

## Suggested First Development Order

1. Fix `camera_streams` and `common_codes` frontend/backend contract mismatches so existing CCTV screens show real backend data.
2. Add backend AI health and prediction proxy with mock-friendly behavior.
3. Add event ingest and map prediction results into detection events.
4. Enable JWT protection and add `/auth/me`, then remove frontend scope inference.
5. Productize realtime inference configuration and event persistence.
6. Expand frontend admin pages around cameras, streams, AI models, sessions, and notifications.
7. Add tests and compose smoke verification around the full loop.

## Verification Checklist For Future Agents

Before finishing a workstream, run the relevant subset and report results:

- Backend: `.\gradlew.bat compileJava` and relevant tests from `backend/`.
- Frontend: `npm run lint` and `npm run build` from `frontend/`.
- AI: serving tests or a direct `GET /health` smoke test from `ai/`.
- Compose: root stack health when the work touches services or environment variables.
- Manual UI smoke: login, CCTV dashboard, detection-event list, detection-event detail, and any new page touched.

If any verification cannot run, state the exact blocker and whether it is environment-related or code-related.
