## ADDED Requirements

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
