-- wire-notification-read-state (UX-03/UX-04): per-user notification read state, orthogonal to
-- delivery `status`. read_at IS NULL means unread; a non-NULL value is the recipient's own
-- in-app read timestamp. Existing rows get no backfill (NULL) — old notifications are correctly
-- treated as unread. Nullable, no default: adding it is a metadata-only operation, no table lock.
ALTER TABLE notifications ADD COLUMN read_at timestamptz;

-- Partial index serving both GET /unread-count and the recipient's own unread list.
CREATE INDEX idx_notif_unread ON notifications (kindergarten_id, recipient_user_id) WHERE read_at IS NULL;
