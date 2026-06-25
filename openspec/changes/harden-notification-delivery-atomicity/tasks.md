## 1. Investigate & pin current behavior

- [ ] 1.1 Re-read `NotificationService.dispatch()` and its SMS branch; list every state write and the exact `@Transactional` boundary, and confirm the `SENDING → SENT/FAILED` states and columns in `notifications`.
- [ ] 1.2 Trace the three callers (`StaffAlertService`, `GuardianNotificationService`, `DeferredNotificationScanner`) and confirm none rely on `dispatch` sharing/extending their transaction for rollback (delivery is already best-effort per recipient). Note any caller that opens an outer transaction around the dispatch loop.
- [ ] 1.3 Write a characterization integration test capturing current externally-visible behavior (recipient resolution, quiet-hours deferral, per-recipient/per-channel isolation) so the refactor can prove parity.

## 2. Schema: idempotency / delivery-attempt storage

- [ ] 2.1 Decide storage shape per design (lean: dedicated `notification_delivery_attempts` table keyed by notification id + idempotency key, with provider + outcome + timestamps).
- [ ] 2.2 Add a new Flyway migration (`V12+`) creating that storage; mirror it into the `db/initdb` baseline per existing convention.
- [ ] 2.3 Keep `FlywayMigrationTest` green (fresh-DB migration chain) and run `gradle cleanTest test` since `db/initdb` seed is a test fixture.

## 3. TDD: failing tests for atomicity & isolation (red)

- [ ] 3.1 Test: provider succeeds but the terminal `SENT` save fails → notification is not left silently at `SENDING` as "delivered but unrecorded"; assert the reconcile-to-terminal behavior from the spec.
- [ ] 3.2 Test: retry of a delivery whose provider call already succeeded does NOT re-send (assert provider invoked at most once across the retry, via a mock/spy `PushoverClient`/`SmsPort`).
- [ ] 3.3 Test: provider failure (timeout/error/no delivery identity) → terminal state persisted as `FAILED`, isolated per recipient and per channel.
- [ ] 3.4 Test (DooD, optional/deep): slow provider during burst dispatch → no HikariCP connection held across the provider call; assert pool active-connections stay free for unrelated queries.

## 4. Refactor dispatch into transaction-isolated phases (green)

- [ ] 4.1 Remove method-level `@Transactional` from `dispatch`; introduce a short `REQUIRES_NEW` transaction that persists `SENDING` + the idempotency token / in-flight attempt and commits.
- [ ] 4.2 Perform the provider call (PUSH and SMS branches) with no transaction open and no connection checked out for its duration.
- [ ] 4.3 Short `REQUIRES_NEW` transaction writes terminal `SENT` (with provider result) / `FAILED`; implement the idempotent reconcile so an in-flight attempt is never blindly re-sent on retry (at-most-once on the retry path per design Decision 2).
- [ ] 4.4 Keep the public `dispatch` signature stable; update callers only where they must stop wrapping the per-recipient call in their own transaction.

## 5. Verify & integrate

- [ ] 5.1 All tests from §3 pass; §1.3 parity test still green (no behavior regression).
- [ ] 5.2 Full backend suite via DooD `gradle cleanTest test` (testcontainers) green.
- [ ] 5.3 Code review for: no connection held across provider call, no row stuck at `SENDING`, no duplicate send on retry, per-recipient/per-channel isolation preserved.
- [ ] 5.4 Update notifications closed-loop progress notes; confirm PRF-04/05 (multi-instance) remain explicitly deferred and not regressed.
