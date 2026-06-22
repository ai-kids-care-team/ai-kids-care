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

The schema source artifacts SHALL stay aligned with the Flyway migrations, which are the source of
truth for the deployed schema. A capability test SHALL assert structural invariants against the
fully-migrated schema (Flyway V1..Vn applied to a PostgreSQL Testcontainer) and SHALL assert that
`db/initdb/01_create_schema.sql` remains a structural mirror of `V1__initial_baseline.sql`.
`db/dbml/schema.dbml` SHALL reflect the cumulative migrated schema (no stale pre-migration column
definitions).

#### Scenario: Migrated schema matches structural invariants

- **WHEN** the backend test suite runs Flyway migrations against a PostgreSQL Testcontainer
- **THEN** structural assertions hold — e.g. `push_subscriptions` exists and `device_tokens` / `device_platform_enum` do not after V7; `notifications.sent_at` and `fail_reason` are nullable after V3

#### Scenario: initdb stays a V1 mirror

- **WHEN** `db/initdb/01_create_schema.sql` or `V1__initial_baseline.sql` is edited and they diverge structurally
- **THEN** the schema-consistency test fails until the two are brought back into structural equivalence

#### Scenario: DBML drift is caught

- **WHEN** a Flyway migration changes a column's nullability/type but `db/dbml/schema.dbml` is not updated to match
- **THEN** the drift is surfaced (the dbml no longer reflects the cumulative migrated schema), to be reconciled before release
