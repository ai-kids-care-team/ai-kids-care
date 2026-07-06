# data-platform Specification

## Purpose
定义数据平台能力：PostgreSQL 为唯一 system-of-record + Neo4j 派生图视图、loader 不得投影 S0/PII（INC-003）、DB-first schema（schema.dbml + ddl-auto=validate）与 Flyway 迁移、按 kindergarten_id 复合键的租户隔离、字典表治理（不得扩展）。
## Requirements
### Requirement: PostgreSQL is the sole system of record

PostgreSQL 16 SHALL be the sole system of record (SoR) for all business data. Neo4j 5.19 SHALL
be a derived, read-only view whose data originates from PostgreSQL. Neo4j MUST NOT be treated as
an authoritative source for any business entity; writes to Neo4j are performed only by the
data-loader, not by any application service.

#### Scenario: Business write reaches PostgreSQL

- **WHEN** any application service (backend or AI subsystem) writes a business entity (user,
  child, detection event, etc.)
- **THEN** the write is made directly to PostgreSQL; Neo4j is not updated in the same
  transaction and is not involved in write-path consistency

#### Scenario: Graph query reads from Neo4j only

- **WHEN** the backend resolves a child-centric multi-hop relationship query (e.g. Child →
  Class → Teacher → Kindergarten → Guardian)
- **THEN** the query is executed against Neo4j via `GraphRepository` (native Cypher); it does
  not JOIN PostgreSQL tables for the graph traversal

---

### Requirement: Neo4j graph model is a minimal projection of PostgreSQL

The Neo4j graph SHALL contain only the node labels and relationship types required for
child-centric multi-hop relationship queries and front-end graph visualization via reagraph.
Node properties SHALL be limited to what is needed for display and traversal; no additional
columns from PostgreSQL MUST be projected.

#### Scenario: Graph nodes contain relationship-query fields only

- **WHEN** the data-loader writes a `Child`, `Class`, `Teacher`, `Kindergarten`, or `Guardian`
  node
- **THEN** each node contains only the properties required for graph traversal and display (e.g.
  `child_id`, `name`, `kindergarten_id`, `status`, `enroll_date`, `leave_date` for
  Child); S0/PII fields are absent (SEC-12: `gender`/성별 is treated as PII and is NOT projected)

#### Scenario: Graph relationship edges carry required attributes

- **WHEN** the data-loader builds the graph relationships from PostgreSQL (`load_graph.py`)
- **THEN** the following relationships are established with the stated properties:
  `(Class)-[:HAS_CHILD]->(Child)`,
  `(Teacher)-[:HAS_CLASS]->(Class)`,
  `(Kindergarten)-[:HAS_TEACHER]->(Teacher)`,
  `(Child)-[:HAS_GUARDIAN]->(Guardian)` (with `relationship`, `is_primary`, `priority`
  attributes on the guardian edge)

---

### Requirement: Neo4j loader MUST NOT project S0 or PII fields into the graph (INC-003)

The data-loader in `db/ne4j_kindergartens/` MUST NOT write any S0 or S1/PII fields into Neo4j
node properties. Specifically, the following fields SHALL be absent from all graph nodes:
`password_hash`, `rrn_hash`, `rrn_first6`, `rrn_encrypted`, `birth_date`, `address`, `email`,
`phone`, `emergency_contact_name`, `emergency_contact_phone`, `contact_phone`, `contact_email`,
`stream_password_encrypted`, `stream_password_ciphertext`.

The loader sources graph data by querying PostgreSQL (the system of record) directly. For each
node label it SHALL `SELECT` only an explicit allowlist of non-PII columns, so that PII columns
never enter the loader's row data and therefore cannot be bound into any Cypher write (allowlist
at the source is the primary INC-003 control, not a post-hoc scrub). This allowlist control SHALL
apply identically to **both** the full-rebuild (bootstrap) path **and** the incremental
watermark-fetch path — the incremental `SELECT` adds only a non-PII watermark predicate to the
`WHERE` clause and reads no additional column. Any loader-internal sync-state stored in Neo4j (e.g. a
high-water-mark meta-node) SHALL carry only non-sensitive bookkeeping properties (table name,
watermark timestamp) and no PII. As defense-in-depth, a scrub script (`no000_scrub_sensitive.py`)
SHALL run before the load to `REMOVE` any sensitive attributes that may remain on pre-existing
nodes from a prior run. The static guard `LoaderPiiProjectionGuardTest` SHALL continue to scan the
loader's Python source and assert that no forbidden field is bound into a node property.

