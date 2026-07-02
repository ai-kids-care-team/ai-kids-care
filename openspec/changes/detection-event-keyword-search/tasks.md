# Tasks — detection-event-keyword-search

## 1. Backend: detection-event keyword filter
- [ ] 1.1 Add a keyword-aware tenant-scoped query to `DetectionEventRepository` (explicit `@Query`, keep `@EntityGraph({kindergarten, cctvCameras, rooms})`), matching `cctvCameras.cameraName` + `rooms.name` + event-type enum value, with the `:keyword is null or blank → no filter` short-circuit and tenant predicate AND-ed outside the keyword OR-group (per design D1).
- [ ] 1.2 Verify the `cast(eventType as string)` HQL works against the Postgres `event_type_enum` custom type via a real test; if not, fall back to resolving matching `EventTypeEnum` values in Java and passing an `in` list (still one query, no row load).
- [ ] 1.3 Wire `DetectionEventService.listDetectionEvents` to call the new query with the `keyword` param (remove the `// TODO`); preserve the exact existing ordering/pagination (design D2).
- [ ] 1.4 Confirm no controller/DTO/pagination contract change (`keyword` param already accepted; response shape unchanged).

## 2. Backend: tests
- [ ] 2.1 Integration test (testcontainers): two kindergartens, events with camera/room/eventType containing a shared term; assert keyword filters WITHIN tenant scope, blank keyword = full unfiltered tenant list, and a term matching the other tenant's data returns nothing (cross-tenant scenario from the spec).
- [ ] 2.2 Run `./gradlew test` (DooD per repo convention) green.

## 3. Consistency cleanup (headless, no spec behavior)
- [ ] 3.1 `AiModelService.listAiModels`: implement the same `LIKE` on model name/description (scoped as the endpoint already is), or if non-trivial leave a tracking comment instead of an inert discard. No frontend consumer exists, so no UI work.

## 4. Frontend
- [ ] 4.1 None — `keyword` already plumbed end-to-end (`detectionEvents.api.ts`). Confirm no change needed.

## 5. Docs / archive readiness
- [ ] 5.1 On completion, ensure the `ai-detection` delta spec (keyword search requirement) is ready to sync/archive via `/opsx:archive`.
