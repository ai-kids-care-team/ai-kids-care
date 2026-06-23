## 1. 授权 action

- [x] 1.1 `AuthorizationAction` 加 `PUSH_SUBSCRIPTION_MANAGE`
- [x] 1.2 `AuthorizationPolicy.isAllowed`：`case PUSH_SUBSCRIPTION_MANAGE -> true`（任一已认证用户;细粒度自作用域由 service 强制）

## 2. DTO

- [x] 2.1 `PushSubscriptionRegisterDTO`（`provider` 限 PUSHOVER、`address` @NotBlank、`deviceLabel` 可选）
- [x] 2.2 `PushSubscriptionUpdateDTO`（address/deviceLabel/status 可选，patch 语义）

## 3. Service（自作用域，替换 denyAll）

- [x] 3.1 [RED] 能力测试 `PushSubscriptionApiTest extends BaseIntegrationTest`（MockMvc + 真登录会话）：注册 201 + 不回显 address;自作用域 list;跨用户 PUT/DELETE → 404;重复 → 409;provider 非法 → 400;匿名 → 401
- [x] 3.2 `PushSubscriptionService`：`register`(user_id 取自 `EffectiveAuthorizationContextHolder.require().userId()`,忽略客户端 user_id;provider 校验 PUSHOVER;status=ACTIVE;捕获唯一冲突→409)、自作用域 `listOwn`、`update`(自作用域+patch)、`delete`(自作用域,hidden-404);`@PreAuthorize(...PUSH_SUBSCRIPTION_MANAGE)`;手装实体或 mapper toEntity 遵守 INC-005（ERROR）
- [x] 3.3 repository 按需加 `findByUser_Id` / `findByIdAndUser_Id`（自作用域查询）

## 4. Controller

- [x] 4.1 `PushSubscriptionController` 加 `POST`/`GET`/`PUT /{id}`/`DELETE /{id}` handler,委托 service;返回 201/200/204
- [x] 4.2 VO 不含 address 复核（`PushSubscriptionVO` 已无 address）

## 5. 验证与收尾（verification-before-completion）

- [x] 5.1 [GREEN] 容器内 `gradle:8.7-jdk21` 跑通 `PushSubscriptionApiTest`
- [~] 5.2 （可选端到端）未单独加 —— 由组合证明:注册 API(本 change,6 测试)写入 active 订阅 + 既有 `NotificationDispatchTest` 证明 dispatch 解析 active 订阅→SENT。如需独立端到端集成测试可后补
- [x] 5.3 容器内**全套件**全绿（既有 148 + 新增），留存证据
- [x] 5.4 范围核对（git diff）：仅 push_subscription 管理相关 + 1 个 auth action;未改投递原语/读 API/其它能力;无 schema 改动;秘密不回显
- [x] 5.5 核对 notifications spec delta 与实现一致（API 已发布、自作用域、provider 限制、409/404/400/401）
- [ ] 5.6 requesting-code-review；按反馈修正
- [ ] 5.7 合并 develop / push / `/opsx:archive`（用户驱动，含 spec delta sync）

---

> 无高风险迁移：本 change 不含删除/迁移/schema 操作（push_subscriptions 表上 change 已建）。产品改动＝发布既有空壳 controller/service + 1 个 user-scoped auth action;遵守上 change 的 INC-005 mapper ERROR 守卫。
