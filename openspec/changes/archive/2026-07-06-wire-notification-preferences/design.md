# 设计 — wire-notification-preferences (UX-08)

## 数据模型：canonical per-user 偏好行

`notification_rules` 表 `target_id` / `min_severity` / `enabled` / `user_id` 均 NOT NULL。用户的
「全局通知偏好」落成**每 (kindergarten_id, user_id) 一条 canonical 行**：

| 列 | 值 | 说明 |
|----|----|------|
| `kindergarten_id` | `activeKindergartenId`（ThreadLocal） | 租户域 |
| `user_id` | 当前会话 userId | 自助归属 |
| `target_type` | `KINDERGARTEN` | 标识这是用户的园所级全局偏好行（非 ROOM/CAMERA 细粒度规则） |
| `target_id` | `activeKindergartenId` | 满足 NOT NULL；本切片语义上等价「整园」 |
| `min_severity` | match-all 默认（最低档，如 `0`） | 满足 NOT NULL；**本切片派发路径不读它**，故对过滤无副作用 |
| `event_type` | `null` | 不做事件类型过滤 |
| `quiet_hours_json` | `{"start":"HH:mm","end":"HH:mm"}` 或 `null` | 用户级静默窗口；null=未设 |
| `enabled` | `true`/`false` | 通知总开关 |

**Upsert 语义**：按 `(kindergarten_id, user_id, target_type=KINDERGARTEN)` 查 canonical 行，存在则
更新 `quiet_hours_json` + `enabled`，不存在则插入（其余列取上表默认）。查询**必须**带
`kindergarten_id` + `user_id` 谓词（多租户 + 自助双重 scoped）。

> 为何不新建「简单偏好表」：既有表已含全部所需列且有索引 `idx_rule_owner(kindergarten_id, user_id)`，
> 新表要 V2 迁移（破坏性），与「零迁移」目标冲突。canonical 行是对既有表的**无迁移**复用。

## 鉴权：镜像 push_subscriptions 自助范式

- 新增 `AuthorizationAction.NOTIFICATION_PREFERENCE_MANAGE`，`authorizationPolicy` 映射为
  **任意已认证用户**（粗粒度门，同 `PUSH_SUBSCRIPTION_MANAGE`）。
- service 方法标 `@PreAuthorize("@authorizationPolicy.isAllowed(...NOTIFICATION_PREFERENCE_MANAGE)")`；
  `userId` 一律取自 `EffectiveAuthorizationContextHolder.require().userId()`，**不信任请求体身份**；
  `kindergartenId` 取自 `...require...activeKindergartenId()`。跨用户/跨租户 → 查询无命中 → 隐藏语义。

## 运行时接线：GuardianNotificationService（核心行为改动）

现状（`notifyOnReview`）：quiet window 在**园所级只算一次**
（`resolveQuietWindow(kindergartenId, null)`，L97-103），对所有家长统一 defer 判定。

改为**每收件人**判定（仅影响 `RESOLVED` 路径；`ESCALATED` 分支完全不变，穿透一切）：

```
对每个 guardianUserId：
  canonical = notificationPreferenceService.findCanonical(kgId, userId)   // 一次查询，可能 null
  若 RESOLVED 且 canonical 存在且 !canonical.enabled → 跳过该家长（总开关关，非紧急不发）
  effectiveJson = (canonical 存在 且 enabled 且 quiet_hours_json 非空)
                    ? canonical.quiet_hours_json      // 用户级覆盖
                    : kindergarten.quiet_hours_json   // 回退园所级
  window = quietHoursService.parse(effectiveJson)
  defer  = window.present 且 isWithinQuietHours(window, now)
  deferUntil = defer ? nextEndInstant(...) : null
  deliver PUSH（沿用现逻辑）
  // ESCALATED：与现状一致——不查偏好、不 defer、附加 SMS（穿透总开关与静默时段）
```

- `QuietHoursService` 保持解析/判定纯函数（`parse` / `isWithinQuietHours` / `nextEndInstant` 不变）；
  per-user 覆盖的**取值决策**放在 `GuardianNotificationService`（配合新 `NotificationPreferenceService`
  的只读 `findCanonical`），避免 QuietHoursService 反向依赖规则仓库。`resolveQuietWindow(kgId, null)`
  的旧园所级路径保留给其它潜在调用方，本方法不再走 null-user 分支。
- **安全红线**：`ESCALATED` 绝不因用户 `enabled=false` 或静默时段被抑制——紧急告警始终即时。
  在 spec 与测试中显式钉死。
- 每家长 canonical 查询是 per-recipient 一次；收件人集通常很小（一个班的家长），可接受。若日后成
  热点，再批量预载（非本切片）。

## 前端

- `services/apis/` 新增 `notificationPreferences.api.ts`（RTK Query `baseApi`，CSRF 自动回填；
  **绝不传 kindergartenId**）：`getMyNotificationPreference` / `updateMyNotificationPreference`。
- `SettingsModal` 加「알림 설정」卡：总开关（toggle）+ 静默시간 起止（time input，两者同时清空=关闭静默；
  仅填其一 → 前端阻止提交/后端 400）。复用现有卡片视觉。

## 边界与校验

- 静默时段：`quietHoursStart` / `quietHoursEnd` 为 `HH:mm`（24h）或均为 null。**仅一侧有值** → 400。
  `start == end` 落库允许但语义为「空窗口/永不静默」（沿用 `QuietWindow.contains` 既有约定）。
- 总开关默认 `true`（无 canonical 行时 GET 返回 enabled=true、静默空）。
