# Tasks — detection-event-keyword-search

## 1. Backend: detection-event keyword filter
- [x] 1.1 Add a keyword-aware tenant-scoped query to `DetectionEventRepository` (explicit `@Query`, keep `@EntityGraph({kindergarten, cctvCameras, rooms})`), matching `cctvCameras.cameraName` + `rooms.name` + event-type enum value, with the `:keyword is null or blank → no filter` short-circuit and tenant predicate AND-ed outside the keyword OR-group (per design D1). — `searchByKindergarten(...)`.
- [x] 1.2 Verify the `cast(eventType as string)` HQL works against the Postgres `event_type_enum` custom type via a real test; if not, fall back to resolving matching `EventTypeEnum` values in Java and passing an `in` list. — Verified cast/`is empty` unsupported (Hibernate 6 SemanticException against the custom enum type); used the sanctioned `IN :eventTypes` fallback resolved in `DetectionEventService.matchingEventTypes`.
- [x] 1.3 Wire `DetectionEventService.listDetectionEvents` to call the new query with the `keyword` param (remove the `// TODO`); preserve the exact existing ordering/pagination (design D2). Also normalizes whitespace-only keyword → null (bug caught in TDD).
- [x] 1.4 Confirm no controller/DTO/pagination contract change (`keyword` param already accepted; response shape unchanged).

## 2. Backend: tests
- [x] 2.1 Integration test (testcontainers): `DetectionEventKeywordSearchTest` — within-tenant filter + case-insensitivity, blank keyword = full unfiltered tenant list, cross-tenant keyword returns empty for the caller.
- [x] 2.2 Run full suite green — `BUILD SUCCESSFUL`, 333 tests, 0 failures (native Java 21 + Docker; DooD not needed on this box).

## 3. Consistency cleanup (headless, no spec behavior)
- [x] 3.1 `AiModelService.listAiModels`: implemented `AiModelRepository.searchAll` LIKE on model **name** (AiModel has no `description` column — adding one would be a schema change, out of scope; documented as an intentional scope limit). TODO removed.

## 4. Frontend
- [x] 4.1 None — `keyword` already plumbed end-to-end (`detectionEvents.api.ts`). Confirmed no change needed.

## 5. Docs / archive readiness
- [ ] 5.1 On completion, ensure the `ai-detection` delta spec (keyword search requirement) is ready to sync/archive via `/opsx:archive`. — Implementation complete + suite green; ready for maintainer review → archive.
