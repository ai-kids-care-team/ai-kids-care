## MODIFIED Requirements

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
