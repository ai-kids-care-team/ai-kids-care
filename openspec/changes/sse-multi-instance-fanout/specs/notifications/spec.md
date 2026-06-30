## ADDED Requirements

### Requirement: Deferred-notification scanner is single-dispatcher across instances

The scheduled scanner that dispatches due `DEFERRED` guardian notifications SHALL ensure that, when
the backend runs as more than one instance, at most one instance executes a given scan tick (a tick
processes the notifications whose `deferred_until` has passed), so a due deferred notification is
dispatched once across the cluster rather than once per instance. The scanner SHALL acquire a cluster-wide lock (via
ShedLock, backed by a `shedlock` lock table in the authoritative PostgreSQL) before scanning; an
instance that fails to acquire the lock for a tick SHALL skip that tick without scanning or
dispatching. The lock's maximum hold SHALL be shorter than the scan interval so that a crashed
lock-holder does not block subsequent ticks on surviving instances. This relaxes the prior
single-instance assumption documented on `DeferredNotificationScanner` while preserving the existing
per-row dispatch semantics (best-effort per row; `SENDING` → `SENT`/`FAILED`; `(kindergarten_id,
dedupe_key)` de-duplication) unchanged. Adding the `shedlock` table is a schema migration requiring
maintainer approval.

#### Scenario: Only one instance dispatches a due deferred notification

- **WHEN** the backend runs two instances and a `DEFERRED` notification's `deferred_until` has passed
- **THEN** exactly one instance acquires the scan lock and dispatches the notification (through `SENDING` to `SENT`/`FAILED`), and the other instance skips its concurrent tick, so the guardian receives the notification once and not once per instance

#### Scenario: Lock contention causes a clean skip

- **WHEN** a scan tick fires on an instance while another instance already holds the scan lock for that tick
- **THEN** the contending instance does not scan or dispatch for that tick and takes no action other than skipping, and a not-yet-due deferred notification is left untouched

#### Scenario: Crashed lock-holder does not stall the cluster

- **WHEN** the instance holding the scan lock crashes mid-tick
- **THEN** the lock expires after its maximum-hold bound (shorter than the scan interval), and a surviving instance acquires the lock on a subsequent tick and resumes dispatching due deferred notifications

#### Scenario: Single-instance scanner behavior is unchanged

- **WHEN** the backend runs as exactly one instance
- **THEN** that instance acquires the lock each tick and dispatches due deferred notifications exactly as before this change, with no behavioral difference other than the added lock acquisition
