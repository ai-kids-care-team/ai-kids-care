## Context

Today `backend/` is deployed as a single replica and two realtime features rely on that assumption:

- **`DetectionEventSseService`** keeps open `SseEmitter`s in a process-local `ConcurrentHashMap<Long /*kindergartenId*/, Set<SseEmitter>>`. On a `DetectionEventIngestedEvent` (a plain `ApplicationEvent` consumed by `@Async @EventListener`, because ingest is autocommit / non-transactional) it re-reads the tenant-scoped `DetectionEventVO` via `detectionEventService.getForPush(eventId, kindergartenId)` and writes it to that kindergarten's local emitters only. SSE `id:` = `event_id`; reconnect replay (`Last-Event-ID`, cap 200) reads from PostgreSQL; heartbeat every 25s; stream lifetime 30min.
- **`DeferredNotificationScanner.scan()`** is a `@Scheduled(fixedDelay 60s)` job that selects `status = DEFERRED AND deferred_until <= now()` and calls `notificationService.dispatch(n)` per row.

Existing Redis usage is **Spring Session (indexed)** + **login-throttle counters** (`LoginThrottleService`), wired through Spring Boot's auto-configured `RedisConnectionFactory` (`spring.data.redis.{host,port,password}` in `application.yml`). No business cache, no pub/sub today. PostgreSQL is the system-of-record; Flyway + `db/initdb` baseline are reconciled via `baseline-on-migrate`; JPA runs `ddl-auto: validate`.

The realtime push closed loop (CCTV → AI → ingest → SSE dashboard) and the quiet-hours deferral loop must stay correct when we run N≥2 replicas behind Caddy.

## Goals / Non-Goals

**Goals:**
- A detection event ingested on **any** instance reaches **every** open dashboard of that kindergarten, regardless of which instance the client's SSE stream is pinned to.
- A due `DEFERRED` notification is dispatched **exactly once per scan tick across the cluster**, not once per instance.
- Preserve multi-tenant isolation: a fanned-out event for KG A MUST NOT reach KG B's emitters, on any instance.
- Reuse the existing Redis connection infra and PostgreSQL; add no new broker.
- Keep the SSE wire contract (event name, ids, heartbeat, replay cap, stream lifetime) byte-identical.

**Non-Goals:**
- Durable per-subscriber delivered cursor across a full cluster restart (read-API history + `Last-Event-ID` replay remain the recovery path).
- Exactly-once end-to-end notification delivery beyond de-duplicating the scanner tick.
- Sticky-session/affinity or Caddy SSE-routing config (deployment follow-up).
- Multi-instance fanout for any stream other than detection events.

## Decisions

### D1. Redis Pub/Sub for SSE fanout (chosen) vs PostgreSQL LISTEN/NOTIFY vs Redis Streams

Publish a tiny envelope on ingest; all instances subscribe and re-fan to their own local emitters.

- **Why Redis Pub/Sub**: Redis is already a hard dependency (sessions); Spring Data Redis ships `RedisMessageListenerContainer` + `RedisTemplate.convertAndSend`. Fire-and-forget pub/sub matches the existing "best-effort live push, durable recovery via replay" model — a missed message during a Redis blip is recovered by `Last-Event-ID` replay from PostgreSQL, exactly as a client reconnect is today.
- **Rejected — PostgreSQL LISTEN/NOTIFY**: the existing requirement explicitly forbids LISTEN/NOTIFY ("backend is sole writer, knows immediately"); reusing it for cross-instance would reintroduce a DB-coupled notify path and a dedicated listening connection.
- **Rejected — Redis Streams**: adds consumer-group/offset durability we do not need (replay already handled by PG) and more operational surface.

### D2. Channel granularity — single channel with tenant routing inside the envelope (chosen) vs per-kindergarten channel

- **Chosen**: one fixed channel (e.g. `detection-events:ingested`). Envelope = `{ kindergartenId, eventId }` (the same minimal pair the in-process `DetectionEventIngestedEvent` already carries — no PII, no VO). Each instance, on receive, looks up only its **own** local emitter set for that `kindergartenId` and fans out; if it has no emitters for that tenant it does nothing (and skips the DB read). Tenant routing is by the `kindergartenId` field, identical to the current local `emittersByKindergarten.get(...)` lookup.
- **Why not per-kindergarten channel** (`detection-events:{kgId}`): would require each instance to dynamically `subscribe/unsubscribe` channels as emitters open/close per tenant (hundreds of kindergartens → churny subscription management) for a marginal bandwidth win. A single channel keeps subscription static and the tenant predicate stays in code where it already lives. **(Open question OQ1 revisits this if fanout volume is high.)**
- **Tenant-safety invariant**: the receiving instance re-reads the VO with `getForPush(eventId, kindergartenId)` (already tenant-scoped in SQL) AND only writes to `emittersByKindergarten.get(kindergartenId)`. An emitter is only ever registered under its subscriber's own active kindergarten (unchanged). So a KG-A envelope can only ever resolve KG-A's VO and reach KG-A emitters — even though all tenants share one channel.

### D3. Where the publish happens — replace local fanout in the `@Async @EventListener`

The `onIngested(DetectionEventIngestedEvent)` listener currently does the local fanout. We change it to **publish to Redis** instead of fanning out locally. A new `@Async` Redis message handler performs the local fanout (the current loop body: re-read VO, write to local emitters, evict on failure). This means the ingesting instance also receives its own published message and fans out to its own local clients — uniform path, no special-casing "local vs remote." Self-delivery latency is one Redis round-trip (sub-ms locally), acceptable and keeps one code path.

