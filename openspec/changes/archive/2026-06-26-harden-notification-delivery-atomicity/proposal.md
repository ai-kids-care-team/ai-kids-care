## Why

`NotificationService.dispatch()` runs its entire body inside a single `@Transactional`,
with the external Pushover/SMS network round-trip sandwiched between two `repository.save`
calls (confirmed: `docs/assessments/2026-06-25-backend-multiangle/findings/performance.md`
PRF-02, high). This causes two problems under load:

1. **Connection-pool exhaustion** — one DB connection is held for the full network round-trip
   (the external clients now have timeouts, but still occupy the connection for up to seconds).
   `dispatch()` is called per-recipient in loops by `StaffAlertService`,
   `GuardianNotificationService`, and `DeferredNotificationScanner`, so a burst of alerts can
   drain HikariCP (max 20) and block unrelated requests.
2. **Delivery-atomicity gap** — if the push succeeds but the subsequent `SENT` save fails, the
   transaction rolls back and the row is left at `SENDING` while the push has already gone out
   (the code comment already admits this). There is no idempotent retry, so a retry would
   double-send.

This is a structural defect, not a tuning knob: the only correct fix is to move external
delivery out of the database transaction.

## What Changes

- Split `NotificationService.dispatch()` into a two-phase, transaction-isolated flow:
  short transaction sets `SENDING` and **commits** → external HTTP/SMS call runs **outside**
  any DB transaction → short transaction writes the terminal `SENT`/`FAILED` state.
- Introduce a **per-delivery idempotency key** so a "push already sent, status write failed"
  case can be safely retried without re-sending (the provider call is guarded by the key /
  the row's current state).
- No DB connection is held across an external network call on any delivery path
  (PUSH and SMS, single and multi-channel).
- Preserve all existing behavior: recipient resolution, `notification_rules`, quiet-hours
  deferral, per-recipient/per-channel best-effort isolation, and the existing
  `SENDING → SENT/FAILED` lifecycle states remain the contract.
- **Out of scope (deferred):** multi-instance dedup / `ShedLock` on
  `DeferredNotificationScanner` (PRF-04) and SSE multi-instance fanout (PRF-05) stay deferred
  until multi-instance deployment; this change targets single-instance atomicity + connection
  safety only.

## Capabilities

### New Capabilities
<!-- none: the outbox/worker is an implementation detail under the existing notifications capability -->

### Modified Capabilities
- `notifications`: add a **delivery atomicity & isolation** requirement — external delivery
  MUST occur outside the DB transaction, the `SENDING → SENT/FAILED` transition MUST be
  idempotent under retry, and no notification may be left indefinitely at `SENDING` when the
  provider call succeeded.

## Impact

- **Code**: `NotificationService` (dispatch split), the call sites that loop per recipient
  (`StaffAlertService`, `GuardianNotificationService`, `DeferredNotificationScanner`) — only
  in how they invoke dispatch, not their recipient logic. Possibly a small delivery-worker /
  outbox component and an idempotency-key column.
- **Schema**: MAY add an idempotency key (and/or an outbox table) via a new Flyway migration.
  `db/initdb` seed is an integration-test fixture, so any schema change requires
  `gradle cleanTest test` (not just `test`).
- **External clients**: `PushoverClient` (timeouts already configured) and
  `SmsPort`/`SolapiSmsAdapter` — unchanged contracts, now invoked outside a transaction.
- **Tests**: TDD with injected-failure construction (provider succeeds, status save fails)
  to prove no row is stuck at `SENDING` and that retry does not double-send.
- **No breaking API/behavior change** for callers or recipients; states and channels unchanged.
