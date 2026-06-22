## ADDED Requirements

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
  `child_id`, `name`, `kindergarten_id`, `status`, `gender`, `enroll_date`, `leave_date` for
  Child); S0/PII fields are absent

#### Scenario: Graph relationship edges carry required attributes

- **WHEN** `no1000_create_relationships.py` runs
- **THEN** the following relationships are established with the stated properties:
  `(Class)-[:HAS_CHILD]->(Child)`,
  `(Teacher)-[:HAS_CLASS]->(Class)`,
  `(Kindergarten)-[:HAS_TEACHER]->(Teacher)`,
  `(Child)-[:HAS_GUARDIAN]->(Guardian)` (with `relationship`, `is_primary`, `priority`
  attributes on the guardian edge)

---

### Requirement: Neo4j loader MUST NOT project S0 or PII fields into the graph (INC-003)

The data-loader scripts in `db/ne4j_kindergartens/` MUST NOT write any S0 or S1/PII fields
into Neo4j node properties. Specifically, the following fields SHALL be absent from all graph
nodes: `password_hash`, `rrn_hash`, `rrn_first6`, `rrn_encrypted`, `birth_date`, `address`,
`email`, `phone`, `emergency_contact_name`, `emergency_contact_phone`, `contact_phone`,
`contact_email`, `stream_password_encrypted`, `stream_password_ciphertext`. A scrub script
(`no000_scrub_sensitive.py`) SHALL be run before any loader re-execution to remove previously
projected sensitive attributes from existing nodes.

#### Scenario: Child loader omits PII fields

- **WHEN** `no500_insert_children.py` writes a `Child` node via `MERGE … SET`
- **THEN** the SET clause does NOT include `rrn_first6`, `rrn_encrypted`, `rrn_hash`,
  `birth_date`, or `address`; these columns from the source data are silently dropped before
  the Cypher write

#### Scenario: Teacher loader omits PII fields

- **WHEN** `no300_insert_teachers.py` writes a `Teacher` node
- **THEN** the SET clause does NOT include `rrn_encrypted`, `rrn_first6`,
  `emergency_contact_name`, or `emergency_contact_phone`

#### Scenario: User loader omits credential and contact fields

- **WHEN** `no100_insert_users.py` writes a `User` node
- **THEN** the SET clause does NOT include `password_hash`, `email`, or `phone`

#### Scenario: Kindergarten loader omits contact PII fields

- **WHEN** `no200_insert_kindergarter.py` writes a `Kindergarten` node
- **THEN** the SET clause does NOT include `address`, `contact_phone`, or `contact_email`

#### Scenario: Guardian loader omits PII fields

- **WHEN** `no600_insert_guardians.py` writes a `Guardian` node
- **THEN** the SET clause does NOT include `rrn_encrypted`, `rrn_first6`, or `address`

#### Scenario: Scrub script removes previously projected sensitive attributes

- **WHEN** `no000_scrub_sensitive.py` is executed against a Neo4j instance that has existing
  nodes from a prior loader run
- **THEN** `REMOVE` Cypher statements strip `password_hash`, `email`, `phone` from `User`;
  `address`, `contact_phone`, `contact_email` from `Kindergarten`; `rrn_encrypted`,
  `rrn_first6`, `emergency_contact_phone`, `emergency_contact_name` from `Teacher`;
  `rrn_first6`, `rrn_encrypted`, `birth_date`, `address` from `Child`; `rrn_encrypted`,
  `rrn_first6`, `address` from `Guardian`

---

### Requirement: Neo4j sync SHALL be one-shot; no incremental sync is implemented

The data-loader (`run_all.sh`) SHALL execute as a one-shot operation at container startup
(`restart: no` in Compose). Neo4j MUST NOT be assumed to reflect PostgreSQL changes made after
the loader run. An incremental or live-sync mechanism is not implemented.

#### Scenario: Graph data becomes stale after a PostgreSQL write

- **WHEN** a business write (e.g. new child enrollment) is committed to PostgreSQL after the
  loader has completed
- **THEN** the Neo4j graph does NOT automatically reflect the change; a full or partial
  loader re-run is required to re-sync

#### Scenario: Loader runs as a one-time Compose task

- **WHEN** Docker Compose starts the data-loader service
- **THEN** the service has `restart: no`, depends on PostgreSQL being healthy and Neo4j being
  started, and exits after `run_all.sh` completes; it does not run again unless explicitly
  re-invoked

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

### Requirement: Schema drift between schema-digest and migrations is prevented (INC guardrail)