#### Scenario: Loader selects only non-PII columns from PostgreSQL

- **WHEN** the data-loader builds a `Child`, `Teacher`, `User`, `Kindergarten`, `Class`, or
  `Guardian` node from PostgreSQL (full rebuild or incremental upsert)
- **THEN** the SQL query `SELECT`s only that label's allowlisted non-PII columns (e.g. for `Child`:
  `child_id`, `kindergarten_id`, `name`, `child_no`, `enroll_date`, `leave_date`,
  `status`, `created_at`, `updated_at`), so PII columns such as `rrn_first6`, `rrn_encrypted`,
  `rrn_hash`, `birth_date`, `address`, `gender`, `emergency_contact_*`, `email`, `phone`,
  `password_hash`, `contact_*` are never read into the loader and never written to the graph

#### Scenario: Incremental fetch reuses the same non-PII allowlist

- **WHEN** an incremental tick fetches rows changed since the high-water mark
  (`SELECT ... FROM <table> WHERE <watermark_expr> >= :watermark`)
- **THEN** the selected column set is exactly that label's non-PII allowlist (the watermark predicate
  is expressed over already-allowlisted non-PII columns such as `updated_at`, or
  `granted_at`/`revoked_at` for `user_role_assignments`); no forbidden S0/PII column is added to the
  incremental query

#### Scenario: Graph nodes contain no PII property after a load

- **WHEN** the loader completes a tick and `MATCH (n) UNWIND keys(n) AS k RETURN DISTINCT k` is run
  against Neo4j (including any sync-state meta-node)
- **THEN** the returned property-key set contains none of the forbidden S0/PII field names

#### Scenario: Static guard detects a forbidden binding

- **WHEN** `LoaderPiiProjectionGuardTest` scans the `db/ne4j_kindergartens/*.py` loader source
- **THEN** it finds at least one loader file and reports zero bindings of a forbidden field into a
  graph node property (`SET node.<field> = $param` or a `{ <field>: $param }` map entry)

#### Scenario: Scrub script removes previously projected sensitive attributes

- **WHEN** `no000_scrub_sensitive.py` is executed against a Neo4j instance that has existing nodes
  from a prior loader run
- **THEN** `REMOVE` Cypher statements strip `password_hash`, `email`, `phone` from `User`;
  `address`, `contact_phone`, `contact_email` from `Kindergarten`; `rrn_encrypted`, `rrn_first6`,
  `emergency_contact_phone`, `emergency_contact_name` from `Teacher`; `rrn_first6`, `rrn_encrypted`,
  `birth_date`, `address` from `Child`; `rrn_encrypted`, `rrn_first6`, `address` from `Guardian`

---

### Requirement: Neo4j sync keeps the derived graph current via incremental sync from PostgreSQL

The data-loader (`db/ne4j_kindergartens/`) SHALL keep the Neo4j derived graph continuously
converged with PostgreSQL via an **incremental sync**, so that business writes committed to
PostgreSQL are reflected in the graph within a bounded polling interval **without a manual
re-run**. The loader SHALL run as a **long-lived process** (Compose `restart: unless-stopped`)
that, on each tick, fetches rows changed since a per-table high-water mark
(`WHERE updated_at >= :watermark`, or an equivalent non-PII change expression such as
`GREATEST(granted_at, COALESCE(revoked_at, granted_at))` for tables without `updated_at`) and
`MERGE`-upserts them into Neo4j using the existing non-PII column allowlist and node/relationship
Cypher. The loader SHALL remain the **sole writer** of Neo4j (no application service writes the
graph); PostgreSQL remains the system of record and Neo4j remains a read-only derived view.

The one-shot **full rebuild** (`MATCH (n) DETACH DELETE n` then load) is retained as the
**bootstrap / recovery path**: when the graph is empty or no high-water mark exists, the loader
performs a full rebuild and initializes the high-water marks to `max(updated_at)` per table;
steady state is incremental upsert (no full clear, so an online graph read never sees an empty
graph). The defense-in-depth scrub (`no000_scrub_sensitive.py`) SHALL still run before the load.

#### Scenario: Steady-state incremental upsert from PostgreSQL

