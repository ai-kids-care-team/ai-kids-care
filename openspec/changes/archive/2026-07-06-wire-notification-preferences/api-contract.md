# API 契约 — wire-notification-preferences (UX-08)

> **冻结**。前后端唯一真源。字段名/可空性/状态码逐字段对齐。
> **前端绝不传 kindergartenId**（租户靠后端 ThreadLocal）。
> 两个端点：GET 读、PUT 写（PUT 是写 → 受 CSRF，前端回填 `X-XSRF-TOKEN`）。
> 错误 body 统一 `{ "error": string }`。

---

### get-my-notification-preference — 读当前用户通知偏好

- **路径**：`GET /api/v1/notification_rules/me`
- **鉴权**：会话（Spring Session）；`@authorizationPolicy.isAllowed(NOTIFICATION_PREFERENCE_MANAGE)`（任意已认证用户）。
- **授权**：仅返回**调用者自己**（session userId + active kindergartenId scoped）的偏好。

#### 响应 `200`
- **VO 类名**：`NotificationPreferenceVO`

| 字段 | 类型 | 可空 | 说明 |
|------|------|------|------|
| enabled | boolean | 否 | 通知总开关；**无 canonical 行时默认 `true`** |
| quietHoursStart | string(`HH:mm`) | 是 | 静默时段起（Asia/Seoul 本地时）；未设为 `null` |
| quietHoursEnd | string(`HH:mm`) | 是 | 静默时段止；未设为 `null` |

- 无偏好行时返回 `{ "enabled": true, "quietHoursStart": null, "quietHoursEnd": null }`（不 404）。

#### 前端对齐点
- `notificationPreferences.api.ts` → `getMyNotificationPreference` query（`providesTags: ['NotificationPreference']`）。
- `SettingsModal` 「알림 설정」卡初始化。

---

### update-my-notification-preference — upsert 当前用户偏好

- **路径**：`PUT /api/v1/notification_rules/me`
- **鉴权**：会话 + CSRF `X-XSRF-TOKEN`；同上 action。
- **授权**：upsert 调用者自己的 canonical 行（`target_type=KINDERGARTEN`），`(kindergarten_id, user_id)` scoped。

#### 请求
- **DTO 类名**：`NotificationPreferenceUpdateDTO`

| 字段 | 类型 | 可空 | 校验/说明 |
|------|------|------|-----------|
| enabled | boolean | 否 | `@NotNull`；通知总开关 |
| quietHoursStart | string(`HH:mm`) | 是 | 与 end **同时为 null**=清除静默；同时有值=设窗口；格式 `HH:mm`（`^([01]\d|2[0-3]):[0-5]\d$`） |
| quietHoursEnd | string(`HH:mm`) | 是 | 同上 |

- 后端把 start/end 组装为 `quiet_hours_json = {"start":"HH:mm","end":"HH:mm"}`（均 null → 存 `null`）。

#### 响应 `200`
- **VO 类名**：`NotificationPreferenceVO`（同 GET，回显 upsert 后的值）。

#### 错误契约
| 情况 | 状态码 | body |
|---|---|---|
| enabled 缺失 | 400 | `{ "error": "..." }` |
| quietHoursStart/End **仅一侧有值** | 400 | `{ "error": "..." }` |
| start/end 格式非 `HH:mm` | 400 | `{ "error": "..." }` |
| 未认证 | 401 | `{ "error": "..." }` |
| 缺 CSRF | 403 | (Spring 默认) |

#### 前端对齐点
- `notificationPreferences.api.ts` → `updateMyNotificationPreference` mutation（`invalidatesTags: ['NotificationPreference']`）。
- `SettingsModal` 保存回调。

---

## 运行时行为契约（后端内部，无线协议，但两侧须知语义）

投递侧（`GuardianNotificationService`）消费上述偏好，**仅影响 `RESOLVED`（非紧急）家长通知**：
- `enabled=false` → 该家长的 `RESOLVED` 通知**不发**。
- 用户级 `quiet_hours_json` 覆盖园所级窗口；未设则回退园所级。
- **`ESCALATED`（紧急）穿透**：忽略用户 `enabled` 与静默时段，始终即时送达（含 SMS 附加通道）。

## enum / 分页
- 无线上 enum（`target_type=KINDERGARTEN` 是后端内部落库值，不出现在端点线协议）。
- 无分页（单用户单偏好）。
