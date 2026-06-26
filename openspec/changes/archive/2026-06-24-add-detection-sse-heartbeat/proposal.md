## Why

闭环 ⑥ 的实时检测看板用 SSE 推送（commit `f2ec5d6`）。当前连接断开仅靠 `SseEmitter` 的 `onError/onTimeout` 回调被动触发——一个静默死掉的 TCP 连接（客户端崩溃、网络中断而未发 FIN）最坏要等到 `STREAM_TIMEOUT_MS`（30 分钟）才被驱逐，期间 `onIngested` 仍会尝试向它发送并在失败时才清理；同时缺少周期 keepalive，中间代理/NAT 的空闲超时也可能静默断流而服务端无感。需要周期心跳来**及时探测并驱逐死连接、保活链路**。

## What Changes

- `DetectionEventSseService` 新增 `@Scheduled sendHeartbeats()`，按可配间隔（默认 25s）向每个已注册 emitter 发送一个 SSE **comment/keepalive 帧**；发送失败即调用既有 `remove(...)` 驱逐该连接。
- `application.yml` 新增 `detection.sse.heartbeat-interval-ms`（默认 25000）；`application-test.yml` 调大（如 3600000）以禁止自动触发，测试直接调方法（沿用 `DeferredNotificationScanner` 模式）。
- 前端 `EventSource` **无需改动**（comment 帧被浏览器忽略，不触发 `onmessage`）。
- nginx **无需改动**（`proxy_read_timeout 3600s` 远大于 25s 心跳）。

## Capabilities

### New Capabilities
（无）

### Modified Capabilities
- `ai-detection`: 在既有「Backend pushes detection events to the frontend on ingest」推送能力上，新增一条 SSE 连接 keepalive 要求。新增独立 requirement，不改既有推送行为。

## Impact

- `backend/.../service/DetectionEventSseService.java`：新增一个 `@Scheduled` 方法 + 发送/驱逐逻辑。
- `backend/.../resources/application.yml` 与 `application-test.yml`：新增心跳间隔配置。
- 无 schema、无前端、无 nginx、无新依赖。`@EnableScheduling` 已在 `AiKidsCareApplication` 开启，无需新基础设施。
