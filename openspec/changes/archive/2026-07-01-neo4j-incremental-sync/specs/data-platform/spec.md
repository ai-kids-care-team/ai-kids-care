## RENAMED Requirements

- FROM: `### Requirement: Neo4j sync SHALL be one-shot from PostgreSQL; no incremental sync is implemented`
- TO: `### Requirement: Neo4j sync keeps the derived graph current via incremental sync from PostgreSQL`

## MODIFIED Requirements

### Requirement: Neo4j sync keeps the derived graph current via incremental sync from PostgreSQL

The data-loader (`db/ne4j_kindergartens/`) SHALL keep the Neo4j derived graph continuously
converged with PostgreSQL via an **incremental sync**, so that business writes committed to
PostgreSQL are reflected in the graph within a bounded polling interval **without a manual
re-run**. The loader SHALL run as a **long-lived process** (Compose `restart: unless-stopped`)
that, on each tick, fetches rows changed since a per-table high-water mark
(`WHERE updated_at >= :watermark`) and `MERGE`-upserts them into Neo4j using the existing non-PII
column allowlist and node/relationship Cypher. The loader SHALL remain the **sole writer** of
Neo4j (no application service writes the graph); PostgreSQL remains the system of record and Neo4j
remains a read-only derived view.

The one-shot **full rebuild** (`MATCH (n) DETACH DELETE n` then load) is retained as the
**bootstrap / recovery path**: when the graph is empty or no high-water mark exists, the loader
performs a full rebuild and initializes the high-water marks to `max(updated_at)` per table;
steady state is incremental upsert (no full clear, so an online graph read never sees an empty
graph). The defense-in-depth scrub (`no000_scrub_sensitive.py`) SHALL still run before the load.

#### Scenario: Steady-state incremental upsert from PostgreSQL

- **WHEN** the loader is running in steady state and a tick executes
- **THEN** for each source table it `SELECT`s only the allowlisted non-PII columns (plus
  `updated_at`) for rows with `updated_at >= :watermark`, `MERGE`-upserts the corresponding
  nodes/relationships into Neo4j, and advances that table's high-water mark to the batch
  `max(updated_at)`; it does NOT clear the whole graph and does NOT read any
  `db/ne4j_kindergartens/data/*.csv` file

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
watermark-fetch path — the incremental `SELECT` adds only the non-PII `updated_at` watermark
column and reads no additional column. Any loader-internal sync-state stored in Neo4j (e.g. a
high-water-mark meta-node) SHALL carry only non-sensitive bookkeeping properties (table name,
watermark timestamp) and no PII. As defense-in-depth, a scrub script (`no000_scrub_sensitive.py`)
SHALL run before the load to `REMOVE` any sensitive attributes that may remain on pre-existing
nodes from a prior run. The static guard `LoaderPiiProjectionGuardTest` SHALL continue to scan the
loader's Python source and assert that no forbidden field is bound into a node property.

#### Scenario: Loader selects only non-PII columns from PostgreSQL

- **WHEN** the data-loader builds a `Child`, `Teacher`, `User`, `Kindergarten`, `Class`, or
  `Guardian` node from PostgreSQL (full rebuild or incremental upsert)
- **THEN** the SQL query `SELECT`s only that label's allowlisted non-PII columns (e.g. for `Child`:
  `child_id`, `kindergarten_id`, `name`, `child_no`, `gender`, `enroll_date`, `leave_date`,
  `status`, `created_at`, `updated_at`), so PII columns such as `rrn_first6`, `rrn_encrypted`,
  `rrn_hash`, `birth_date`, `address`, `emergency_contact_*`, `email`, `phone`, `password_hash`,
  `contact_*` are never read into the loader and never written to the graph

#### Scenario: Incremental fetch reuses the same non-PII allowlist

- **WHEN** an incremental tick fetches rows changed since the high-water mark
  (`SELECT ... FROM <table> WHERE updated_at >= :watermark`)
- **THEN** the selected column set is exactly that label's non-PII allowlist plus the `updated_at`
  watermark column; no forbidden S0/PII column is added to the incremental query

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

## ADDED Requirements

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
