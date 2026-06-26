## Context

`NotificationService.dispatch(Notification)` is annotated `@Transactional` and, within that one
transaction, sets `SENDING` + saves, calls the external provider (`PushoverClient` or
`SmsPort`/`SolapiSmsAdapter`), then sets `SENT` and saves. The external call therefore runs while a
HikariCP connection (pool max 20) is checked out, and a failure of the second save rolls back to a
durable `SENDING` row even though the push already left the building (the code comment admits this).
Callers (`StaffAlertService`, `GuardianNotificationService`, `DeferredNotificationScanner`) invoke
`dispatch` once per recipient in loops, multiplying the held-connection time.

External-client timeouts already exist (PRF-03, landed). Multi-instance concerns
(`ShedLock` on the scanner / SSE fanout, PRF-04/05) remain deferred. This change is single-instance
correctness only.

## Goals / Non-Goals

**Goals:**
- No DB connection is held across an external provider call on any delivery path.
- The `SENDING → SENT/FAILED` transition is idempotent: a provider success followed by a failed
  status write never (a) leaves the row stuck at `SENDING`, nor (b) causes a duplicate send on retry.
- Preserve all current behavior: recipient resolution, `notification_rules`, quiet-hours deferral,
  per-recipient/per-channel best-effort isolation, existing lifecycle states.

**Non-Goals:**
- No asynchronous polling outbox worker / no background delivery daemon in this change.
- No multi-instance dedup (`ShedLock`) or distributed delivery coordination (deferred, PRF-04/05).
- No change to provider adapters' contracts or to the set of lifecycle states.

## Decisions

### Decision 1: Three short transactions around an out-of-transaction provider call (not one big `@Transactional`)

Restructure `dispatch` so it is **not** `@Transactional` at the method level. Instead:
1. **Tx A (short, `REQUIRES_NEW`)**: resolve the delivery identity, persist `SENDING` + an
   idempotency token / attempt marker, commit.
2. **No transaction**: call the provider (`PushoverClient` / `SmsPort`) — timeouts already bound it.
3. **Tx B (short, `REQUIRES_NEW`)**: write terminal `SENT` (with provider result) or `FAILED`, commit.

The connection for A is released before step 2; B checks out a fresh connection after the call.

*Alternative considered — full async outbox table + `@Scheduled` worker that polls and delivers:*
more durable across process crashes and a natural fit for multi-instance, but it adds a polling
component, a new failure-reconciliation surface, and overlaps the deferred multi-instance work.
Rejected for now as over-scoped; the synchronous two-phase split removes the actual defects
(connection-held + non-atomic terminal write) with far less surface area. The outbox/worker remains
the forward path when multi-instance delivery is taken on.

### Decision 2: Idempotency via a recorded delivery attempt, checked before re-send

Before the provider call (in Tx A), record a per-delivery idempotency key and that an attempt is
**in flight**. After the call (Tx B), record the outcome. On any retry of a notification still in
`SENDING` with an in-flight attempt recorded, the service MUST NOT blindly re-send; it reconciles to
`FAILED` (or a terminal state) rather than risk a duplicate, because Pushover/Solapi do not expose a
reliable provider-side idempotency key. The guarantee offered is therefore **at-most-once on the
retry path** (no duplicate sends), accepting that a crash exactly between provider-success and Tx B
may record a delivered message as `FAILED` — preferable to double-notifying a parent.

*Storage choice (to settle in tasks):* either (a) a small `notification_delivery_attempts` table
keyed by notification id + idempotency key, or (b) idempotency/attempt columns on `notifications`.
Lean toward a dedicated table to avoid widening the hot `notifications` row and to leave room for the
future async worker. Any schema change goes through a new Flyway migration; `db/initdb` is an
integration-test fixture, so verification uses `gradle cleanTest test`.

### Decision 3: Callers unchanged except they no longer wrap delivery in their own transaction

`StaffAlertService` / `GuardianNotificationService` / `DeferredNotificationScanner` keep their
recipient-resolution logic. The only change is that the per-recipient `dispatch` call is now
self-contained (its own short transactions) rather than participating in / extending a caller
transaction. Confirm none of these callers rely on `dispatch` running inside a shared transaction
for rollback semantics (they already treat delivery as best-effort per recipient).

## Risks / Trade-offs

- **[Crash between provider-success and Tx B records a sent message as FAILED]** → Accept (at-most-once
  on retry). Documented as the deliberate trade-off in Decision 2; the alternative (risking duplicate
  guardian alerts) is worse for this domain.
- **[`REQUIRES_NEW` nested-transaction / connection use]** → Each phase is short and sequential, not
  nested concurrently; net connection-hold time drops sharply because the network call holds none.
  Verify no caller opens an outer transaction that would make `REQUIRES_NEW` suspend/hold a second
  connection across the call.
- **[Schema migration on a fixture-backed test suite]** → New Flyway migration + `cleanTest`; mirror
  the migration into `db/initdb` baseline per existing convention, and keep `FlywayMigrationTest` green.
- **[Behavior parity regressions]** → Cover with tests asserting recipient resolution, quiet-hours
  deferral, and per-recipient/per-channel isolation are unchanged.

## Migration Plan

1. Add the idempotency/attempt storage via a new Flyway migration (`V12+`), mirrored into
   `db/initdb` baseline; keep `FlywayMigrationTest` green.
2. Refactor `dispatch` into the three-phase flow behind the same public method signature so callers
   are untouched.
3. TDD: inject a provider that succeeds while the terminal save fails; assert no row stuck at
   `SENDING` semantics and that retry does not double-send; assert FAILED on provider failure;
   assert no connection held across the call (e.g. via a slow-provider + pool-metrics probe, DooD).
4. Rollback: the change is additive (new table/columns + internal refactor); reverting the code
   restores the prior behavior, and the unused migration is inert.

## Open Questions

- Storage shape: dedicated `notification_delivery_attempts` table vs idempotency columns on
  `notifications` (lean: dedicated table). To finalize in tasks.
- Whether to expose a minimal reconciliation/requeue path for `FAILED` deliveries now, or defer it
  with the future async worker (lean: defer).