The repository SHALL maintain a `docs/engineering/schema-digest.md` that enumerates all NOT
NULL, UNIQUE, FK, and enum constraints. A CI check (`schema-digest-drift.yml`) SHALL fail if
the digest diverges from the current migrations. The digest MUST be regenerated with
`bash scripts/schema-digest.sh` after any migration.

#### Scenario: Migration added without regenerating digest causes CI failure

- **WHEN** a developer adds a new Flyway migration but does not regenerate `schema-digest.md`
- **THEN** the `schema-digest-drift.yml` GitHub Actions workflow detects the drift and fails

#### Scenario: Digest regenerated after migration passes CI

- **WHEN** a developer adds a Flyway migration and runs `bash scripts/schema-digest.sh` before
  pushing
- **THEN** `schema-digest.md` reflects the new constraint set and the drift check passes

---

### Requirement: Flyway manages production schema evolution; initdb is for demo/CI only

Production schema evolution SHALL use Flyway migrations versioned as `VN__description.sql` in
`backend/src/main/resources/db/migration/`. `db/initdb/01_create_schema.sql` serves as the V1
baseline snapshot and as the demo/CI initialization script only; it MUST NOT be edited to
evolve the schema after the baseline is established.

#### Scenario: Fresh production deployment runs Flyway from V1

- **WHEN** the backend connects to an empty production database for the first time
- **THEN** Flyway executes V1 (`V1__initial_baseline.sql`) to create the base schema, then
  applies V2 through VN in order

#### Scenario: Demo environment with existing initdb tables is baselined

- **WHEN** Flyway starts against a database already initialized by `initdb/*.sql`
- **THEN** `baseline-on-migrate=true` marks V1 as completed without re-executing it, and Flyway
  applies V2+ migrations on top

#### Scenario: Flyway history is auditable

- **WHEN** an operator runs `SELECT * FROM flyway_schema_history`
- **THEN** each applied migration version, checksum, installed-on timestamp, and execution
  status is visible

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

### Requirement: dictionary tables (menu and common_codes) SHALL be governed per ADR-0013 and MUST NOT be extended

Per ADR-0013 (Accepted 2026-05-29), `menu` SHALL be replaced by a front-end TypeScript static
config, and `common_codes` SHALL be replaced by a backend enum metadata endpoint
(`GET /api/v1/enums/{name}?context=<table>`) plus front-end i18n. Both tables (`menu` from
`db/initdb/02_menu.sql`, singular; `common_codes` from `db/initdb/03_CommonCode.sql`, plural)
are platform dictionary tables outside the 28 core business tables; their associated
backend/frontend code MUST NOT be extended; no new Flyway migration SHALL target either table;
they are awaiting independent Implementation.

#### Scenario: menu table is not referenced by new code

- **WHEN** a developer adds or modifies backend or frontend code
- **THEN** no new call site references `MenuController`, `MenuService`, `MenuVO`, or
  `/api/v1/menus`; existing call sites are preserved only until the static migration is
  implemented

#### Scenario: common_codes table is not extended by new migrations

- **WHEN** a developer authors a new Flyway migration
- **THEN** the migration MUST NOT add columns, constraints, or data to the `common_codes`
  table; the table is in a frozen pending-removal state

#### Scenario: common_codes CRUD stack is not extended

- **WHEN** a developer adds backend code
- **THEN** no new endpoint, DTO, VO, or repository is added to the `CommonCode` CRUD stack
  (`CommonCodeController`, `CommonCodeService`, `CommonCodeMapper`, `CommonCodeRepository`,
  `CommonCode` entity, `CommonCodeCreateDTO`, `CommonCodeUpdateDTO`, `CommonCodeVO`)

---

### Requirement: MapStruct unmapped target properties must not be silently dropped (INC-005)

All MapStruct mapper interfaces in the backend SHALL use `unmappedTargetPolicy = ReportingPolicy.ERROR`
or equivalent configuration so that an unmapped target property in a DTO/VO/entity mapping
causes a compile-time error rather than being silently set to null or ignored.

#### Scenario: New field added to entity without updating mapper

- **WHEN** a developer adds a field to a JPA entity but does not add the corresponding mapping
  in the MapStruct mapper
- **THEN** the build fails with a compile-time MapStruct unmapped target error; no silent null
  is introduced into the mapped output

#### Scenario: Mapper with full field coverage compiles successfully

- **WHEN** all target properties in a MapStruct mapper are explicitly mapped or explicitly
  ignored via `@Mapping(target = "...", ignore = true)`
- **THEN** the build succeeds with no MapStruct warnings or errors for unmapped targets
