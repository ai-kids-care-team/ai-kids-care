## ADDED Requirements

### Requirement: Mapper output completeness is guarded against silent drops (INC-005)

MapStruct mappers SHALL NOT silently drop an unmapped target property. Every `@Mapper` SHALL set
`unmappedTargetPolicy = ReportingPolicy.ERROR`, so that a target field with no source mapping and
no explicit `@Mapping(target = ..., ignore = true)` fails compilation (and therefore fails the
`./gradlew test` gate). This guard SHALL be enforced via the per-mapper annotation, NOT via a
global `mapstruct.unmappedTargetPolicy=ERROR` build argument (the prior harness-style override,
which is disallowed by the testing-and-ci capability).

#### Scenario: New unmapped target fails the build

- **WHEN** a developer adds a field to a mapped target type without a corresponding source mapping or an explicit ignore
- **THEN** compilation fails with a MapStruct unmapped-target error and the backend test gate does not pass

#### Scenario: Intentionally unset targets are explicit

- **WHEN** a target field is intentionally left unmapped (e.g. `id`, `createdAt`, relationship entities, server-derived status)
- **THEN** it is declared with `@Mapping(target = ..., ignore = true)` rather than relying on a silent default

---

### Requirement: Schema source artifacts are guarded against drift

The schema source artifacts SHALL stay aligned with the Flyway migrations (V1..Vn), which are the
source of truth for the deployed schema. A capability test SHALL assert structural invariants
against the fully-migrated schema (initdb baseline + V2..Vn applied to a PostgreSQL Testcontainer)
so that a migration regression leaving the live schema in the wrong terminal shape is caught. `db/dbml/schema.dbml` SHALL reflect the cumulative migrated
schema and SHALL be reconciled whenever a migration changes a column/type/constraint (verified at
review); `db/initdb/01_create_schema.sql` is the demo/CI seed baseline and need not be a strict
textual mirror of `V1__initial_baseline.sql` — the migrations are written idempotently
(`IF NOT EXISTS` / `DROP NOT NULL`) so the fresh-V1 and initdb+baseline paths converge to the same
terminal schema.

#### Scenario: Migrated schema matches structural invariants

- **WHEN** the backend test suite runs Flyway migrations against a PostgreSQL Testcontainer
- **THEN** structural assertions hold — e.g. `push_subscriptions` exists and `device_tokens` / `device_platform_enum` do not after V7; `notifications.sent_at` and `fail_reason` are nullable after V3; `children`/`guardians`/`teachers` have `rrn_hash` NOT NULL and no `rrn_encrypted` after V4–V6

#### Scenario: Migration regression is caught

- **WHEN** a change to a migration would leave the terminal schema in the wrong shape (missing table, wrong nullability, dropped guard)
- **THEN** the schema-consistency test fails before the change can merge through the backend test gate

#### Scenario: DBML is reconciled when a migration changes the schema

- **WHEN** a Flyway migration changes a column's nullability/type/constraint
- **THEN** `db/dbml/schema.dbml` is updated in the same change to reflect the new terminal schema (kept aligned by process and review, since the DBML is the DB-first design source)
