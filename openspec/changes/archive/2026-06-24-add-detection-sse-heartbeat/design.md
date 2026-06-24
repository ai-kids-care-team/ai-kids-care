## Context

`DetectionEventSseService` 现状：

- 注册表 `Map<Long, Set<SseEmitter>> emittersByKindergarten`（值为 `ConcurrentHashMap.newKeySet()`），按 `kindergartenId` 分桶。
- `STREAM_TIMEOUT_MS = 30 * 60 * 1000`（30 分钟）。
- `register/registerEmitter`（后者 package-private，是测试注入 mock emitter 的 seam）wiring `onCompletion/onTimeout/onError → remove`。
- `@Async @EventListener onIngested(DetectionEventIngestedEvent)`：遍历本园 emitter 发送 `detection-event` 帧，发送抛异常即 `remove(...)` 驱逐。

基础设施：`AiKidsCareApplication` 已带 `@EnableScheduling` + `@EnableAsync`；`DeferredNotificationScanner.scan()` 是现成的 `@Scheduled(fixedDelayString=..., initialDelayString=...)` 范式，test profile 用大间隔禁自动触发、测试直接调方法。nginx `/stream` location `proxy_read_timeout 3600s`、`proxy_buffering off`。

## Goals / Non-Goals

**Goals:**
- 周期 keepalive 帧保活 SSE 链路，击穿中间代理/NAT 空闲超时。
- 死连接发送失败时**及时驱逐**，不必等 30 分钟 `STREAM_TIMEOUT_MS`。

**Non-Goals:**
- 不改 `STREAM_TIMEOUT_MS` 语义。
- 不做多实例 SSE fanout（Redis pub/sub，独立 high-risk change）。
- 不改前端协议或 nginx 配置。
- 不做持久「已投递游标」catch-up（独立 change）。

## Decisions

- **D1 帧类型 = SSE comment 帧**（`SseEmitter.event().comment("hb")`）：浏览器 `EventSource` 忽略 comment 帧，不触发 `onmessage`，但数据仍走完整 TCP 路径、重置 nginx `proxy_read_timeout` 与 NAT 空闲计时。备选 `.name("heartbeat").data("")` 会让客户端观测到事件——无必要的协议噪音，否决。
- **D2 调度范式**：`@Scheduled(fixedDelayString = "${detection.sse.heartbeat-interval-ms:25000}", initialDelayString = "${detection.sse.heartbeat-initial-ms:25000}")`，照搬 `DeferredNotificationScanner`；`application-test.yml` 设大间隔禁自动触发，单测直接调 `sendHeartbeats()`。复用已开启的 `@EnableScheduling`，无新 config 类。
- **D3 间隔选择**：默认 25s。25s ≪ nginx `proxy_read_timeout 3600s`（~144× 余量），≪ `STREAM_TIMEOUT_MS` 30min，且 < 多数 NAT/LB 60–90s 空闲超时。可配以便部署调优。
- **D4 失败驱逐**：发送 `try/catch` → 调用既有 `remove(kindergartenId, emitter)`，与 `onIngested` 的驱逐逻辑一致复用。这是本 change 的主价值——把「静默死连接」的清理从「最长 30min」缩短到「最多一个心跳周期」。

## Risks / Trade-offs

- **心跳风暴（大量连接）** → comment 帧极小、间隔 25s，开销可忽略；遍历用并发集合安全。
- **遍历时并发增删 emitter** → 注册表是 `ConcurrentHashMap` + `newKeySet`，迭代弱一致、安全；驱逐用既有 `remove`。
- **多实例下各实例只心跳自己进程内的连接** → 本就是进程内注册表，无跨实例语义问题（多实例 fanout 是另一个 change 的范畴）。
- **心跳与 `onIngested` 并发向同一 emitter send** → `SseEmitter.send` 线程安全由 Spring 保证；失败侧都走 `remove`，幂等。

## Migration Plan

新增配置项带安全默认值，无 schema。部署即生效。回滚 = 移除 `@Scheduled` 方法与配置项。

## Open Questions

- 心跳间隔默认值 25s 是否合适生产 NAT 环境？→ 设为可配，默认 25s，部署可调。
- 是否需要在心跳里顺带清理「超过 N 个周期无活动」的连接？→ 本期仅「发送失败即驱逐」，更激进的空闲回收留后续（避免过度设计）。