- **WHEN** the loader is running in steady state and a tick executes
- **THEN** for each source table it `SELECT`s only the allowlisted non-PII columns for rows changed
  since `:watermark`, `MERGE`-upserts the corresponding nodes/relationships into Neo4j, and advances
  that table's high-water mark to the batch max; it does NOT clear the whole graph and does NOT read
  any `db/ne4j_kindergartens/data/*.csv` file

#### Scenario: Bootstrap full rebuild on an empty graph

- **WHEN** the loader starts and finds the graph empty or no stored high-water mark
- **THEN** it performs a one-shot full rebuild (`MATCH (n) DETACH DELETE n`, recreate constraints,
  reload all nodes/relationships from PostgreSQL via the non-PII allowlist) and initializes each
  table's high-water mark to `max(updated_at)`, then enters steady-state incremental sync

#### Scenario: Graph converges to PostgreSQL after a later write

- **WHEN** a business write (e.g. a new child enrollment or a class reassignment) is committed to
  PostgreSQL after the loader has started
- **THEN** the change is picked up by a subsequent incremental tick and the Neo4j graph converges
  to reflect it within the configured polling interval — no manual loader re-run is required

#### Scenario: Loader runs as a long-lived Compose service

- **WHEN** Docker Compose starts the data-loader service
- **THEN** the service has `restart: unless-stopped`, depends on PostgreSQL being healthy and Neo4j
  being healthy, runs the scrub once, then loops (incremental tick + sleep) for the lifetime of the
  container rather than exiting after a single load

#### Scenario: Loader fails loudly and retries when PostgreSQL is unreachable

- **WHEN** the loader cannot reach PostgreSQL or a required query errors during a tick
- **THEN** the affected table's high-water mark is NOT advanced and the error is surfaced (the tick
  does not silently advance past unread rows); the loader retries on the next tick rather than
  silently producing a partial or stale graph

---

### Requirement: Incremental sync propagates deletes and tolerates missed updates

The incremental sync SHALL converge the derived graph to PostgreSQL including **deletions**, which
the watermark-by-`updated_at` scan alone cannot observe. Soft deletes (a row whose `status`
transitions to an inactive value) SHALL be carried by the normal incremental upsert (the bumped
`updated_at` is picked up and the node's `status` is updated, the node remaining in the graph
consistent with current behavior). Hard deletes (physical row removal) and relationship-row
removal SHALL be reconciled by a periodic **full id reconcile**: the loader fetches the live id set
per table from PostgreSQL (`SELECT <id>` only) and runs
`MATCH (n:<Label>) WHERE NOT n.<idKey> IN $liveIds DETACH DELETE n` (and the analogous edge
reconcile) so that entities deleted from PostgreSQL do not linger as orphan nodes/edges. The sync
SHALL be **idempotent and self-healing**: `MERGE` upserts make re-applying overlapping rows safe,
and the watermark boundary uses `>=` so that rows written within the same timestamp tick across a
restart are not skipped. The per-table high-water mark SHALL be **persisted** (e.g. a Neo4j
`(:_GraphSyncState {table, watermark})` meta-node) so the loader resumes from the correct position
after a restart without re-scanning the entire source or doing an unnecessary full rebuild.

#### Scenario: Hard delete is reconciled away as an orphan

- **WHEN** a row is physically deleted from PostgreSQL (e.g. a `child_guardian_relationships` row or
  a `children` row) and a reconcile pass runs
- **THEN** the loader's live-id reconcile removes the corresponding orphan node/edge from Neo4j
  (`DETACH DELETE`), and the per-label node count matches the PostgreSQL `SELECT count(*)`

#### Scenario: Soft delete updates status and keeps the node

- **WHEN** a child/teacher/etc. row's `status` transitions to an inactive value in PostgreSQL
  (a soft delete), bumping its `updated_at`
- **THEN** the next incremental tick upserts the node with the new `status`; the node remains in the
  graph (not removed), consistent with the existing status-carrying node model

#### Scenario: Watermark persists and the loader resumes after restart

- **WHEN** the loader process restarts while the graph is already populated and a high-water mark is
  stored
- **THEN** it does NOT perform a full `DETACH DELETE` rebuild; it reads the persisted per-table
  high-water mark and resumes incremental fetching from that position

#### Scenario: Overlapping replay is idempotent

