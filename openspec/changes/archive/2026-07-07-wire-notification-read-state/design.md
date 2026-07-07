## Context

站内通知行本就是 per-recipient（`notifications.recipient_user_id`），但「未读」在前端绑投递状态枚举（`isNotificationUnread` = `QUEUED/SENDING/FAILED/DEFERRED`），非本人阅读；无 mark-as-read、无导航徽标。`NotificationStatusEnum` 虽含 `READ` 值，但它是投递生命周期的一环、从不被置位、且与「本人是否读过」语义正交——复用它会把投递与阅读混在一维。约束：DB-1 已 squash 至单一 `V1` baseline，本变更是**首条 V2** Flyway 迁移（破坏性，须维护者逐个批准）；`db/dbml/schema.dbml`、`db/initdb/01_create_schema.sql`、Flyway baseline 三装配路径须同源。

## Goals / Non-Goals

**Goals:**
- 引入正交的 per-user `read_at`，让站内「未读」= `read_at IS NULL`（本人行），而非投递状态。
- 导航 ambient 未读徽标（本人未读计数）；收件箱 mark-as-read 幂等；FAILED 与未读分离。

**Non-Goals:**
- 不改投递语义/枚举取值；不复用 `READ` 枚举做读状态；不做推送已读回执（带外不可知）；不做 admin 代他人标已读；不新建 SSE/WS 未读通道（详见 proposal Non-goals）。

## Decisions

### D1 — read_at 正交列（非复用 status.READ）
`notifications` 加 `read_at timestamptz NULL`。未读 = `read_at IS NULL`。投递 `status` 保持独立（回答「push/sms 发出去没」），`read_at` 回答「本人在站内开过没」。二者正交，避免语义坍缩。既有 `READ` 枚举值不动、不置位（保留以免动 enum 三处同源；仅不再作未读真源）。
- 备选：transition status→READ 当读状态 → 否决，混淆投递与阅读，且 admin 看他人通知会误置。

### D2 — V2 迁移（首条，破坏性）
新建 `db/migration/V2__add_notification_read_at.sql`：
```sql
ALTER TABLE notifications ADD COLUMN read_at timestamptz;
CREATE INDEX idx_notif_unread ON notifications (kindergarten_id, recipient_user_id) WHERE read_at IS NULL;
```
（部分索引服务 unread-count 与本人未读列表；`read_at` 可空、无默认——既有行视为未读，符合语义。）同步：`schema.dbml` 的 `notifications` 加 `read_at timestamptz [note: "본인 열람 시각; NULL=미읽음(V2)"]` + 索引；`initdb/01_create_schema.sql` 加同列同索引。`ddl-auto=validate` 要求 entity 与 schema 完全对齐。
- **验证注意**：若为 seed（`db/initdb/`）演示数据补 `read_at` 值，须 `./gradlew cleanTest test`（seed 是 testcontainer fixture，不在 test 输入会被判 UP-TO-DATE）。

### D3 — 后端端点与授权
- `PATCH /notifications/{id}/read`（写 → CSRF）：service `@PreAuthorize` + repository 以 `WHERE notification_id=:id AND recipient_user_id=:me AND kindergarten_id=:tenant` 的 UPDATE 置 `read_at=now()`；受影响行数 0 → 隐藏 404 + 审计（越权/跨租户/不存在一律 404，不泄露存在性）。幂等：已读再调，UPDATE 命中但值不变（或 `AND read_at IS NULL` 后 0 行仍返回 200 no-op）——**决策：** 用 `recipient+tenant` 谓词判存在与归属（决定 404 vs 200），已读再置为 no-op 200。
- `GET /notifications/unread-count`：`SELECT count(*) WHERE recipient_user_id=:me AND kindergarten_id=:tenant AND read_at IS NULL`。**始终按本人**（即便 KINDERGARTEN_ADMIN 全园可读列表，未读徽标只计本人收到的）。
- `NotificationReadVO` += `readAt`（`OffsetDateTime`, 可空）。list/detail 一并回填。

### D4 — admin 全园视图的读状态语义
KINDERGARTEN_ADMIN 的 `GET /notifications` 返回全园通知；其中他人通知的 `readAt` = **该收件人**的读时刻（时间戳非 PII，可暴露）。admin 的**徽标**仍只计 `recipient_user_id=admin` 的未读；admin **不能**标他人已读（`PATCH read` 谓词含 `recipient_user_id=:me` → 他人行 0 命中 → 404）。这一点在 spec 与实现里都钉死。

### D5 — 前端徽标与 mark-read
- 未读数在 **AppLayout/SessionBootstrap** 层轻量拉 `GET /unread-count`，登录后刷新；`TopBar` 「알림」渲染红点/计数。可选：复用既有 detection SSE 到达时重取未读数（不新建通道；无 SSE 也能靠进入/离开收件箱后刷新）。
- 收件箱打开单条/点击即 `markRead(id)` → 本地乐观置 `readAt` + 递减徽标；`isNotificationUnread` 改依 `readAt==null`，退役 `UNREAD_NOTIFICATION_STATUSES`。
- `FAILED` 用「전송 실패」独立视觉（如红色标签），与「未读」红点区分。

## Risks / Trade-offs

- [首条 V2 迁移，三装配路径漂移风险] → 三处（V2 sql / dbml / initdb）同步加列 + `ddl-auto=validate` 启动即校验；FlywayMigrationTest 兜底。
- [幂等 mark-read 与 404 语义边界] → 谓词先判归属（决定 404），再置位；已读 no-op 200；专测钉死越权→404、幂等→200。
- [徽标拉取时机导致短暂 stale] → 登录刷新 + 收件箱交互后重取即可满足「及时感知」；不追求实时（Non-goal）。
- [seed 若不补 read_at → 演示数据全未读] → 可接受（语义正确：旧通知视为未读）；若要演示「部分已读」再补 seed（触发 cleanTest）。

## Migration Plan

**部署步骤**：V2 为纯加列（可空、无默认、带部分索引），对既有行无回填、无锁表风险（Postgres 加可空列是元数据操作）。上线顺序：先迁移（加列）→ 再部署带新 entity/端点的 backend。**回滚**：`ALTER TABLE notifications DROP COLUMN read_at;`（无数据依赖，安全）——但因 DB-1 只保单一 baseline 策略，回滚优先靠 revert 代码 + 不前进迁移；生产无环境（当前仅脚手架），实际风险面小。**须维护者逐个批准 V2 迁移方可 apply。**

## Open Questions

- 是否给 seed 补「部分已读」演示数据（触发 `cleanTest test`）？默认不补（旧通知全未读，语义正确），仅在需要 demo 徽标清零体验时补。
- 徽标是否接 SSE 实时递增（新告警到达即 +1）？默认走「登录 + 交互后刷新」；实时接 SSE 列为可选增强，不阻塞本变更。
