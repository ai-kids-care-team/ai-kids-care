## ADDED Requirements

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