- **WHEN** the same changed rows are fetched again because the watermark boundary is inclusive
  (`updated_at >= :watermark`) or a prior tick partially failed
- **THEN** the `MERGE`-based upsert re-applies them without creating duplicate nodes/edges and
  without corrupting the graph (the operation is idempotent)

---

### Requirement: Schema definition is DB-first with a single authoritative source

`db/dbml/schema.dbml` SHALL be the single authoritative definition of the PostgreSQL schema.
`db/initdb/01_create_schema.sql` SHALL be generated from it via `dbml2sql`. The backend JPA
entities SHALL match the generated DDL; `spring.jpa.hibernate.ddl-auto=validate` enforces this
at startup. Schema evolution SHALL be performed by editing `schema.dbml` first, regenerating
the SQL, then applying the change via Flyway migration.

#### Scenario: Schema change starts from DBML

- **WHEN** a developer changes the PostgreSQL schema (add column, change type, new table)
- **THEN** the change begins with an edit to `db/dbml/schema.dbml`, followed by
  `dbml2sql db/dbml/schema.dbml -o db/initdb/01_create_schema.sql`, and then a new Flyway
  migration file in `backend/src/main/resources/db/migration/`

#### Scenario: Backend startup validates entity-schema alignment

- **WHEN** the Spring Boot backend starts
- **THEN** Hibernate validates all JPA entity mappings against the live PostgreSQL schema; a
  mismatch (missing column, wrong type, extra column annotated as non-nullable) causes startup
  failure before any request is served

---

### Requirement: Flyway manages production schema evolution; initdb is for demo/CI only

Production schema evolution SHALL use Flyway migrations versioned as `VN__description.sql` in
`backend/src/main/resources/db/migration/`. The migration set is **squashed to a single
consolidated baseline** `V1__initial_baseline.sql` that creates the full terminal schema in one
step; the historical `V2..V12` migration chain has been folded into V1 and removed. Future schema
changes SHALL resume at `V2` and append forward (append-only after V1). `db/initdb/01_create_schema.sql`
is regenerated from `db/dbml/schema.dbml` to the same terminal schema and serves as the demo/CI
initialization script only; it MUST NOT be edited to evolve the schema after the baseline is
established (edit DBML → regenerate → add a new `VN` migration instead).

#### Scenario: Fresh production deployment runs the consolidated V1 baseline

- **WHEN** the backend connects to an empty production database for the first time
- **THEN** Flyway executes the single `V1__initial_baseline.sql` to create the full terminal
  schema; no `V2..V12` chain exists (any future migrations are applied as `V2+` once added)

#### Scenario: Demo environment with existing initdb tables is baselined

- **WHEN** Flyway starts against a database already initialized by `initdb/*.sql`
- **THEN** `baseline-on-migrate=true` marks V1 as completed without re-executing it; with no `V2+`
  migrations present, no further migrations are applied and the schema already matches the V1 terminal shape

#### Scenario: Flyway history is auditable

- **WHEN** an operator runs `SELECT * FROM flyway_schema_history`
- **THEN** the applied/baselined version (single V1), checksum, installed-on timestamp, and
  execution status are visible

---

### Requirement: Multi-tenant isolation SHALL be enforced via kindergarten_id composite keys

All tenant-scoped business tables SHALL carry a `kindergarten_id` column (exceptions: `users`,
`audit_logs`, `ai_models`, and platform dictionary tables). Cross-table foreign keys between
tenant-scoped tables MUST be composite, including `kindergarten_id`, to prevent cross-tenant
data linkage at the schema level.

#### Scenario: Composite FK prevents cross-tenant reference

- **WHEN** a row in `camera_streams` references `cctv_cameras`
- **THEN** the FK is composite `(kindergarten_id, camera_id) → cctv_cameras(kindergarten_id,
  camera_id)`; a row in one tenant's `cctv_cameras` cannot be referenced by another tenant's
  `camera_streams` row

#### Scenario: Unique constraint scoped to tenant

- **WHEN** two children in different kindergartens have the same `child_id` sequence value
- **THEN** the UNIQUE constraint `uq_child_kg_childid (kindergarten_id, child_id)` prevents
  collision within the same tenant while allowing the same sequence values across tenants

---

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

