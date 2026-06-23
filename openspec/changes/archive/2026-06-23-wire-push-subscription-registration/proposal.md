## Why

上一个 change ship 了 PUSH 投递链路（`PushoverConfig` 配置化 + `NotificationService.dispatch` 生命周期 + `push_subscriptions` 寻址模型），但留了一个端到端缺口：**没有任何途径往 `push_subscriptions` 写入收件人的 Pushover user-key**。`PushSubscriptionController` 是空壳（405）、`PushSubscriptionService` 是 `denyAll()` 读桩。结果是投递原语永远命中「no active Pushover subscription → FAILED」——接好但用不了。

「notifications 余项」的其余部分（规则引擎触发、SMS、EMAIL、staff 阈值、guardian 闸门）均被上游阻塞（ADR-0015 检测闭环未实现、ADR-0018 SMS 未决、阈值未定）。本 change 只做其中**唯一无阻塞、最高价值**的一项：发布 `push_subscriptions` 的**自助管理 API**，让用户登记自己的 Pushover user-key，使 PUSH 投递端到端可用。

## What Changes

- **发布 push_subscriptions 自助管理 API**（per-user，自作用域）：
  - `POST /api/v1/push_subscriptions` —— 登记调用者自己的推送订阅（provider=PUSHOVER、address=Pushover user-key、可选 device_label）；`user_id` 取自会话身份（**不信客户端**），status=ACTIVE。
  - `GET /api/v1/push_subscriptions` —— 列出**调用者自己**的订阅（VO 不含 address 秘密）。
  - `PUT /api/v1/push_subscriptions/{id}` —— 更新自己订阅的 address/device_label/status。
  - `DELETE /api/v1/push_subscriptions/{id}` —— 删除自己的订阅。
  - 跨用户访问他人订阅 → 隐藏 404；重复 `(user_id,provider,address)` → 409。
- **授权**：新增 `AuthorizationAction.PUSH_SUBSCRIPTION_MANAGE`，对**任一已认证用户**放行（push_subscriptions 无 kindergarten_id、纯 user-scoped，任何角色含 PLATFORM 都可管自己的）；细粒度「只能管自己的」由 service 用 `EffectiveAuthorizationContext.userId()` 在查询内强制（镜像 NotificationService 自作用域模式）。
- **provider 限制**：仅接受 `PUSHOVER`（唯一已实现推送商）；其它值 → 400。
- **DTO/Service/Controller**：新增 `PushSubscriptionRegisterDTO`（+ 可能 update DTO）；`PushSubscriptionService` 加 register/update/delete + 自作用域 list（替换 denyAll）；`PushSubscriptionController` 加四个 handler；`PushSubscriptionMapper` 加 toEntity（遵守 INC-005：为 id/user/status/createdAt/lastVerifiedAt 等显式 `ignore`/服务端设值）。
- **更新 notifications spec**：把「Push subscription management API not yet published（405）」requirement 翻转为「已发布的自助管理 API」。

Non-goals（明确不做，均被阻塞或属其它 change）：
- **不**做规则引擎评估/检测触发器（ADR-0015 阻塞）、**不**做 guardian-review 闸门、**不**做 staff 高置信阈值（spec 未定阈值）。
- **不**做 SMS（ADR-0018）/ EMAIL。
- **不**发布 notification-rule 管理 API（仍 405，留待规则引擎 change）。
- **不**改 PUSH 投递原语本身（上 change 已交付）；不改通知读 API。

## Capabilities

### New Capabilities
（无：属既有 notifications 能力）

### Modified Capabilities
- `notifications`: 将「Push subscription management API not yet published」requirement 翻转为「Push subscription self-service management API（已发布）」——用户自助登记/更新/删除自己的 Pushover 投递身份；自作用域、不信客户端 user_id、provider 限 PUSHOVER；这使 PUSH 投递端到端可用。notification-rule 管理 API 仍未发布。

## Impact

- **产品代码**：`PushSubscriptionController`（4 handler）、`PushSubscriptionService`（register/update/delete + 自作用域 list）、`PushSubscriptionMapper`（toEntity，遵守 INC-005 ERROR）、新 `PushSubscriptionRegisterDTO`（+ update DTO）、`AuthorizationAction` + `AuthorizationPolicy`（新 action）。
- **测试**：能力测试（Testcontainers）—— 登记成功/自作用域 list/跨用户 404/重复 409/provider 非法 400/匿名 401；可选端到端：登记后 `dispatch` → SENT（打桩 PushoverClient）。
- **CI**：复用 `backend-java-tests.yml`。
- **spec**：notifications delta（随 change，archive sync）。
- **无破坏性迁移**：push_subscriptions 表上 change 已建；本 change 不动 schema。
- **端到端解锁**：本 change 后，「登记 Pushover key → 创建 PUSH 通知 → dispatch 投递」全链路打通（触发器仍待规则引擎 change，但人工/内部调用 dispatch 已可真实投递）。
