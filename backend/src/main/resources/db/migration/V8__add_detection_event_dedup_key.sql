-- V8: add an idempotency dedup_key to detection_events.
--
-- The AI generates a dedup_key (from camera + alarm-onset time) and submits it with each
-- detection event; the backend relies on a UNIQUE (kindergarten_id, dedup_key) constraint so AI
-- reconnects / debounce retries do not create duplicate detection_events rows.
--
-- Additive, non-destructive: ADD COLUMN (nullable) -> backfill existing rows with a unique value
-- -> SET NOT NULL -> CREATE UNIQUE INDEX. Runs forward in both schema paths (fresh-V1 and
-- initdb+baseline); initdb (V1 mirror) is not edited.

ALTER TABLE "detection_events" ADD COLUMN "dedup_key" varchar;

-- Backfill pre-existing rows (seed/demo) with a per-row unique value.
UPDATE "detection_events" SET "dedup_key" = 'seed-' || "event_id" WHERE "dedup_key" IS NULL;

ALTER TABLE "detection_events" ALTER COLUMN "dedup_key" SET NOT NULL;

CREATE UNIQUE INDEX "uq_detection_events_dedup"
  ON "detection_events" ("kindergarten_id", "dedup_key");

COMMENT ON COLUMN "detection_events"."dedup_key"
  IS '멱등성 키(AI 생성: camera + alarm 시작 시각). UNIQUE (kindergarten_id, dedup_key)로 재연결/디바운스 중복 방지';
