## 1. DB — V2 迁移（首条；破坏性，须维护者批准后方可执行）

- [ ] 1.1 新建 `db/migration/V2__add_notification_read_at.sql`：`ALTER TABLE notifications ADD COLUMN read_at timestamptz;` + `CREATE INDEX idx_notif_unread ON notifications (kindergarten_id, recipient_user_id) WHERE read_at IS NULL;`
- [ ] 1.2 `db/dbml/schema.dbml` 的 `notifications` 加 `read_at timestamptz` + 未读部分索引（真源同步）
- [ ] 1.3 `db/initdb/01_create_schema.sql` 加同列同索引（fresh install 路径同源）
- [ ] 1.4 确认三处一致 + Flyway `baseline-on-migrate` 语义下 V2 从 V1 baseline 续起

## 2. Backend — entity / VO / 读侧

- [ ] 2.1 `Notification` entity 加 `read_at`（`OffsetDateTime readAt`，nullable）；`ddl-auto=validate` 通过
- [ ] 2.2 `NotificationReadVO` 加 `readAt`（可空）；list/detail mapper 回填
- [ ] 2.3 保持内部字段（channel/dedupeKey/...）仍不出现在读响应

## 3. Backend — mark-read + unread-count 端点

- [ ] 3.1 `NotificationController` 加 `PATCH /{id}/read`（写 → CSRF 生效）
- [ ] 3.2 `NotificationService.markRead(id)`：`@PreAuthorize` + repository UPDATE `read_at=now()` `WHERE notification_id=:id AND recipient_user_id=:me AND kindergarten_id=:tenant`；0 行 → 隐藏 404 + 审计；已读 no-op 200（幂等）
- [ ] 3.3 `NotificationController` 加 `GET /unread-count`；`NotificationService.unreadCount()` = `count WHERE recipient_user_id=:me AND kindergarten_id=:tenant AND read_at IS NULL`（始终本人，含 admin）
- [ ] 3.4 租户+收件人谓词全部写进 JPQL/SQL（禁「加载后过滤」）

## 4. Backend 测试

- [ ] 4.1 mark-read：本人置位 200 + `read_at` 落库；越权/跨租户/不存在 → 404 + 审计；已读再调幂等 200
- [ ] 4.2 unread-count：仅计本人 `read_at IS NULL`；admin 全园可读但徽标只计本人；租户隔离
- [ ] 4.3 read VO 含 `readAt`、投递内部字段不泄漏
- [ ] 4.4 若改 `db/initdb/` seed → `./gradlew cleanTest test`；否则 `./gradlew test` 全绿

## 5. Frontend — api + 徽标 + mark-read

- [ ] 5.1 `notifications.api.ts`：`NotificationReadVO` 加 `readAt`；加 `markRead(id)`(`PATCH .../read`) + `getUnreadCount()`；`isNotificationUnread` 改依 `readAt==null`，退役 `UNREAD_NOTIFICATION_STATUSES`
- [ ] 5.2 AppLayout/SessionBootstrap 层拉 `getUnreadCount`（登录后刷新）；`TopBar`「알림」挂未读红点/计数
- [ ] 5.3 `NotificationsListForm`：打开/点击单条 → `markRead` + 乐观置已读 + 递减徽标；读完计数归零
- [ ] 5.4 `FAILED` 用「전송 실패」独立视觉，与「未读」红点区分

## 6. Frontend 测试（vitest）

- [ ] 6.1 `isNotificationUnread` 按 `readAt`（null=未读、非 null=已读；FAILED 不再自动未读）
- [ ] 6.2 `markRead`/`getUnreadCount` api（CSRF 头、路径、乐观更新）
- [ ] 6.3 徽标组件按 unread-count 呈现/归零

## 7. 验证门

- [ ] 7.1 后端 `./gradlew test`（或 seed 改动则 `cleanTest test`）全绿
- [ ] 7.2 前端 `npm run lint && npm run build` + 新 vitest 全绿
- [ ] 7.3 security-analyst 复核：mark-read 越权→404、unread-count 本人域、租户谓词入 SQL、CSRF 生效
- [ ] 7.4 integration-analyst 复核：`readAt`/PATCH/unread-count 两侧逐字段吻合、投递 status 与 read 语义未混
- [ ] 7.5 门禁清零后 `openspec archive wire-notification-read-state -y`（notifications delta）；Lead 手动收口 archive commit
