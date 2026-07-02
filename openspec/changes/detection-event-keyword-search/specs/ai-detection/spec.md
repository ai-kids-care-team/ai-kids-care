## ADDED Requirements

### Requirement: Detection event list keyword search

The detection-event list API `GET /api/v1/detection-events` SHALL support an optional `keyword` query parameter that filters the returned events by a case-insensitive substring match. The `keyword` predicate MUST be expressed inside the same tenant-scoped JPQL/SQL query as the existing `kindergarten_id` predicate (composed with `AND`); the backend MUST NOT load a tenant's events and filter in application code afterwards. The match MUST cover only non-PII, human-meaningful fields associated with the event: the source **camera name**, the **room name**, and the **event-type enum value** (the Korean display label is frontend i18n per ADR-0013 and is therefore not matchable server-side). A blank or absent `keyword` MUST preserve the current unfiltered behavior. The keyword filter MUST NOT widen tenant scope: results remain restricted to the caller's active kindergarten, and no keyword value can surface another kindergarten's events. Pagination/response shape is unchanged.

#### Scenario: Staff searches own kindergarten's events by keyword

- **WHEN** an authenticated `KINDERGARTEN_ADMIN` or `TEACHER` calls `GET /api/v1/detection-events?keyword=<term>`
- **THEN** the backend returns only that kindergarten's detection events whose camera name, room name, or event-type enum value contains `<term>` (case-insensitive), most-recent-first, scoped to the caller's active kindergarten without the caller supplying a kindergarten id

#### Scenario: Blank keyword is a no-op filter

- **WHEN** the caller omits `keyword` or passes a blank/whitespace value
- **THEN** the backend returns the same unfiltered, tenant-scoped, most-recent-first list it returns today (no behavior change)

#### Scenario: Keyword cannot cross tenant boundary

- **WHEN** a staff member of kindergarten A passes a `keyword` that matches events belonging to kindergarten B
- **THEN** the backend returns no kindergarten B events; the keyword predicate is AND-ed with the caller's active-kindergarten predicate inside the query, so foreign-tenant rows are never candidates