The schema source artifacts SHALL stay aligned with the consolidated Flyway baseline `V1`, which is
the source of truth for the deployed schema. A capability test SHALL assert structural invariants
against the fully-initialized schema (fresh `V1` on an empty Testcontainer, and the
`initdb` baseline path) so that a regression leaving the live schema in the wrong terminal shape is
caught. `db/dbml/schema.dbml` SHALL reflect the terminal schema and SHALL be reconciled whenever a
future migration changes a column/type/constraint (verified at review); `db/dbml/schema.dbml` is the
DB-first source from which both `db/initdb/01_create_schema.sql` and `V1__initial_baseline.sql` are
generated, so the fresh-V1 and initdb+baseline paths converge to the same terminal schema.

#### Scenario: Initialized schema matches structural invariants

- **WHEN** the backend test suite initializes the schema against a PostgreSQL Testcontainer (V1 baseline)
- **THEN** structural assertions hold in the V1 terminal schema — e.g. `push_subscriptions` exists and `device_tokens` / `device_platform_enum` do not; `notifications.sent_at` and `fail_reason` are nullable; `children`/`guardians`/`teachers` have `rrn_hash` NOT NULL and no `rrn_encrypted`; `detection_events.dedup_key` exists with the `uq_detection_events_dedup` unique index

#### Scenario: Schema regression is caught

- **WHEN** a change would leave the terminal schema in the wrong shape (missing table, wrong nullability, dropped guard)
- **THEN** the schema-consistency test fails before the change can merge through the backend test gate

#### Scenario: DBML is reconciled when a future migration changes the schema

- **WHEN** a future Flyway migration (`V2+`) changes a column's nullability/type/constraint
- **THEN** `db/dbml/schema.dbml` is updated in the same change to reflect the new terminal schema (kept aligned by process and review, since the DBML is the DB-first design source)

### Requirement: Detection event idempotency key (dedup_key)

The `detection_events` table SHALL carry a `dedup_key` column (NOT NULL) with a unique constraint on
`(kindergarten_id, dedup_key)`. The `dedup_key` is generated by the AI subsystem (from camera +
alarm-onset time) and submitted with the detection event; the backend SHALL rely on this constraint
so that AI reconnects or debounce retries do not create duplicate `detection_events` rows. This is
additive to the existing schema and does not alter any other detection column.

#### Scenario: Duplicate detection event is rejected at the DB

- **WHEN** a second `detection_events` row is inserted with the same `(kindergarten_id, dedup_key)`
- **THEN** the `uq_detection_events_dedup` unique constraint rejects the insert, and the ingest path returns the existing event idempotently rather than creating a duplicate

#### Scenario: dedup_key present in the migrated schema

- **WHEN** the backend test suite runs Flyway migrations against a PostgreSQL Testcontainer
- **THEN** `detection_events.dedup_key` exists (NOT NULL) and the `uq_detection_events_dedup` unique index is present

### Requirement: Seed dataset quality and test-anchor contract

The business seed scripts under `db/initdb/` SHALL constitute a clean, self-consistent, minimal dataset. The scope is the numbered `*_seed.sql` files `21..46` and `88`; the schema and platform-reference scripts (`01_create_schema.sql`, `02_menu.sql`, `03_CommonCode.sql`) are out of scope. These seed files are loaded into the integration-test PostgreSQL container (`BaseIntegrationTest` copies the whole `db/initdb` directory into `/docker-entrypoint-initdb.d`) and into the demo/CI database; production SHALL NOT depend on them (the production database is schema-only via Flyway, `Dockerfile.prod`).

Because the seed simultaneously serves as the shared fixture baseline for integration tests, it
SHALL preserve a set of **test-anchor invariants** that tests rely on. Changes to the seed MUST keep
these invariants intact:

- A `users` row `login_id = 'admin'` with `user_id = 1` exists, plus the per-role login accounts the
  authentication and authorization integration tests depend on.
- Kindergarten ids `{1, 2, 3}` exist and serve as cross-tenant comparison groups.
- Each kindergarten has more than one `rooms` row, and at least one room that is not assigned to any
  given test teacher (so authorization tests have a genuine-but-unassigned negative sample).
- Each kindergarten has enough `classes` / `children` / `cctv_cameras` background rows for the tests
  that read them.

