## Why

2026-07-07 三角度分析（experience，对抗式验证 CONFIRMED）确认站内通知的「未读」语义有产品缺口（UX-03/UX-04）：站内收件箱把「未读」绑在**投递状态枚举**（`QUEUED/SENDING/FAILED/DEFERRED`）上，而非**本人是否读过**——导致「已成功送达但用户从未打开」显示为「已读」、「投递失败」反而显示为「未读/NEW」；且收件箱无 mark-as-read、计数永不清零；导航层也无 ambient 未读徽标，用户须主动点进收件箱才知有新告警。平台核心价值是「实时检测 + 及时通知家长」，站内闭环感知因此打折。本变更引入**每用户读状态**并驱动导航未读徽标。

## What Changes

- **BREAKING（schema，首条 V2 迁移）**：`notifications` 表加 `read_at timestamptz NULL`（`NULL`=未读；DB-1 squash 后的首条 Flyway `V2` 迁移）+ 支持未读计数的索引。同步更新 `db/dbml/schema.dbml` 与 `db/initdb/01_create_schema.sql`（三装配路径同源）。
- **Backend**：
  - 新增 `PATCH /api/v1/notifications/{id}/read`（**写操作 → 走 CSRF**）：仅**收件人本人**（`recipient_user_id`=当前用户）可置 `read_at=now()`，幂等（已读再调 no-op 200）；他人/跨租户/不存在 → 隐藏 404 + 审计。
  - 新增 `GET /api/v1/notifications/unread-count`：返回**调用者本人**（`recipient_user_id`=自己）`read_at IS NULL` 的计数（用于徽标）。
  - `NotificationReadVO` 增 `readAt` 字段；「未读」判定语义从投递状态改为 `read_at IS NULL`。投递失败（`FAILED`）在读侧独立呈现，不再混入「未读」。
- **Frontend**：
  - `TopBar` 「알림」挂 ambient 未读红点/计数（在 AppLayout/SessionBootstrap 轻量拉 `unread-count`，登录后刷新）。
  - 收件箱打开/点击单条即调 `PATCH .../read` 置已读；`isNotificationUnread` 改依 `readAt`（非投递状态白名单，退役 `UNREAD_NOTIFICATION_STATUSES`）；`FAILED` 用「전송 실패」独立视觉，与「未读」区分。

## Capabilities

### New Capabilities
<!-- 无：读状态归入既有 notifications 能力。 -->

### Modified Capabilities
- `notifications`：MODIFIED「Tenant-scoped notification read API」——`NotificationReadVO` 增 `readAt`，新增 `PATCH /{id}/read`（收件人本人）与 `GET /unread-count`，`405` 场景收窄为 POST/PUT/DELETE（PATCH read 已发布）。ADDED「Per-user notification read state drives the in-app unread indicator」——`read_at` 为唯一未读真源、徽标按本人未读计数、mark-as-read 幂等、FAILED 与未读分离。

## Impact

- **db/**：新增 `db/migration/V2__add_notification_read_at.sql`（`ALTER TABLE notifications ADD COLUMN read_at timestamptz;` + 未读计数索引）；`schema.dbml` + `initdb/01_create_schema.sql` 同步加列。**破坏性 = schema 演进**（V2）。
- **backend/**：`Notification` entity + `NotificationReadVO`（+readAt）；`NotificationController`（+PATCH read、+unread-count）；`NotificationService`/`NotificationRepository`（收件人本人 mark-read 的 tenant+recipient 谓词写进 SQL/JPQL、unread-count 查询、隐藏 404）；`@PreAuthorize` 授权。
- **frontend/**：`services/apis/notifications.api.ts`（+markRead、+getUnreadCount、readAt 字段、退役投递状态白名单）；`TopBar` 徽标；`NotificationsListForm`（mark-read 交互、FAILED 独立态）；AppLayout/SessionBootstrap 拉未读数。
- **测试**：后端 `./gradlew test`（新增 mark-read 幂等/越权 404/unread-count 隔离用例，且**验证 seed 改动**须 `cleanTest test`——若 seed 加 read_at）；前端 vitest（badge、mark-read、readAt 未读判定）。
- **不影响**：投递语义（教职员即时/家长复核后/quiet_hours/ESCALATED/DEFERRED 扫描）、多租户隔离链、AI 子栈、其他契约。

## Non-goals

- 不改任何投递（delivery）语义或 `NotificationStatusEnum` 取值（既有 `READ` 枚举值保持不动、不复用作读状态——读状态用正交 `read_at`，避免混淆投递与阅读）。
- 不做 KINDERGARTEN_ADMIN「代他人标已读」：admin 全园视图里他人通知的 `read_at` 是该收件人的读时刻，admin 只能标自己的（`recipient_user_id`=自己）。
- 不做推送渠道（Pushover/SMS）的「已读回执」——带外投递是否被看到后端不可知（维护者已明确），本变更只做**站内**读状态。
- 不引入 WebSocket/SSE 实时未读推送；徽标用登录时 + 轻量拉取刷新（可选复用既有 SSE 触发重取，不新建通道）。
