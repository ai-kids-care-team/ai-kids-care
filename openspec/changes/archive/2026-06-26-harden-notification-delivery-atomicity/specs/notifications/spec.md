## ADDED Requirements

### Requirement: Notification delivery is transaction-isolated and atomic

External notification delivery (Pushover push, SMS) SHALL be performed **outside** any open
database transaction, so that no database connection is held for the duration of a provider
network round-trip. The notification lifecycle transition `SENDING → SENT` / `SENDING → FAILED`
SHALL be idempotent: a delivery whose provider call already succeeded MUST NOT be re-sent on a
subsequent retry, and a notification MUST NOT remain indefinitely in `SENDING` after the provider
call has returned.

#### Scenario: Provider call holds no database connection

- **WHEN** the backend dispatches a notification through any channel (PUSH or SMS)
- **THEN** the `SENDING` state is committed in a short transaction that completes before the
  provider network call begins
- **AND** the provider HTTP/SMS call executes with no database transaction open and no pooled
  connection checked out for its duration
- **AND** the terminal `SENT` or `FAILED` state is written in a separate short transaction after
  the provider call returns

#### Scenario: Provider succeeds but the terminal status write fails

- **WHEN** the provider call succeeds but persisting the `SENT` state fails (e.g. transient DB error)
- **THEN** the notification is not left silently at `SENDING` such that the message was delivered
  but the record never reflects it
- **AND** a retry of that delivery, guided by the per-delivery idempotency key, does **not** send
  the message to the recipient a second time

#### Scenario: Burst dispatch does not exhaust the connection pool on slow providers

- **WHEN** `StaffAlertService`, `GuardianNotificationService`, or `DeferredNotificationScanner`
  dispatches to many recipients in a loop while the provider responds slowly
- **THEN** no database connection is held while waiting on a provider response
- **AND** unrelated database-backed requests are not blocked waiting for a free HikariCP connection
  on account of in-flight notification deliveries

#### Scenario: Failed delivery is recorded as FAILED, not lost

- **WHEN** the provider call fails (timeout, error response, or no delivery identity for the recipient)
- **THEN** the notification's terminal state is persisted as `FAILED`
- **AND** the failure is isolated per recipient and per channel, so one recipient's failure does not
  abort delivery to the others
