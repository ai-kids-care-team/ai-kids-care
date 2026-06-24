## Why

闭环 ③a（复核确认 → 家长 PUSH）的完整异步链路目前**只有单元级覆盖**：`GuardianNotificationServiceTest` 直接调用同步 overload `notifyOnReview(...)`，绕过了 `@Async @TransactionalEventListener(AFTER_COMMIT)` 这条真实的事务/线程边界；`EventReviewApiTest` 验证了 HTTP 复核契约却不断言任何通知落库。也就是说，从 `POST /api/v1/event_reviews` 到 `notifications` 表落库这条**端到端链路没有回归网**——而它恰是整个通知闭环里最易因重构（事件字段、监听器相位、关系图解析）而静默回归的一跳。

## What Changes

- 新增一个集成测试类，覆盖「认证后的 HTTP 复核确认 → `EventReviewedEvent` 发布 → AFTER_COMMIT 异步监听 → 关系图解析家长 → `NotificationService.dispatch` → `notifications` 落库」的完整链路。
- 覆盖三条关键路径：
  - `ESCALATED` 强制即时通知 → 终态 `SENT`；
  - `RESOLVED` 命中 quiet-hours → 落 `DEFERRED` 且跳过 dispatch（③b 行为）；
  - 公共空间事件（无班级图）→ 经 `affectedChildIds` 解析家长。
- 用 Awaitility DB 轮询处理异步监听边界（沿用 `DetectionIngestApiTest` 已确立的模式）；`@RecordApplicationEvents` 仅用于断言事件已发布。
- **无产品代码变更、无 schema 迁移**——纯测试补网。

## Capabilities

### New Capabilities
（无）

### Modified Capabilities
- `notifications`: 为既有 `Guardian notification on review confirmation` 行为补充一条 spec 层要求——该链路 SHALL 有端到端集成验证。新增独立 requirement，不改既有行为。

## Impact

- `backend/src/test/...`：新增 1 个集成测试类（继承 `BaseIntegrationTest`，`@MockBean PushoverService`）。
- 可能在 `@BeforeEach` 通过 JDBC 重置 `detection_events.status`、清理 `notifications`、插入 `push_subscriptions`（不改 seed 文件本身）。
- 无产品代码、无 schema、无 API、无依赖变更。
