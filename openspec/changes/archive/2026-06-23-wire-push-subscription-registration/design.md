## Context

PUSH 投递链路上 change 已就绪（`PushoverConfig`、`NotificationService.dispatch` 生命周期、`push_subscriptions` 表 + `PushSubscriptionRepository.findByUser_IdAndProviderAndStatus`），但 `PushSubscriptionController` 空壳(405)、`PushSubscriptionService` 仅 `denyAll()` 读桩 → 无途径写入收件人 Pushover user-key → 投递永远 FAILED。本 change 发布自助管理 API 补上缺口。

约束/既有模式：
- 授权：`@PreAuthorize("@authorizationPolicy.isAllowed(ACTION)")` 粗粒度门 + service 用 `EffectiveAuthorizationContextHolder` 做细粒度作用域（见 NotificationService：粗门 NOTIFICATION_READ + repo SQL recipient-scoped）。
- `push_subscriptions` 无 `kindergarten_id`，纯 user-scoped；`AuthorizationPolicy` 现有 action 多基于 tenantIdentity/PLATFORM scope，无「任一已认证用户管自己资源」的纯 user-scoped 先例（最接近 `PLATFORM_ANNOUNCEMENT_READ -> true`）。
- INC-005 守卫：所有 `@Mapper` 带 `unmappedTargetPolicy=ERROR`；新增 `toEntity` 必须为每个未映射 target 显式 `@Mapping(ignore=true)` 或映射，否则编译失败。
- 秘密：Pushover user-key 是投递地址（半秘密）；`PushSubscriptionVO` 现已不含 address —— 保持不在读响应暴露。

## Goals / Non-Goals

**Goals:** 发布 push_subscriptions 自助 CRUD（注册/列出/更新/删除自己的订阅），使 PUSH 投递端到端可用；自作用域、不信客户端 user_id；provider 限 PUSHOVER。

**Non-Goals:** 规则引擎/检测触发/guardian 闸门/staff 阈值（ADR-0015/未定阈值）、SMS（ADR-0018）/EMAIL、notification-rule 管理 API、改投递原语或读 API。

## Decisions

### D1：自助、自作用域、不信客户端 user_id
所有操作的 `user_id` = `EffectiveAuthorizationContextHolder.require().userId()`，**忽略**请求体里的任何 user_id。list/update/delete 的查询都带 `user_id = caller` 谓词 → 天然只能碰自己的行。跨用户访问他人订阅 → 仓储查不到 → 隐藏 404（镜像 NotificationService.getNotification 的 hidden-404 + 审计模式）。
- 理由：push_subscriptions 是个人投递身份,自助是最简且安全的模型,无需 tenant/role 复杂度。

### D2：授权 = 新 action `PUSH_SUBSCRIPTION_MANAGE`，任一已认证用户放行
`AuthorizationPolicy`: `case PUSH_SUBSCRIPTION_MANAGE -> true;`（context 存在即 true，等价「任一已认证用户」，镜像 PLATFORM_ANNOUNCEMENT_READ）。细粒度由 D1 的 user-scoped 查询强制。匿名 → isAllowed 经 `orElse(false)` 拒 → 401。
- 备选：按角色限制——否决,推送订阅人人可有(GUARDIAN/TEACHER/ADMIN/平台角色皆可登记自己的设备),无理由按角色排除。

### D3：provider 限 PUSHOVER；status 直接 ACTIVE
注册仅接受 `provider=PUSHOVER`（唯一实现）；非法 → 400（`PushProviderEnum.valueOf` 或显式校验）。新订阅 status=ACTIVE（Pushover 在发送时校验 key 有效性；`last_verified_at` 可由后续成功投递回填，本 change 不做验证握手——保持简单）。
- 备选：PENDING+验证握手——否决（超范围；Pushover 无标准 verify 端点，发送即验证）。

### D4：重复订阅 → 409
`(user_id, provider, address)` 有 DB 唯一约束（上 change 建）。重复注册 → `DataIntegrityViolationException` → 由 `ApiExceptionHandler` 映射或 service 预检为 409 Conflict。
- 实现：service 捕获唯一冲突转 409，或先 `findByUser_IdAndProviderAndStatus` 存在则 409；倾向捕获 DB 冲突（避免 TOCTOU）。

### D5：DTO 与 Mapper（遵守 INC-005）
- `PushSubscriptionRegisterDTO`：`provider`(默认/限 PUSHOVER)、`address`(@NotBlank)、`deviceLabel`(可选)。
- 更新用同 DTO 或 `PushSubscriptionUpdateDTO`（address/deviceLabel/status 可选，patch 语义）。
- `PushSubscriptionMapper.toEntity`：为 `id`/`user`/`status`/`createdAt`/`lastVerifiedAt` 显式 `@Mapping(ignore=true)`（服务端设值），`provider`/`address`/`deviceLabel` 映射 —— 否则 `unmappedTargetPolicy=ERROR` 编译失败。或 service 直接 new 实体赋值,绕过 mapper toEntity（更直白、零未映射风险）。倾向 service 手装实体（字段少）。

### D6：VO 不含 address
读响应继续用 `PushSubscriptionVO`（无 address）。注册成功返回 201 + VO（含 id/provider/deviceLabel/status），不回显 user-key。

## Risks / Trade-offs

- [INC-005：新 toEntity 漏 ignore → 编译失败] → 优先 service 手装实体（无 mapper toEntity）规避；若用 mapper 则逐 target 核 ignore。
- [跨用户越权] → 全部查询 user-scoped + hidden-404；测试覆盖跨用户 delete/get。
- [重复注册竞态] → 捕获 DB 唯一冲突转 409（非先查后插）。
- [address 是半秘密] → 不入 VO/日志；错误响应不回显（沿用 ErrorResponse 既有脱敏）。
- [纯 user-scoped action 是新模式] → 已有 PLATFORM_ANNOUNCEMENT_READ->true 先例,风险低；service 强制 user 作用域是安全锚点。

## Migration Plan

1. 加 `AuthorizationAction.PUSH_SUBSCRIPTION_MANAGE` + `AuthorizationPolicy` case。
2. TDD：写能力测试（注册/自作用域 list/跨用户 404/重复 409/provider 400/匿名 401）。
3. 实现 DTO + service（自作用域 register/list/update/delete，user_id 取自 context）+ controller 四 handler；替换 denyAll。
4. 容器内全套件全绿；notifications spec delta；可选端到端（登记→dispatch→SENT，打桩 client）。
5. code review；合 develop / push / archive。
- 回滚：纯新增端点 + 一个 enum 值；git 还原即可；无 schema 改动。

## Open Questions

- update 用同一 RegisterDTO 还是独立 UpdateDTO（patch 语义）？apply 第 3 步定，倾向独立 UpdateDTO + NullValuePropertyMappingStrategy.IGNORE。
- 是否本期加端到端「登记→dispatch→SENT」集成测试（@MockBean PushoverClient）？倾向加一个,坐实缺口已闭合。
