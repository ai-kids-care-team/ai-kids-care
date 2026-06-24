## ADDED Requirements

### Requirement: Review-confirmation guardian notification is verified end-to-end

The system SHALL have integration test coverage proving that an authenticated review confirmation submitted over HTTP results in guardian notification rows reaching their correct terminal state through the asynchronous `AFTER_COMMIT` transactional event path — exercising the real `@Async @TransactionalEventListener` boundary rather than calling the dispatch service directly.

#### Scenario: ESCALATED confirmation delivers immediately

- **WHEN** an authorized KINDERGARTEN_ADMIN confirms a review with `resultStatus = ESCALATED` for a classroom detection event via `POST /api/v1/event_reviews`
- **THEN** a notification row for each resolved guardian SHALL eventually reach status `SENT`, observed by polling the notifications table after the asynchronous listener completes

#### Scenario: RESOLVED confirmation during quiet hours is deferred

- **WHEN** a review is confirmed with `resultStatus = RESOLVED` while the kindergarten is within its configured quiet-hours window
- **THEN** the guardian notification row SHALL be persisted with status `DEFERRED` and immediate dispatch SHALL be skipped

#### Scenario: Public-space event resolves recipients from affectedChildIds

- **WHEN** a review is confirmed for a public-space detection event (no active classroom assignment) with an explicit `affectedChildIds` list
- **THEN** guardians SHALL be resolved from `affectedChildIds` rather than the room→class→child graph, and the resulting notification rows SHALL be persisted
