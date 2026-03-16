-- ---------------------------------------------------------------------
-- announcements
-- 공지사항 테이블
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS announcements (
  id            BIGSERIAL PRIMARY KEY,
  author_id     BIGINT NOT NULL,
  title         VARCHAR(200) NOT NULL,
  body          TEXT NOT NULL,
  is_pinned     BOOLEAN NOT NULL DEFAULT FALSE,
  pinned_until  TIMESTAMPTZ,
  status        status_enum NOT NULL DEFAULT 'ACTIVE',
  published_at  TIMESTAMPTZ,
  starts_at     TIMESTAMPTZ,
  ends_at       TIMESTAMPTZ,
  view_count    BIGINT NOT NULL DEFAULT 0,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  deleted_at    TIMESTAMPTZ,
  CONSTRAINT fk_announcements_author
    FOREIGN KEY (author_id) REFERENCES users(user_id),
  CONSTRAINT ck_announcements_display_period
    CHECK (starts_at IS NULL OR ends_at IS NULL OR starts_at <= ends_at)
);

-- ---------------------------------------------------------------------
-- indexes
-- ---------------------------------------------------------------------
CREATE INDEX IF NOT EXISTS idx_announcements_status
  ON announcements(status);

CREATE INDEX IF NOT EXISTS idx_announcements_is_pinned
  ON announcements(is_pinned);

CREATE INDEX IF NOT EXISTS idx_announcements_published_at
  ON announcements(published_at);

CREATE INDEX IF NOT EXISTS idx_announcements_starts_at
  ON announcements(starts_at);

CREATE INDEX IF NOT EXISTS idx_announcements_ends_at
  ON announcements(ends_at);

CREATE INDEX IF NOT EXISTS idx_announcements_author_id
  ON announcements(author_id);

-- ---------------------------------------------------------------------
-- comments
-- ---------------------------------------------------------------------
COMMENT ON COLUMN announcements.status
  IS 'ACTIVE | PENDING | DISABLED';

COMMENT ON COLUMN announcements.published_at
  IS '게시 일시(게시 상태로 전환된 시각); 초안(DISABLED)인 경우 null';

COMMENT ON COLUMN announcements.starts_at
  IS '노출 시작 일시(선택)';

COMMENT ON COLUMN announcements.ends_at
  IS '노출 종료 일시(선택)';

COMMENT ON COLUMN announcements.deleted_at
  IS '소프트 삭제 일시(선택)';