# wire-notification-preferences (UX-08)

## Why

`notification_rules` 表（含 `user_id` / `quiet_hours_json` / `enabled`）自 V1 baseline 就已就位，
且有种子数据，但**无任何写入口、无运行时消费者**：`NotificationRuleController` 是空类、
`NotificationRuleService` 两个方法均 `@PreAuthorize("denyAll()")`，而 `QuietHoursService`
只读**园所级** `kindergartens.notification_quiet_hours_json`，其 `guardianUserId` 参数注释为
「reserved for future per-guardian override」始终传 `null`。结果：用户级静默时段与通知总开关
是「存了但从不生效」的死配置。UX-08 把这条既有但未接线的能力接通，给家长（及任何登录用户）
一个自助设置通知偏好的入口，兑现「后端就绪、只差最后一公里」。

## What Changes

- **自助偏好 API**（self-service，镜像 `push_subscriptions` 的鉴权范式）：登录用户读/写**自己**的
  通知偏好（用户级**静默时段** + 通知**总开关**），落在每用户一条 canonical `notification_rules`
  行（`target_type=KINDERGARTEN`）。跨用户/跨租户不可见 → 隐藏 404。
- **接通运行时消费**：`GuardianNotificationService` 在**每收件人**维度：
  - **总开关** `enabled=false` → 跳过该家长的 `RESOLVED`（非紧急）通知；
  - **用户级静默时段** 覆盖园所级窗口（用户未设则回退园所级）。
  - `ESCALATED`（紧急）**穿透**总开关与静默时段，始终即时送达（儿童安全红线不被用户设置削弱）。
- 前端 `SettingsModal` 新增「通知 설정」卡（静默时段起止 + 总开关），复用现成设置弹窗聚合点。

## Non-goals

- **不做 schema 迁移**：复用 V1 baseline 既有 `notification_rules` 表与列，零 Flyway 变更。
- **不做** 按事件类型 / 严重度 / 房间-摄像头范围的细粒度规则 UI（`event_type` / `min_severity` /
  `target_type=ROOM|CAMERA` 列保留但本切片不暴露；canonical 行 `min_severity` 取 match-all 默认值、
  本切片对派发无过滤作用）。
- **不做** 园所管理员代配下属规则（`NotificationRuleController` 既有 `denyAll()` list/get 管理面
  占位保持不动，属未来 admin 面）。
- **不改** `ESCALATED` 穿透语义、`DeferredNotificationScanner`、去重键、通知实体结构。
