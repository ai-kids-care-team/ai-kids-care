## 1. 测试脚手架

- [ ] 1.1 新建集成测试类 `GuardianNotificationConfirmDispatchE2EIT`（继承 `BaseIntegrationTest`，`@AutoConfigureMockMvc`，`@MockBean PushoverService`，`@RecordApplicationEvents`）
- [ ] 1.2 `@BeforeEach` 经 JDBC：`UPDATE detection_events SET status='OPEN'`（被测事件）、`DELETE FROM notifications WHERE event_id IN (...)`、`UPSERT push_subscriptions`（guardian user 121）；`seedTenantUser` 注入 kg1 的 KINDERGARTEN_ADMIN
- [ ] 1.3 复用 helper：`login` 取 `AI_KIDS_CARE_SESSION` cookie、`withCsrf` 附 XSRF token

## 2. Red → Green：三条路径（TDD）

- [ ] 2.1 [RED] `escalatedConfirmation_deliversImmediately`：先用「会失败的探针」确认测试确实驱动该异步路径（例如先断言一个相反期望或临时绕过监听看红），确保非假绿
- [ ] 2.2 [GREEN] 修正为 Awaitility `await().atMost(15s)` 轮询 `notifications`，断言 user 121 行 `status=SENT`；用 `@RecordApplicationEvents` 旁证 `EventReviewedEvent` 已发布
- [ ] 2.3 [RED→GREEN] `resolvedDuringQuietHours_isDeferred`：`@BeforeEach` 为 kg1 设必命中静默窗的 `notification_quiet_hours_json` → confirm RESOLVED → 断言行 `status=DEFERRED` 且未 dispatch
- [ ] 2.4 [RED→GREEN] `publicSpaceEvent_resolvesViaAffectedChildIds`：confirm 公共空间事件 + `affectedChildIds=[1]` → 断言家长经 affectedChildIds 解析并落库

## 3. 验证

- [ ] 3.1 DooD 全套件 `gradle cleanTest test`（参考 backend-test-dood-invocation）全绿，新增 3 scenario 通过
- [ ] 3.2 `git diff` 确认仅新增 test 文件、零产品代码/schema/seed 文件改动