Seed data values SHALL be self-consistent: `room_type` MUST agree with the room `name` and use
consistent vocabulary; relationship cardinalities (e.g. `class_room_assignments`) MUST reflect a
realistic shape rather than a misleading artifact, without breaking the relationship topology the
tests assert. The seed MUST NOT contain dead data for tables removed by Flyway migrations — in
particular there SHALL be no `device_tokens` seed (the `device_tokens` table is dropped by `V7` in
favor of `push_subscriptions`).

#### Scenario: Seed loads cleanly with referential integrity

- **WHEN** the `db/initdb` scripts are executed in filename order against a fresh PostgreSQL instance
  (integration-test container or demo init)
- **THEN** all business seed inserts succeed with no foreign-key violation, and the container starts

#### Scenario: Test-anchor invariants hold after a seed rewrite

- **WHEN** the business seed is rewritten or reduced
- **THEN** the `admin`/`user_id=1` account, the `{1,2,3}` kindergartens, the per-kindergarten
  `room > 1` count, and the genuine-but-unassigned room negative sample all still hold, and the full
  integration-test suite (the `BaseIntegrationTest` subclasses plus `SchemaConsistencyGuardTest`)
  passes

#### Scenario: No dead seed for migration-dropped tables

- **WHEN** the seed directory is inspected
- **THEN** no seed file inserts into `device_tokens` (or any table dropped by a Flyway migration);
  values such as `room_type` are consistent with the corresponding `name`

#### Scenario: Production does not depend on seed

- **WHEN** the production stack starts (`docker-compose.prod.yml`, `Dockerfile.prod`, schema created
  by Flyway)
- **THEN** no `db/initdb` business seed is applied, and the application runs against a schema-only
  database

### Requirement: Step-3b quiet-hours schema invariants

The terminal migrated schema SHALL include the step-③b quiet-hours columns and enum value on both
deploy paths (initdb+baseline and fresh-V1). Specifically, after Flyway `V9`:
`notifications.deferred_until` (timestamptz, nullable) and
`kindergartens.notification_quiet_hours_json` (varchar, nullable) SHALL exist, and
`notification_status_enum` SHALL contain the value `DEFERRED`. `db/dbml/schema.dbml` and
`db/initdb/01_create_schema.sql` SHALL be reconciled with this `V9` change so the two deploy paths
converge to the same terminal schema (verified by `SchemaConsistencyGuardTest`).

#### Scenario: Quiet-hours columns and enum value present after V9

- **WHEN** the fully-migrated schema is inspected against a PostgreSQL Testcontainer (initdb+baseline
  and fresh-V1 paths)
- **THEN** `notifications.deferred_until` and `kindergartens.notification_quiet_hours_json` columns
  exist (both nullable) and `notification_status_enum` contains `DEFERRED`

#### Scenario: Schema source artifacts reconciled with V9

- **WHEN** the `V9` migration adds the two columns and the enum value
- **THEN** `db/dbml/schema.dbml` and `db/initdb/01_create_schema.sql` reflect the same terminal shape,
  so the initdb+baseline and fresh-V1 paths do not drift

### Requirement: Platform reference data is served by enum metadata and static config, not dictionary tables

Platform reference data SHALL NOT be stored in database dictionary tables; the `menu` and
`common_codes` tables, their seed, and their backend/frontend CRUD stacks (`MenuController`/
`MenuService`/`MenuVO`, and `CommonCodeController`/`CommonCodeService`/`CommonCodeMapper`/
`CommonCodeRepository`/`CommonCode` entity/`CommonCodeCreateDTO`/`CommonCodeUpdateDTO`/`CommonCodeVO`)
SHALL be removed (per ADR-0013). Enumerated reference values SHALL be served by a read-only backend
endpoint `GET /api/v1/enums/{name}` (with an optional `context=<table>` qualifier for table-scoped
groups), backed by the existing `com.ai_kids_care.v1.type.*` Java enums and returning the enum codes;
human-readable labels SHALL be supplied by front-end i18n, not the backend. Navigation menus SHALL be
defined as a front-end TypeScript static config keyed by role, not fetched from the backend. A Flyway
migration SHALL drop both tables so that a fresh Flyway-only production database has neither table, and
`db/initdb` SHALL no longer create or seed them.

#### Scenario: Enum metadata endpoint serves reference codes

- **WHEN** a client requests `GET /api/v1/enums/{name}` for a known enum name (e.g. `gender`, `guardian_relationship`, `teacher_level`, `event_type`, `event_status`)
- **THEN** the response lists that enum's codes (sourced from the corresponding `type.*` Java enum), is readable without authentication, and contains no DB-dictionary-backed rows

