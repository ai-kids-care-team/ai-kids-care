## 1. 配置

- [ ] 1.1 `application.yml` 新增 `detection.sse.heartbeat-interval-ms: 25000`（及 `heartbeat-initial-ms`）
- [ ] 1.2 `application-test.yml` 将心跳间隔设为大值（如 3600000）禁止自动触发，测试直接调方法

## 2. Red → Green：心跳 + 驱逐（TDD）

- [ ] 2.1 [RED] `DetectionEventSseServiceTest` 加 `sendHeartbeat_sendsKeepaliveFrameToRegisteredEmitter`：`registerEmitter(KG, mockEmitter)` → `sse.sendHeartbeats()` → `verify(emitter).send(any(SseEmitter.SseEventBuilder.class))`；方法不存在 → 编译/运行红
- [ ] 2.2 [GREEN] `DetectionEventSseService` 新增 `@Scheduled sendHeartbeats()`，遍历注册表发送 comment 帧
- [ ] 2.3 [RED] `sendHeartbeat_evictsEmitterOnFailure`：`doThrow(IOException).when(emitter).send(any())`，连发两次 → `verify(emitter, times(1)).send(...)`（第二次已被驱逐）
- [ ] 2.4 [GREEN] 发送 `try/catch` → 调用既有 `remove(...)` 驱逐

## 3. 验证

- [ ] 3.1 DooD 全套件 `gradle cleanTest test`（参考 backend-test-dood-invocation）全绿：新增 SSE 单测通过，既有 `DetectionEventStreamAuthTest`/`DetectionEventSseServiceTest` 不回归
- [ ] 3.2 `git diff` 确认仅改 `DetectionEventSseService` + 两个 yml，零前端/nginx/schema 改动
