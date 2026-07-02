## Why

The detection-event list endpoint (`GET /api/v1/detection-events`) accepts a `keyword` query parameter that the frontend already sends (`detectionEvents.api.ts` plumbs it end-to-end), but the backend silently discards it (`DetectionEventService.listDetectionEvents` has `// TODO: filter DetectionEvent by keyword` and never uses the param). This is a live trap: whoever wires a search box to the dashboard gets a control that appears to work but filters nothing. The repo already has the correct precedent (`AnnouncementService`/`AppreciationLetterService` implement the same pattern with tenant-scoped JPQL `LIKE`).

## What Changes

- Implement `keyword` filtering in `DetectionEventService.listDetectionEvents` via a tenant-scoped JPQL `LIKE` predicate, matching the existing `AnnouncementService` precedent.
- The `keyword` predicate MUST be `AND`-ed with the existing `kindergarten_id` tenant predicate **inside the same JPQL query** — no load-then-filter (preserves the multi-tenant isolation invariant).
- `keyword` matches human-meaningful, non-PII fields on the detection event: **camera name**, **room name**, and the **event-type enum value** (the Korean display label lives in frontend i18n after ADR-0013, so the backend matches the enum value, not the label).
- Blank/absent `keyword` preserves current behavior (no filter) — non-breaking.
- Consistency cleanup (no spec behavior change): `AiModelService.listAiModels` carries the same discarded-`keyword` TODO but is a headless endpoint with no frontend consumer; fix the TODO the same way (match model name/description) so the pattern is uniform, or leave a tracking note if out of scope. This is tracked in tasks, not as a capability requirement.

## Capabilities

### New Capabilities
<!-- none -->

### Modified Capabilities
- `ai-detection`: the detection-event read/list API gains a documented `keyword` search requirement (tenant-scoped `LIKE`, matches camera/room name + event-type enum value, blank = no filter).

## Impact

- **Backend**: `DetectionEventService.listDetectionEvents` (implement filter), `DetectionEventRepository` (new/extended tenant-scoped query supporting the `keyword` predicate), optionally `AiModelService.listAiModels` (consistency). New backend test asserting keyword filters within tenant scope and never crosses tenants.
- **Frontend**: none (`keyword` already wired; no contract change).
- **DB**: none (no schema change; matches existing indexed/queried columns).
- **API contract**: `GET /api/v1/detection-events?keyword=` behavior becomes real; response shape/pagination unchanged.