#### Scenario: Dictionary tables and CRUD stacks are gone

- **WHEN** the backend builds and the schema is migrated to its terminal state
- **THEN** no `menu` or `common_codes` table exists, no `MenuController`/`/api/v1/menus` or `CommonCodeController`/`/api/v1/common_codes` route is registered, and `db/initdb` contains no `02_menu.sql`/`03_CommonCode.sql`

#### Scenario: Fresh Flyway-only production validates without dictionary tables

- **WHEN** the backend starts against an empty database and runs Flyway migrations with `ddl-auto=validate`
- **THEN** startup succeeds with neither `menu` nor `common_codes` present, and the previously `@Disabled` `FlywayMigrationTest` passes

#### Scenario: Navigation menu comes from front-end static config

- **WHEN** the front-end renders navigation for any role (including the anonymous/no-session case)
- **THEN** the menu items come from a TypeScript static config keyed by role, with no call to `/api/v1/menus`

### Requirement: Graph query API is reachable and gated by tenant-scoped authorization

The backend relationship-graph read API SHALL be reachable by authenticated tenant staff and SHALL
replace the dormant `@PreAuthorize("denyAll()")` guard on `GraphService` with a method-level
authorization gate. The gate SHALL be a new `AuthorizationAction.GRAPH_READ` evaluated by
`AuthorizationPolicy`, granting access only to a caller with an effective KINDERGARTEN-scoped tenant
identity AND role `TEACHER` or `KINDERGARTEN_ADMIN` (mirroring the detection-event dashboard
audience). The `@PreAuthorize` annotation SHALL be placed on the `GraphService` method (not the
controller), and the controller endpoint SHALL be served under `/api/v1/**` (session + CSRF posture),
NOT under `/api/v1/internal/**` and NOT in any CSRF exemption.

#### Scenario: Authenticated tenant staff reads a child graph

- **WHEN** a user with role `TEACHER` or `KINDERGARTEN_ADMIN` and an active kindergarten identity
  calls `GET /api/v1/graph/children/{childId}` for a child in their own kindergarten
- **THEN** the request is authorized via `GRAPH_READ`, `GraphService.getChildGraph` executes, and the
  child's class/teacher/kindergarten/guardian graph is returned

#### Scenario: denyAll is no longer the guard

- **WHEN** the backend builds after this change
- **THEN** `GraphService.getChildGraph` is annotated with
  `@PreAuthorize("@authorizationPolicy.isAllowed(... GRAPH_READ)")` and no longer carries
  `@PreAuthorize("denyAll()")`

#### Scenario: Wrong role or no tenant identity is denied

- **WHEN** a caller without an effective KINDERGARTEN-scoped tenant identity (e.g. an unauthenticated
  request, or a platform-scoped role such as `SUPERADMIN`/`PLATFORM_IT_ADMIN`, or a `GUARDIAN` under
  the default policy) calls the graph endpoint
- **THEN** `AuthorizationPolicy.isAllowed(GRAPH_READ)` returns false and the request is denied (no
  graph data is returned)

### Requirement: Graph query enforces tenant isolation inside Cypher and hides cross-tenant existence

The active `kindergarten_id` SHALL be obtained from
`EffectiveAuthorizationContextHolder.requireActiveKindergartenId()` (the ThreadLocal tenant context),
NEVER from a URL path/query parameter or request body. The `kindergarten_id` predicate SHALL be
written into the Cypher query itself (anchor `MATCH (ch:Child {child_id: $childId, kindergarten_id:
$kgId})`, with the traversed `Class`/`Teacher`/`Kindergarten`/`Guardian` nodes constrained to the same
`kindergarten_id`); load-then-filter in Java is prohibited. A child that does not exist, or exists only
in another tenant, or is absent for the caller's kindergarten SHALL all yield HTTP 404 (existence
hidden), never 403, 200-with-empty, or 500.

#### Scenario: Cross-tenant child id returns 404

- **WHEN** a `TEACHER`/`KINDERGARTEN_ADMIN` in kindergarten A requests
  `GET /api/v1/graph/children/{childId}` for a `childId` that exists only in kindergarten B
