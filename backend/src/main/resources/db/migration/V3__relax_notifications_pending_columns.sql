-- V3__relax_notifications_pending_columns.sql
-- OQ-DATA-3 / ADR-0018（通知子系统）：一条「待发（pending）」通知在尚未发送前不应被
-- 强制要求发送结果字段。放宽仅在「发送尝试之后」才有意义的 NOT NULL 列：
--   sent_at      → 发送前为 NULL（DROP NOT NULL）
--   fail_reason  → 无失败时为 NULL（DROP NOT NULL）
--   retry_count  → 新建通知为 0（保留 NOT NULL，加 DEFAULT 0）
-- 实体 Notification 的可空性同步放宽，与本迁移一致（ddl-auto=validate 不校验可空性，
-- 故二者须并改才在应用层真正生效）。既有 seed 行已有值，不受影响。

ALTER TABLE notifications ALTER COLUMN sent_at DROP NOT NULL;
ALTER TABLE notifications ALTER COLUMN fail_reason DROP NOT NULL;
ALTER TABLE notifications ALTER COLUMN retry_count SET DEFAULT 0;
