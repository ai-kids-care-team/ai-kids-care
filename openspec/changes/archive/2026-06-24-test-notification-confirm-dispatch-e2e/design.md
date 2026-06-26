## Context

闭环 ③a 链路（commit `dafdda6`）：

1. `EventReviewService.confirm(EventReviewCreateDTO)` `@Transactional` `@PreAuthorize(EVENT_REVIEW_WRITE)`——写 `EventReview` + 更新 `detection_events.status`，提交前 `publishEvent(EventReviewedEvent)`（`EventReviewService.java:71`）。
2. `EventReviewedEvent`（record，字段 `eventId/kindergartenId/resultStatus/roomId/detectedAt/affectedChildIds/notifyGuardians`）——`roomId/detectedAt` 在实体仍受管时即时取出，避免异步监听里 lazy-load。
3. `GuardianNotificationService.onEventReviewed(...)` `@Async @TransactionalEventListener(phase = AFTER_COMMIT)`（`GuardianNotificationService.java:59`）→ 同步 overload `notifyOnReview(...)`。
4. `resolveRecipients(...)`：教室事件经 `class_room_assignment → child_class_assignment → child_guardian_relationship → user` 解析家长；公共空间事件直接用 `affectedChildIds`。
5. `NotificationService.dispatch(...)` → 保存 `Notification` → `PushoverService.sendToUser(...)`；dedupe key `evt-{eventId}-u-{userId}-guardian`（DB 唯一约束静默去重）。
6. ③b 分支：`RESOLVED` 时查 `QuietHoursService.resolveQuietWindow(...)`，命中静默则落 `DEFERRED` 并跳过 dispatch；`ESCALATED` 永远即时。

**测试现状与 gap：** `GuardianNotificationServiceTest` 直接调 `notifyOnReview()`（同步，绕过异步边界）；`EventReviewApiTest` 只测 HTTP 契约不断言通知；`DetectionIngestApiTest:155-160` 已用 Awaitility 轮询 `notifications` 验证另一条 `@Async @TransactionalEventListener` 副作用。缺的是：HTTP 复核入口 → 异步落库的整链断言。

## Goals / Non-Goals

**Goals:**
- 端到端回归网，真实跨越 `@Async @TransactionalEventListener(AFTER_COMMIT)` 边界。
- 覆盖 ESCALATED 即时、RESOLVED quiet-hours、公共空间 `affectedChildIds` 三条路径。

**Non-Goals:**
- 不改任何产品行为/代码。
- 不打真实 Pushover（`@MockBean PushoverService`）。
- 不测 ③b 的 `DeferredNotificationScanner` 补发（独立路径，已有 `DeferredNotificationScannerTest`）。
- 不引入多实例/ShedLock 断言（独立 change）。

## Decisions

- **D1 测试形态**：`@SpringBootTest(RANDOM_PORT)` + `@AutoConfigureMockMvc`，继承 `BaseIntegrationTest`（共享 Testcontainers postgres+redis），`@MockBean PushoverService` 让 dispatch 在进程内到达 `SENT`。沿用 `seedTenantUser/login/withCsrf` helper、会话 cookie `AI_KIDS_CARE_SESSION`。
- **D2 异步断言机制**：用 **Awaitility** `await().atMost(15s).untilAsserted(...)` 轮询 `notifications` 表（沿用 `DetectionIngestApiTest`）。`@RecordApplicationEvents` 只能断言 `EventReviewedEvent` 已发布、**等不到** 异步监听落库，故仅作发布断言的补充，不作主断言。备选「自定义同步 AsyncConfigurer」被否：会改变被测的真实异步语义。
- **D3 fixture**：复用 seed `event#1`（kg1，room1 → class1 → child1 → guardian `user 121`/`guardian-kg1`）。`@BeforeEach` 经 JDBC：`UPDATE detection_events SET status='OPEN' WHERE id=1`、`DELETE FROM notifications WHERE event_id IN (...)`、`UPSERT push_subscriptions` for user 121（seed 无此行，`GuardianNotificationServiceTest` 亦在 `@BeforeEach` 插）。**不改 seed 文件**，故无需 `cleanTest`（但本 change 是新测试类，照常会被编译进 test 输入）。
- **D4 三个 scenario**：
  - ESCALATED：confirm `{eventId:1, resultStatus:"ESCALATED", notifyGuardians:true}` → Awaitility 断言 user 121 的 `notifications` 行 `status=SENT`。
  - RESOLVED + quiet-hours：`@BeforeEach` 设 kg1 `notification_quiet_hours_json` 为「当前时刻落在静默窗」→ confirm RESOLVED → 断言行 `status=DEFERRED`（dispatch 在异步方法内同步跳过，可不依赖 Awaitility，但仍用短轮询等行出现）。
  - 公共空间：confirm `{eventId:<public room event>, affectedChildIds:[1]}` → 断言家长经 affectedChildIds 解析、落库。

## Risks / Trade-offs

- **共享 testcontainer 状态串扰** → `@BeforeEach` 清理 `notifications` + 重置 status 隔离；dedupe key 防重复行。
- **Awaitility flaky（异步未及时）** → `atMost(15s)` 与既有一致；CI/DooD 下足够。
- **quiet-hours 时刻依赖测试运行时钟** → 用「跨午夜全覆盖窗」或基于 `QuietHoursService` 注入的时区构造，确保任意运行时刻都命中（design 阶段标注，apply 时按 `QuietHoursService` 接口确定具体构造）。
- **改 seed 风险** → 本 change 不改 seed 文件，仅 `@BeforeEach` JDBC 改运行时数据。

## Migration Plan

纯测试，无部署影响。回滚 = 删除测试类。

## Open Questions

- quiet-hours scenario 的「当前必命中」窗口如何构造最稳？→ apply 时依 `QuietHoursService.resolveQuietWindow` 实际签名定（可能注入 Clock 或用全天窗）。
- 公共空间 scenario 用哪条 seed 事件（room3 놀이터 event#2 status OPEN）→ apply 时核对 `42_detection_events_seed.sql`。
