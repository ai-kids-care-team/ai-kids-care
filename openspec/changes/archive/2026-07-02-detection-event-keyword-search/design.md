## Context

`GET /api/v1/detection-events` list read is served by `DetectionEventService.listDetectionEvents` → `DetectionEventRepository.findByKindergarten_Id(kindergartenId, pageable)` (an `@EntityGraph`-fetched derived query). The service method receives `keyword` but discards it (`// TODO`). The frontend already sends `keyword` (`detectionEvents.api.ts`). Tenant scope comes from `EffectiveAuthorizationContextHolder.requireActiveKindergartenId()` — the caller never passes a kindergarten id.

The repo's canonical keyword pattern is `AnnouncementRepository.listActiveAnnouncements`:
```jpql
where (:keyword is null or :keyword = '' or a.title like concat('%', :keyword, '%') or a.body like concat('%', :keyword, '%'))
```

## Goals / Non-Goals

**Goals**
- Make `keyword` actually filter, tenant-scoped, inside one query (no load-then-filter).
- Match camera name + room name + event-type enum value (non-PII, human-meaningful).
- Blank/absent keyword = current behavior (non-breaking).

**Non-Goals**
- No frontend change (already wired). No pagination/response-shape change. No new index (matches columns already joined by the existing `@EntityGraph`). No Korean-label matching (labels are frontend i18n; backend has only the enum value).

## Decisions

### D1 — New explicit `@Query` instead of a derived method name
The three match fields span two joined to-one relations (`cctvCameras.cameraName`, `rooms.name`) plus the entity's own `eventType` enum, with the `blank → no-op` short-circuit. That is not expressible as a Spring-Data derived method name, so add an explicit `@Query` on `DetectionEventRepository` mirroring the `AnnouncementRepository` precedent. Keep the existing `findByKindergarten_Id` (used elsewhere) and add a keyword-aware sibling, or replace the service's call site with the new query — the new query with a blank keyword returns the identical set, so the service can call the keyword query unconditionally.

Proposed query (keeps the `@EntityGraph` so the N+1 fix is preserved):
```jpql
@EntityGraph(attributePaths = {"kindergarten", "cctvCameras", "rooms"})
@Query("""
    select d from DetectionEvent d
    where d.kindergarten.id = :kindergartenId
      and (:keyword is null or :keyword = ''
           or lower(d.cctvCameras.cameraName) like lower(concat('%', :keyword, '%'))
           or lower(d.rooms.name)            like lower(concat('%', :keyword, '%'))
           or lower(cast(d.eventType as string)) like lower(concat('%', :keyword, '%')))
    """)
Page<DetectionEvent> searchByKindergarten(@Param("kindergartenId") Long kindergartenId,
                                          @Param("keyword") String keyword, Pageable pageable);
```
- **Tenant predicate is `d.kindergarten.id = :kindergartenId`, AND-ed first** — the keyword OR-group is fully parenthesized so it can never widen tenant scope (satisfies the spec's cross-tenant scenario).
- `cast(d.eventType as string)` matches the enum **value** (e.g. `ASSAULT`), not the Korean label. Confirm the Hibernate cast works with the `event_type_enum` custom column type; if the custom enum type rejects `cast`, fall back to comparing against a bound set of enum values whose name contains the keyword (resolve in Java to a `List<EventTypeEnum>` and pass as an `in` param — still one query, still no row load).
- `lower(...)` on both sides = case-insensitive (Announcement precedent is case-sensitive; detection search benefits from case-insensitive — a small, intentional improvement, documented in the spec as "case-insensitive").
- Sort: preserve the existing most-recent-first ordering (via `Pageable` sort or an explicit `order by d.id desc` matching current behavior — verify what `findByKindergarten_Id` currently orders by and keep it identical).

### D2 — Ordering / pagination parity
Whatever ordering the current `findByKindergarten_Id` call produces (Pageable-driven or default) MUST be preserved so the only observable change is the filter. Verify in the service/controller how the `Pageable`/sort is built and reuse it.

### D3 — `ai_models` consistency (headless, no spec impact)
`AiModelService.listAiModels` has the identical discarded-`keyword` TODO but no frontend consumer and no capability spec of its own. Fix it the same way (tenant/platform-scoped `LIKE` on model name + description) for pattern uniformity, in the same change. This is a tasks-level cleanup, not an `ai-detection` requirement; if the enum/scoping for ai_models turns out non-trivial, leave the TODO with a tracking comment rather than expanding scope.

## Risks / Trade-offs

- **`cast(enum as string)` portability** — Postgres custom `event_type_enum` type may not accept an HQL `cast`. Mitigation in D1 (resolve matching enum values in Java, pass as `in`). The implementer MUST verify via a real test, not assume.
- **Join fan-out** — matching on `cctvCameras`/`rooms` uses the same to-one joins already fetched by `@EntityGraph`; no extra query, no to-many fan-out, `Pageable` stays safe.

## Verification

- New backend integration test (testcontainers): seed two kindergartens each with events whose camera/room/eventType contain a shared term; assert (a) keyword filters within tenant, (b) a term matching tenant B's data returns nothing for a tenant-A caller, (c) blank keyword returns the full tenant-A list unchanged. Run `./gradlew test` (DooD per repo convention).