- Serialization: JSON via `Jackson2JsonRedisSerializer` (or `GenericJackson2`) for the 2-field envelope on a dedicated `RedisTemplate`; the message body is non-PII (two longs).

### D4. ShedLock (JDBC provider) for the deferred scanner

- Wrap `scan()` with `@SchedulerLock(name = "deferredNotificationScan", lockAtMostFor = "PT55S", lockAtLeastFor = "PT0S")`; add `@EnableSchedulerLock(defaultLockAtMostFor = ...)`.
- **Provider = JDBC on PostgreSQL** (`shedlock-provider-jdbc-template`), not the Redis provider: the lock is about guarding a **DB-row dispatch** job; PostgreSQL is authoritative and already the transactional store the scanner reads/writes. Keeping the lock in the same store the work touches avoids a split-brain where the lock store (Redis) and the work store (PG) disagree.
- `lockAtMostFor` (55s) < scan interval (60s) bounds a crashed-holder's lock so the next tick on a survivor proceeds; `lockAtLeastFor = 0` because per-row dispatch is already idempotent-ish via `(kindergarten_id, dedupe_key)` and status transitions, so we only need to prevent concurrent ticks, not enforce a minimum hold.
- **Requires the `shedlock` table** (`name PK, lock_until, locked_at, locked_by`) — a Flyway migration (see Migration Plan). Because `ddl-auto: validate`, the table must either be excluded from Hibernate validation (it is not a JPA `@Entity`, so it is invisible to validation — no mapping needed) — confirm at apply time.

## Risks / Trade-offs

- **Redis down → no live fanout** → Mitigation: publish failure is logged and swallowed; clients recover missed events on their next reconnect via `Last-Event-ID` replay from PostgreSQL (same as today's client-disconnect recovery). Sessions also depend on Redis, so a Redis outage already degrades the app; fanout degrades no worse.
- **Duplicate local fanout if both old local path and new pub/sub path run** → Mitigation: the local fanout is **moved** into the Redis message handler; the ingest listener only publishes (no double delivery on the ingesting instance).
- **All tenants share one channel → every instance wakes on every event** → Mitigation: the per-instance handler short-circuits (no DB read, no work) when it holds no emitters for that `kindergartenId`; envelope is 2 longs. Revisit per-tenant channels only if profiling shows wakeup cost matters (OQ1).
- **ShedLock clock skew across instances** → Mitigation: ShedLock JDBC uses the **DB** clock (`lock_until` computed by the DB), not instance clocks, so inter-instance skew does not affect correctness.
- **Schema migration on a live system** → Mitigation: `CREATE TABLE shedlock` is additive/non-breaking; gated on maintainer approval; reflected in `db/initdb` baseline so both assembly paths validate. Requires `./gradlew cleanTest test` after the seed/baseline touch.
- **Self-delivery via Redis adds a hop to same-instance pushes** → Trade-off accepted for a single uniform fanout path; latency is a local Redis round-trip.

## Migration Plan

1. Add ShedLock deps + Redis pub/sub config behind no feature flag needed (behavior is backward-compatible at N=1).
2. **MAINTAINER-APPROVAL-GATED**: add Flyway migration `V<next>__create_shedlock_table.sql` (next sequential version after the current baseline — assign at apply time; the repo is mid-squash to a single `V1__initial_baseline.sql`, so the maintainer fixes the final number) AND mirror it into `db/initdb` baseline. Run `./gradlew cleanTest test` (seed is a testcontainer fixture not in the `test` task inputs).
3. Deploy is rolling-safe: at N=1 the new path behaves identically (instance publishes, same instance receives and fans out). Scaling to N≥2 then "just works."
4. **Rollback**: revert code + deps; the `shedlock` table can be left in place (harmless) or dropped in a follow-up migration. No data migration, no backfill.

## Open Questions

- **OQ1 — channel granularity**: single channel + in-code tenant routing (chosen) vs per-kindergarten channels. Decision pending expected fanout volume and number of active tenants per instance; revisit if every-instance wakeups become measurable.
- **OQ2 — message serialization / schema evolution**: exact envelope serializer (JSON vs a compact form) and whether to version the envelope for forward/backward compatibility during rolling deploys (mixed-version instances on one channel).
- **OQ3 — replay-after-failover semantics**: when a client's instance dies and `EventSource` reconnects (likely to a different instance), is `Last-Event-ID` replay from PostgreSQL sufficient for the gap, or are there ordering edge cases between a replayed frame and an in-flight pub/sub frame on the new connection? (The existing `openStream` replay-before-register ordering is per-connection and should hold, but cross-instance timing needs validation.)
- **OQ4 — ShedLock lock store**: confirm JDBC-on-PostgreSQL over the Redis provider; confirm `lockAtMostFor`/`lockAtLeastFor` values against real dispatch durations.
- **OQ5 — Caddy/SSE deployment**: does multi-instance need sticky sessions for SSE, and does the existing gzip-exclusion for SSE paths still hold per-instance? (Deployment follow-up, flagged not solved here.)
- **OQ6 — `ddl-auto: validate` interaction**: confirm the non-`@Entity` `shedlock` table is invisible to Hibernate validation (expected) and needs no `@Table` mapping or validation exclusion.
