-- V9: quiet-hours deferral (closed-loop step ③b).
--
-- Adds the DEFERRED notification status, a kindergarten-wide quiet-hours window, and a
-- deferred_until timestamp. Additive, non-destructive: runs forward in both schema paths
-- (fresh-V1 and initdb+baseline); initdb (V1 mirror) is NOT edited — both paths apply V9.
--
-- PG (12+) allows ALTER TYPE ... ADD VALUE inside a transaction; the new enum value is only
-- used at runtime by later transactions (the deferral write + the scanner), never within this
-- migration's own transaction, so Flyway's single-transaction execution is safe.

ALTER TYPE "notification_status_enum" ADD VALUE IF NOT EXISTS 'DEFERRED';

ALTER TABLE "kindergartens" ADD COLUMN "notification_quiet_hours_json" varchar;

ALTER TABLE "notifications" ADD COLUMN "deferred_until" timestamptz;

COMMENT ON COLUMN "kindergartens"."notification_quiet_hours_json"
  IS '전원 통일 정숙시간(quiet hours) 창 JSON {"start":"HH:mm","end":"HH:mm"}, Asia/Seoul; NULL이면 정숙시간 없음 (③b)';

COMMENT ON COLUMN "notifications"."deferred_until"
  IS '정숙시간 지연 발송 예정 시각(DEFERRED 상태일 때); 스캐너가 이 시각 경과 후 dispatch (③b)';
