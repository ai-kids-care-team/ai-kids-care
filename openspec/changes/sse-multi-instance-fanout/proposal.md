> **状态：无限期搁置（DEFERRED，维护者 2026-06-30 裁定）。** 当前部署是单实例、无水平扩容/滚动部署的现实压力，「单实例假设」目前不痛；且本变更的 `shedlock` 表迁移版本号依赖 DB-1 schema squash 先落地。本草案作为设计资本保留、不进入活跃实现，待真正需要多实例（N≥2 副本）时再启动。届时须重新核对：DB-1 squash 后的 Flyway 版本号、ShedLock 与 squash 后 baseline 的兼容、以及下方各 Open Question。

## Why

Two documented single-instance assumptions block running more than one `backend/` replica, which the platform will need for horizontal scaling and zero-downtime rolling deploys:

1. **The live-dashboard SSE registry is process-local.** `DetectionEventSseService` holds open emitters in an in-process `ConcurrentHashMap<kindergartenId, Set<SseEmitter>>` and fans out a `DetectionEventIngestedEvent` only to emitters of the same JVM. With two instances, an event ingested on instance A is **never pushed** to a staff client whose SSE stream is pinned to instance B — the realtime alert closed loop silently drops for half the dashboards.
2. **The deferred-notification scanner is unguarded `@Scheduled`.** `DeferredNotificationScanner.scan()` runs every 60s on **every** instance. With N replicas a due `DEFERRED` guardian notification is dispatched up to N times → duplicate parent SMS/PUSH deliveries.

Both are explicitly flagged in code as "single-instance assumed — Redis pub/sub / ShedLock follow-up." This change relaxes that invariant so the realtime push and the deferred scanner stay correct under N≥2 replicas, **without weakening multi-tenant `kindergarten_id` isolation**.

## What Changes

- **SSE cross-instance fanout via Redis pub/sub.** On `DetectionEventIngestedEvent`, instead of (or in addition to) writing only to local emitters, the ingesting instance **publishes** a small envelope (`kindergartenId` + `eventId`) to a Redis channel. **Every** instance subscribes; on receiving the message each instance re-reads the `DetectionEventVO` (tenant-scoped) and writes it to its own local emitters for that kindergarten. The existing local-only path is replaced by publish→subscribe→local-fanout so a client connected to any instance receives the event.
- **Distributed lock on the deferred scanner via ShedLock.** `DeferredNotificationScanner.scan()` is wrapped so that at most one instance runs a given scan tick; the others skip. This adds the **new dependency `net.javacrumbs.shedlock`** and a **new `shedlock` table** (lock store) — a schema migration.
- Heartbeat, stream lifetime (30min), `Last-Event-ID` replay cap (200), and the bounded `@Async` executor remain per-instance and unchanged; replay still reads from PostgreSQL (authoritative), so it is already correct across instances.
- No change to the public SSE/REST contract, the AI→backend ingest contract, or notification channels.

## Capabilities

### New Capabilities
<!-- none — both behaviors already exist as spec'd requirements; this change modifies them -->

### Modified Capabilities
- `ai-detection`: the "Backend pushes detection events to the frontend on ingest" requirement currently scopes **cross-instance live fanout out of scope**; this change brings it in scope and adds a cross-instance fanout requirement (Redis pub/sub) with tenant-isolation scenarios.
- `notifications`: the quiet-hours deferral / scheduled-scanner requirement currently assumes a single scanner instance; this change adds a distributed-lock (single-dispatcher) requirement so the scanner does not duplicate deliveries across replicas.

## Impact

- **Code**: `DetectionEventSseService` (publish on ingest + Redis message listener → local fanout), a new Redis pub/sub config/bean reusing the existing `RedisConnectionFactory` (today only Spring Session + login throttle), `DeferredNotificationScanner` (`@SchedulerLock`), a ShedLock `LockProvider` bean + `@EnableSchedulerLock`.
- **Dependencies**: add `net.javacrumbs.shedlock:shedlock-spring` + `shedlock-provider-jdbc-template` (JDBC lock store on the authoritative PostgreSQL) to `backend/build.gradle`.
- **Schema (MAINTAINER APPROVAL REQUIRED)**: a new Flyway migration creating the `shedlock` table. JPA runs `ddl-auto: validate`, so the table must be mapped or excluded from validation; the migration must also be reflected in the `db/initdb` baseline so both assembly paths agree.
- **Config**: a Redis channel name / serialization setting; ShedLock default lock-at-most-for. Both are env/`application.yml` settings.
- **Ops**: relaxes the documented **single-instance assumption**; multi-instance becomes supported but Redis becomes a hard dependency for realtime fanout (it already is for sessions). Caddy SSE gzip-exclusion and sticky-session behavior are deployment concerns noted for follow-up.

## Non-goals

- **Not** building a durable, server-persisted per-subscriber delivered-cursor across a full cluster restart with no client connected (still covered by read-API history + `Last-Event-ID` replay).
- **Not** introducing Redis Streams / Kafka or any new message broker — reuse the existing Redis (Spring Session) connection only.
- **Not** changing the SSE wire protocol, event name (`detection-event`), heartbeat (25s), stream lifetime (30min), or replay cap (200).
- **Not** adding sticky-session/load-balancer affinity config or Caddy SSE routing changes (deployment follow-up).
- **Not** migrating the login-throttle or session stores; no business cache is introduced in Redis.
- **Not** addressing exactly-once notification delivery beyond de-duplicating the scanner tick; per-row `(kindergarten_id, dedupe_key)` dedupe already exists.
