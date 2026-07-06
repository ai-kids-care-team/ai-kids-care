# Tasks — wire-notification-preferences (UX-08)

## 后端（backend/）
- [ ] 新增 `AuthorizationAction.NOTIFICATION_PREFERENCE_MANAGE` + `authorizationPolicy` 映射为任意已认证用户（镜像 `PUSH_SUBSCRIPTION_MANAGE`）。
- [ ] `NotificationPreferenceVO`（enabled/quietHoursStart/quietHoursEnd）+ `NotificationPreferenceUpdateDTO`（`@NotNull enabled`，start/end `HH:mm` pattern，可空）。
- [ ] `NotificationRuleRepository` 加 canonical 查询：按 `(kindergarten_id, user_id, target_type=KINDERGARTEN)` 查唯一行。
- [ ] `NotificationPreferenceService`：`getMine()`（无行返回默认 enabled=true/空静默）、`upsertMine(dto)`（组装 quiet_hours_json、canonical 行 upsert、start/end 单侧有值→400）、只读 `findCanonical(kgId, userId)` 供投递侧。全部 `@PreAuthorize(NOTIFICATION_PREFERENCE_MANAGE)`、userId/kgId 取自 Holder、双谓词 scoped。
- [ ] `NotificationRuleController` 加 `GET /me` + `PUT /me`（注入 service，路由分发）。既有 `denyAll()` list/get 保持不动。
- [ ] `GuardianNotificationService.notifyOnReview`：defer 判定移入 per-recipient 循环——RESOLVED 且 `!enabled` 跳过该家长；effectiveJson = 用户级 quiet(若 enabled 且非空) 否则园所级；ESCALATED 分支完全不变（穿透）。
- [ ] 测试：`NotificationPreferenceService`（upsert/默认/单侧 400/跨用户隔离）；`GuardianNotificationService`（enabled=false 跳过 RESOLVED、用户级静默覆盖、ESCALATED 穿透总开关与静默、无偏好=默认发）。

## 前端（frontend/）
- [ ] `services/apis/notificationPreferences.api.ts`：`getMyNotificationPreference` / `updateMyNotificationPreference`（baseApi、CSRF 自动、绝不传 kindergartenId、tag `NotificationPreference`）。
- [ ] `SettingsModal` 加「알림 설정」卡：总开关 toggle + 静默 시작/종료 time input；单侧填值前端阻止提交；保存调 update。
- [ ] 类型对齐契约字段名/可空性。

## 门禁
- [ ] 后端 `./gradlew test` 全绿（含新测试）。
- [ ] 前端 `npm run lint && npm run build` 绿。
- [ ] 安全复核（自助 scoped/跨用户 404/ESCALATED 穿透红线）+ 集成复核（契约双侧逐字段）。
- [ ] archive。
