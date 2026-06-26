## REMOVED Requirements

### Requirement: dictionary tables (menu and common_codes) SHALL be governed per ADR-0013 and MUST NOT be extended

**Reason**: ADR-0013 is implemented by this change. The freeze/"awaiting independent Implementation"
governance no longer applies: the `menu` and `common_codes` tables, their `db/initdb` seed, and their
backend/frontend CRUD stacks are removed, and platform reference data is served by an enum metadata
endpoint plus a front-end static menu config. This requirement is superseded by the ADDED requirement
"Platform reference data is served by enum metadata and static config, not dictionary tables".

## ADDED Requirements

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
