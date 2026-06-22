## Why

通知的 PUSH 投递路径是**生产隐患**：`NotificationService` 在 `channel == PUSH` 时给 `PushoverService` 传两个字面量空串作 apiToken/userId，而 `PushoverService` 现已加非空校验 —— 即任何 PUSH 通知**运行即抛** `IllegalArgumentException`。凭据从未配置化（application.yml 无 `pushover.*` 键）。

同时存在一处**数据模型债**：`device_tokens` 表按 FCM/APNS 直推模型设计（`platform=IOS|ANDROID` + `push_token` + 按设备唯一），但选定的投递通道是 **Pushover**（user-key 模型，平台无关，不存推送令牌）—— 二者架构不兼容。该表当前是**死表**：无派发路径读它、管理 API 未发布（405）故无 client 能写入、生产为空、代码引用仅限其自包含的 entity/repo/mapper/vo/service 桩。**现在是治理它最便宜的时刻**，发布注册 API、积累真实数据后迁移成本只会上升。

本 change 把 PUSH 投递原语**做对**，并把寻址模型一次治理干净，消除运行即抛的隐患，为后续规则引擎派发留下一个正确、可测的投递方法。

## What Changes

- **Pushover 凭据配置化**：新增 `PushoverConfig`（`@ConfigurationProperties(prefix="pushover")` + `@Validated` + `@NotBlank`，fail-fast）；application.yml 加 `pushover.api-token: ${PUSHOVER_API_TOKEN}`（沿用 rrn/internal.ai 的 `${ENV}` 秘密模式）。`PushoverService` 改为注入配置与（可注入的）client，去掉调用点的字面量空串。
- **寻址模型治理（方案 3，provider-aware）**：以 `push_subscriptions` 取代 `device_tokens` —— 字段 `user_id, provider(push_provider_enum: PUSHOVER), address(=Pushover user-key), device_label?(可选 Pushover 设备名), status, last_verified_at?, created_at`，唯一 `(user_id, provider, address)`。丢弃无意义的 `device_platform_enum`/`push_token` 等 FCM 残留假设。
  - **BREAKING（schema）**：Flyway V7 DROP `device_tokens`（+ 视情况 DROP 未被其他表使用的 `device_platform_enum`）、CREATE `push_provider_enum` + `push_subscriptions`；同步更新 `db/initdb` 建表与 seed（生产为空、demo seed 重建为 Pushover 形）。对应 entity `DeviceToken→PushSubscription`、repository（加 `findByUserIdAndProviderAndStatus` 等查询）、mapper、vo、`DeviceTokenService/Controller→PushSubscription*`（仍**不发布**管理 API，保持 405）。
- **PUSH 投递做对**：实现一个投递方法 —— 解析收件人的 active `push_subscriptions`(PUSHOVER) 地址，经 `PushoverService` 发送，并按 spec 落实投递生命周期（`QUEUED→SENDING→SENT`/`FAILED`、`sent_at`、`fail_reason`、`retry_count`）。`PushoverService` 重构为可注入 client 以便单测打桩。
- **测试（TDD）**：后端测试门已就绪 —— 为 `PushoverConfig` fail-fast、`push_subscriptions` 约束（唯一性、provider 枚举）、投递生命周期（成功/失败路径，Pushover client 打桩）补能力测试。

Non-goals（明确不做，各有未决上游/归属别处）：
- **不**做 SMS 投递（ADR-0018 未决；SMS 地址本就在 `users.phone`）、**不**做 EMAIL。
- **不**做规则引擎派发与 detection-event 触发（范围 B，受 staff 告警阈值未定 + ai-detection 闭环 ADR-0015 阻塞）—— 本 change 只产出「被调用就能正确投递」的原语，触发器留待规则引擎 change。
- **不**发布 device-token/push-subscription 与 notification-rule 管理 API（spec 现明确「故意未发布、应 405」，维持现状）。
- **不**改动通知读 API（已正确接线发布）。

## Capabilities

### New Capabilities
（无：均属既有 notifications 能力）

### Modified Capabilities
- `notifications`: ① PUSH 投递通道由「凭据未接线」→「配置化凭据 + 实现的 Pushover 投递路径」；② 寻址模型由「device_tokens 即投递地址（FCM/APNS 形）」→ provider-aware `push_subscriptions`（per-user Pushover user-key）；③「device token API not yet published」相关表述改为 `push_subscriptions` 管理 API 仍未发布；④ 落实投递生命周期状态机的实现承诺。SMS/EMAIL/规则引擎/阈值等 gap 表述保留。

## Impact

- **产品源码（首次自迁移以来改动后端 src/main）**：`PushoverService`、`NotificationService`（投递方法 + 去空串）、新 `PushoverConfig`；`DeviceToken*` → `PushSubscription*`（entity/repo/mapper/vo/service/controller）。
- **DB**：Flyway `V7__replace_device_tokens_with_push_subscriptions.sql` + `db/initdb/01_create_schema.sql` 与 seed（`23_*`）更新；新增 `push_provider_enum`，移除 `device_platform_enum`（若无其他引用）。**注意 ddl-auto=validate + initdb+baseline 测试路径**：迁移须与 initdb/实体三方一致，否则 context 启动失败（已有 `ContextLoadSmokeTest`/`FlywayMigrationSmokeTest` 会即时抓到）。
- **配置/部署**：application.yml 加 `pushover.*`；compose/部署需提供 `PUSHOVER_API_TOKEN`（非生产用测试 token，沿用 application-test.yml 的 `${ENV}` 注入；**绝不**提交真实凭据）。
- **测试**：新增能力测试，走已重建的 Testcontainers 地基与后端 CI 门。
- **spec**：notifications delta（随 change，archive 时 sync）。
- **可追溯性**：寻址模型方案 3 的取舍记于本 change `design.md`（新体制下不另起 ADR）。
