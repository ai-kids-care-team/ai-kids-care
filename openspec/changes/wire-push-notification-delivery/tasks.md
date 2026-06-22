## 1. 寻址模型治理：device_tokens → push_subscriptions（TDD）

- [x] 1.1 前置校验完成：`device_platform_enum` 仅被 `device_tokens`（V1/initdb/dbml 各处的该表定义）引用 → V7 可一并 DROP。并核实 initdb 镜像 V1 基线（两路径 V7 前都有 device_tokens）
- [x] 1.2 `PushSubscriptionConstraintTest`：唯一 `(user_id,provider,address)` 冲突 → `DataIntegrityViolationException` 且断言约束名 `uq_push_subscriptions_user_provider_address`（绿）
- [x] 1.3 `PushSubscription` 实体 + `PushProviderEnum` + `PushSubscriptionRepository`(`findByUser_IdAndProviderAndStatus`) + mapper + vo（vo 不暴露 address）
- [x] 1.4 Flyway `V7__replace_device_tokens_with_push_subscriptions.sql`：DROP device_tokens → DROP device_platform_enum → CREATE push_provider_enum + push_subscriptions（容器内执行成功、validate 通过）
- [x] 1.5 未改 initdb device_tokens、保留 seed 23；更新 `db/dbml/schema.dbml`（enum/table/ref 三处 → push_subscriptions）
- [x] 1.6 `DeviceToken*` 栈删除，新增 `PushSubscriptionService/Controller`（`/api/v1/push_subscriptions`），维持未发布/405（无 handler）
- [x] 1.7 容器内 section1 验证：context 启动、validate 通过、约束测试绿、既有 132 不受影响（133 tests 全绿）

## 2. Pushover 凭据配置化（TDD）

- [x] 2.1 `PushoverConfigValidationTest`：空白 api-token → `@NotBlank` 违例；非空通过（绿）
- [x] 2.2 `PushoverConfig`(`@ConfigurationProperties("pushover")`+`@Validated`) + `@EnableConfigurationProperties` 注册 + application.yml `pushover.api-token: ${PUSHOVER_API_TOKEN}` + application-test.yml 非密测试 token
- [x] 2.3 重构 `PushoverService`：构造注入 `PushoverConfig` + `PushoverClient`(由 `PushoverClientConfig` @Bean 提供)；以 `sendToUser(userKey,title,body)` 取代按参数传凭据的 8 参 `sendMessage`

## 3. PUSH 投递原语 + 生命周期（TDD；触发器延后）

- [x] 3.1 投递成功 → `SENT` + `sent_at`（`NotificationDispatchTest.push_success_marksSentWithTimestamp`，绿）
- [x] 3.2 投递失败 → `FAILED` + `fail_reason` + `retry_count++`（`push_deliveryFailure_marksFailedAndIncrementsRetry`，绿）
- [x] 3.3 无 active PUSHOVER 订阅 → `FAILED`、不调用 client（`push_noActiveSubscription_marksFailedWithoutCallingPushover`，绿）
- [x] 3.4 `NotificationService.dispatch` 实现（SENDING→解析订阅→Pushover→SENT/FAILED）；**已删 createNotification 的字面量空串**；未新增 HTTP 写端点（写仍 405，触发器留待规则引擎 change）

## 4. Spec 核对与验证（verification-before-completion）

- [x] 4.1 notifications spec delta 与实现一致（push_subscriptions 模型、Pushover 配置化投递、push-subscription 管理 API 仍 405）
- [x] 4.2 容器内 `gradle:8.7-jdk21` 全套件全绿：**141 tests / 2 skipped(@Disabled) / 0 failures**
- [x] 4.3 范围核对（git diff 确认）：产品改动仅 notifications 子系统 + config + application.yml + V7/dbml；SMS/EMAIL/规则引擎/触发器/管理 API 未触碰；秘密仅 `${ENV}`，测试用非密占位
- [ ] 4.4 requesting-code-review；按反馈修正
- [ ] 4.5 合并 develop / push / `/opsx:archive`（用户驱动，含 spec delta sync）

---

## ⚠️ 维护者批准项（apply 前必须确认 —— 含高风险 schema 删除）

> 本 change 含 **`DROP TABLE device_tokens`(+ 可能 `DROP TYPE device_platform_enum`)** 的 Flyway V7 迁移，属「删除/迁移/schema」高风险操作。实际风险低（生产该表为空：管理 API 从未发布、无 client 能写入、无派发读取），但仍为破坏性迁移。**apply 执行 V7 前须经维护者点头。** 另：本 change 是自迁移以来**首次改动后端 `src/main` 产品代码**。