- **THEN** the Cypher anchor `MATCH (ch:Child {child_id: $childId, kindergarten_id: $kgId})` does not
  match, `GraphRepository` raises `jakarta.persistence.EntityNotFoundException`, and the API responds
  HTTP 404 — the same response as for a non-existent child (existence is not disclosed)

#### Scenario: Tenant id comes from context, not the request

- **WHEN** the graph endpoint is invoked
- **THEN** the kindergarten scope is read via `requireActiveKindergartenId()` and bound as the Cypher
  `$kgId` parameter; the client does not (and cannot) supply `kindergartenId` to widen scope

#### Scenario: Tenant predicate is in the query, not post-filtered

- **WHEN** the child graph is resolved
- **THEN** the `kindergarten_id` predicate is present in the executed Cypher (on the `Child` anchor and
  the traversed nodes); the repository does not fetch a broader result and filter it in Java

### Requirement: Graph query responses contain no PII and never join back to PostgreSQL

The graph read API SHALL project only the non-PII node/edge properties already present in the Neo4j
derived graph (which holds no S0/PII fields per INC-003). VO mapping SHALL read exclusively from Neo4j
driver `Node`/relationship values; it MUST NOT join back to PostgreSQL (no JPA/SQL lookup) to enrich
the response, so that no PII column (`rrn_hash`, `rrn_first6`, `rrn_encrypted`, `birth_date`,
`address`, `email`, `phone`, `emergency_contact_*`, `password_hash`, `contact_*`) can re-enter via the
read path. This requirement does not weaken the loader-side INC-003 control; it extends the no-PII
invariant to the query/response path.

#### Scenario: Response carries only graph node properties

- **WHEN** `GET /api/v1/graph/children/{childId}` succeeds
- **THEN** the `ChildGraphVO` contains only graph-projected fields (e.g. child `name`/`childNo`/
  `status`, class, teacher, kindergarten, and guardians with edge `relationship`/`isPrimary`/
  `priority`) and no S0/PII field (SEC-12: `gender` is not projected)

#### Scenario: VO mapping does not re-read PostgreSQL

- **WHEN** the graph traversal result is mapped to `ChildGraphVO`
- **THEN** the mapping uses only Neo4j driver values returned by `GraphRepository`; it performs no JPA
  repository call or SQL query against PostgreSQL to populate any field

### Requirement: Teacher-centric graph query is reachable and enforces the same tenant isolation

The backend SHALL expose a teacher-centric graph read at `GET /api/v1/graph/teachers/{teacherId}`
returning a `TeacherGraphVO` of the teacher's classes and the children in those classes
(`(Teacher)-[:HAS_CLASS]->(Class)-[:HAS_CHILD]->(Child)`), gated by the same `GRAPH_READ`
authorization action and `@PreAuthorize` on the `GraphService` method (not the controller). The active
`kindergarten_id` SHALL be obtained from `requireActiveKindergartenId()` (ThreadLocal), never from the
URL, and the `kindergarten_id` predicate SHALL be written into the Cypher anchor
`MATCH (t:Teacher {teacher_id: $teacherId, kindergarten_id: $kgId})` with traversed nodes constrained
to the same tenant; load-then-filter is prohibited. A teacher that does not exist or exists only in
another tenant SHALL yield HTTP 404 (existence hidden), and the response SHALL contain no PII and SHALL
NOT join back to PostgreSQL, identical to the child-centric path.

#### Scenario: Authenticated tenant staff reads a teacher graph

- **WHEN** a user with role `TEACHER` or `KINDERGARTEN_ADMIN` and an active kindergarten identity calls
  `GET /api/v1/graph/teachers/{teacherId}` for a teacher in their own kindergarten
- **THEN** the request is authorized via `GRAPH_READ`, `GraphService.getTeacherGraph` executes, and the
  teacher's classes and the children in those classes are returned with no PII fields

#### Scenario: Cross-tenant teacher id returns 404

- **WHEN** a `TEACHER`/`KINDERGARTEN_ADMIN` in kindergarten A requests
  `GET /api/v1/graph/teachers/{teacherId}` for a `teacherId` that exists only in kindergarten B
- **THEN** the Cypher anchor `MATCH (t:Teacher {teacher_id: $teacherId, kindergarten_id: $kgId})` does
  not match, the repository raises `jakarta.persistence.EntityNotFoundException`, and the API responds
  HTTP 404 — the same response as for a non-existent teacher

