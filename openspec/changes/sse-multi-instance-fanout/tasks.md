## 1. Dependencies & schema (maintainer-approval-gated)

- [ ] 1.1 Add `net.javacrumbs.shedlock:shedlock-spring` and `net.javacrumbs.shedlock:shedlock-provider-jdbc-template` to `backend/build.gradle`
- [ ] 1.2 **[MAINTAINER APPROVAL REQUIRED — schema migration]** Add Flyway migration `V<next>__create_shedlock_table.sql` creating the `shedlock` table (`name PK, lock_until, locked_at, locked_by`); assign the next sequential version after the current baseline at apply time
- [ ] 1.3 **[MAINTAINER APPROVAL REQUIRED — seed/baseline]** Mirror the `shedlock` table into the `db/initdb` baseline so both assembly paths agree under `baseline-on-migrate`
- [ ] 1.4 Confirm `ddl-auto: validate` does not flag `shedlock` (non-`@Entity`, invisible to Hibernate validation); document the result (resolves OQ6)

## 2. ShedLock wiring for the deferred scanner

- [ ] 2.1 Add a `LockProvider` bean (JdbcTemplateLockProvider on the PostgreSQL `DataSource`) and `@EnableSchedulerLock(defaultLockAtMostFor = ...)`
- [ ] 2.2 Annotate `DeferredNotificationScanner.scan()` with `@SchedulerLock(name = "deferredNotificationScan", lockAtMostFor = "PT55S", lockAtLeastFor = "PT0S")`
- [ ] 2.3 Verify per-row dispatch semantics are unchanged (best-effort, `SENDING`→`SENT`/`FAILED`, `(kindergarten_id, dedupe_key)` dedupe)

## 3. Redis pub/sub config for SSE fanout

- [ ] 3.1 Add a dedicated `RedisTemplate`/serializer for the `{kindergartenId, eventId}` envelope reusing the existing `RedisConnectionFactory` (no new connection)
- [ ] 3.2 Add a `RedisMessageListenerContainer` subscribing every instance to the shared detection-events channel; make the channel name configurable in `application.yml`
- [ ] 3.3 Define the envelope type (two longs, no PII) and its (de)serialization

## 4. Fanout path in DetectionEventSseService

- [ ] 4.1 Change `onIngested(DetectionEventIngestedEvent)` to **publish** the envelope to Redis instead of fanning out locally
- [ ] 4.2 Add an `@Async` Redis message handler that, per received envelope, re-reads the tenant-scoped VO via `getForPush(eventId, kindergartenId)` and fans out to local emitters for that `kindergartenId` only (reusing existing per-emitter send + eviction)
- [ ] 4.3 Ensure no double delivery on the ingesting instance (single uniform receive→fanout path)
- [ ] 4.4 Make publish/receive failures best-effort: log without PII, never fail ingest; keep heartbeat / 30min lifetime / replay-cap per-instance and unchanged

## 5. Tests

- [ ] 5.1 Unit/integration test: envelope published on ingest; receiver fans out only to the matching `kindergartenId` emitters (tenant isolation — KG A envelope never reaches KG B emitters)
- [ ] 5.2 Test: single-instance behaves identically (publish→self-receive→one delivery; wire contract unchanged)
- [ ] 5.3 Test: Redis publish failure does not fail ingest; missed event recoverable via `Last-Event-ID` replay
- [ ] 5.4 Test: ShedLock — concurrent scan ticks result in exactly one dispatch; contender skips; due vs not-yet-due rows handled correctly
- [ ] 5.5 Run `./gradlew cleanTest test` (seed/baseline changed → not in `test` task inputs)

## 6. Docs & follow-up

- [ ] 6.1 Update CLAUDE.md notes that flag the single-instance SSE registry and ShedLock follow-up (assumption now relaxed)
- [ ] 6.2 Record deployment follow-ups not solved here: Caddy SSE routing / sticky sessions for multi-instance (OQ5)
- [ ] 6.3 Resolve open questions OQ1–OQ4 (channel granularity, envelope versioning, replay-after-failover, lock-store/tuning) before or during review
