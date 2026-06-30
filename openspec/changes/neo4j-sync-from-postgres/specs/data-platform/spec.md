## MODIFIED Requirements

### Requirement: Neo4j loader MUST NOT project S0 or PII fields into the graph (INC-003)

The data-loader in `db/ne4j_kindergartens/` MUST NOT write any S0 or S1/PII fields into Neo4j
node properties. Specifically, the following fields SHALL be absent from all graph nodes:
`password_hash`, `rrn_hash`, `rrn_first6`, `rrn_encrypted`, `birth_date`, `address`, `email`,
`phone`, `emergency_contact_name`, `emergency_contact_phone`, `contact_phone`, `contact_email`,
`stream_password_encrypted`, `stream_password_ciphertext`.

The loader sources graph data by querying PostgreSQL (the system of record) directly. For each
node label it SHALL `SELECT` only an explicit allowlist of non-PII columns, so that PII columns
never enter the loader's row data and therefore cannot be bound into any Cypher write (allowlist
at the source is the primary INC-003 control, not a post-hoc scrub). As defense-in-depth, a scrub
script (`no000_scrub_sensitive.py`) SHALL run before the load to `REMOVE` any sensitive attributes
that may remain on pre-existing nodes from a prior run. The static guard `LoaderPiiProjectionGuardTest`
SHALL continue to scan the loader's Python source and assert that no forbidden field is bound into a
node property.

#### Scenario: Loader selects only non-PII columns from PostgreSQL

- **WHEN** the data-loader builds a `Child`, `Teacher`, `User`, `Kindergarten`, `Class`, or
  `Guardian` node from PostgreSQL
- **THEN** the SQL query `SELECT`s only that label's allowlisted non-PII columns (e.g. for `Child`:
  `child_id`, `kindergarten_id`, `name`, `child_no`, `gender`, `enroll_date`, `leave_date`,
  `status`, `created_at`, `updated_at`), so PII columns such as `rrn_first6`, `rrn_encrypted`,
  `rrn_hash`, `birth_date`, `address`, `emergency_contact_*`, `email`, `phone`, `password_hash`,
  `contact_*` are never read into the loader and never written to the graph

#### Scenario: Graph nodes contain no PII property after a load

- **WHEN** the loader completes and `MATCH (n) UNWIND keys(n) AS k RETURN DISTINCT k` is run
  against Neo4j
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

### Requirement: Neo4j sync SHALL be one-shot from PostgreSQL; no incremental sync is implemented

The data-loader (`run_all.sh`) SHALL execute as a one-shot operation at container startup
(`restart: no` in Compose) that **queries PostgreSQL directly** to (re)build the derived graph; it
SHALL NOT read static CSV snapshots. The graph reflects the PostgreSQL state **as of the loader's
run time**. Neo4j MUST NOT be assumed to reflect PostgreSQL changes made after the loader run; an
incremental or live-sync mechanism is not implemented. To guarantee the graph strictly mirrors the
current source, the loader SHALL clear the existing graph before rebuilding (`MATCH (n) DETACH
DELETE n`), which is safe because Neo4j holds no authoritative data (it is a read-only derived
view).

#### Scenario: Loader builds the graph from PostgreSQL, not CSV

- **WHEN** the data-loader runs
- **THEN** it connects to PostgreSQL using the injected `DB_HOST/DB_PORT/DB_NAME/DB_USER/DB_PASSWORD`
  configuration and reads node/relationship data via SQL queries; it does not open any
  `db/ne4j_kindergartens/data/*.csv` file (those CSV snapshots no longer exist)

#### Scenario: Graph reflects PostgreSQL at run time and clears stale state

- **WHEN** the loader runs after rows have been deleted from PostgreSQL since a prior run
- **THEN** the loader first clears the graph and rebuilds it, so deleted entities do not linger as
  orphan nodes; node counts per label match the corresponding PostgreSQL `SELECT count(*)`

#### Scenario: Graph data becomes stale after a later PostgreSQL write

- **WHEN** a business write (e.g. new child enrollment) is committed to PostgreSQL after the loader
  has completed
- **THEN** the Neo4j graph does NOT automatically reflect the change; a loader re-run is required to
  re-sync

#### Scenario: Loader runs as a one-time Compose task

- **WHEN** Docker Compose starts the data-loader service
- **THEN** the service has `restart: no`, depends on PostgreSQL being healthy and Neo4j being
  started, and exits after `run_all.sh` completes; it does not run again unless explicitly re-invoked

#### Scenario: Loader fails loudly when PostgreSQL is unreachable

- **WHEN** the loader cannot connect to PostgreSQL or a required query errors
- **THEN** the loader process exits with a non-zero status rather than silently producing an empty or
  partial graph
