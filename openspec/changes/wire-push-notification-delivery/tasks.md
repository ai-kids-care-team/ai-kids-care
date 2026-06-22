## 1. 寻址模型治理：device_tokens → push_subscriptions（TDD）

- [ ] 1.1 前置校验：grep 确认 `device_platform_enum` 仅被 `device_tokens` 引用（决定 V7 能否一并 DROP 该枚举）
- [ ] 1.2 [RED] 写 `push_subscriptions` 约束测试（唯一 `(user_id,provider,address)` 冲突 → `DataIntegrityViolationException` 且断言约束名；provider 枚举仅 PUSHOVER）—— 先红
- [ ] 1.3 实体/枚举/仓储/映射：`PushSubscription`（取代 `DeviceToken`）、`push_provider_enum`、`PushSubscriptionRepository`（加 `findByUserIdAndProviderAndStatus` 等）、mapper、vo
- [ ] 1.4 Flyway `V7__replace_device_tokens_with_push_subscriptions.sql`：DROP `device_tokens`（+ 若 1.1 通过则 DROP `device_platform_enum`）→ CREATE `push_provider_enum` → CREATE `push_subscriptions`
- [ ] 1.5 同步 `db/initdb/01_create_schema.sql`（fresh/demo 建表 + 枚举）与 seed `23_*`（重建为 Pushover 形最小占位或清空，不放误导性假 user-key）
- [ ] 1.6 `DeviceTokenService/Controller` → `PushSubscriptionService/Controller`（路径 `/api/v1/push_subscriptions`），**维持未发布/405**，不暴露 handler
- [ ] 1.7 [GREEN] 容器内：context 启动、`ddl-auto=validate` 通过（三方一致）、1.2 约束测试绿；`ContextLoadSmokeTest`/`FlywayMigrationSmokeTest` 仍绿

## 2. Pushover 凭据配置化（TDD）

- [ ] 2.1 [RED] `PushoverConfig` fail-fast 测试：`pushover.api-token` 空白 → 启动/绑定校验失败（`@Validated`+`@NotBlank`）—— 先红
- [ ] 2.2 `PushoverConfig`（`@ConfigurationProperties(prefix="pushover")`）+ application.yml `pushover.api-token: ${PUSHOVER_API_TOKEN}` + application-test.yml 加非密测试 token（仿 internal.ai 模式）
- [ ] 2.3 重构 `PushoverService`：构造注入 `PushoverConfig` + 可注入 `PushoverClient`（接口/薄 wrapper，生产 bean=`PushoverRestClient`）；移除按参数传入的凭据

## 3. PUSH 投递原语 + 生命周期（TDD；触发器延后）

- [ ] 3.1 [RED] 投递成功：打桩 client 返回成功 → `status=SENT` + `sent_at` 置位
- [ ] 3.2 [RED] 投递失败：打桩 client 抛错 → `status=FAILED` + `fail_reason` 非空 + `retry_count++`
- [ ] 3.3 [RED] 收件人无 active PUSHOVER 订阅 → `FAILED`（不调用 client、不发空地址）
- [ ] 3.4 [GREEN] 实现 `NotificationService` 投递方法（解析订阅地址→SENDING→Pushover→SENT/FAILED 生命周期）；**删除调用点字面量空串**；不新增 HTTP 写入端点（写操作仍 405，触发器留待规则引擎 change）

## 4. Spec 核对与验证（verification-before-completion）

- [ ] 4.1 核对 notifications spec delta 与实现一致（push_subscriptions 模型、Pushover 配置化投递、push-subscription 管理 API 仍 405）
- [ ] 4.2 容器内 `gradle:8.7-jdk21` 实跑**全套件**全绿（既有 132 + 新增），留存证据
- [ ] 4.3 范围核对：SMS/EMAIL/规则引擎/触发器/管理 API 未触碰；产品改动限于 notifications 子系统 + 该 schema；秘密仅 `${ENV}`、无真实凭据提交
- [ ] 4.4 requesting-code-review；按反馈修正
- [ ] 4.5 合并 develop / push / `/opsx:archive`（用户驱动，含 spec delta sync）

---

## ⚠️ 维护者批准项（apply 前必须确认 —— 含高风险 schema 删除）

> 本 change 含 **`DROP TABLE device_tokens`(+ 可能 `DROP TYPE device_platform_enum`)** 的 Flyway V7 迁移，属「删除/迁移/schema」高风险操作。实际风险低（生产该表为空：管理 API 从未发布、无 client 能写入、无派发读取），但仍为破坏性迁移。**apply 执行 V7 前须经维护者点头。** 另：本 change 是自迁移以来**首次改动后端 `src/main` 产品代码**。
