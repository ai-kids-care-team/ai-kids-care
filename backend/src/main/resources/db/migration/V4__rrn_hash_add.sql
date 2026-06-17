-- V4__rrn_hash_add.sql
-- ADR-0024 D4: 阶段一 — 添加 rrn_hash 可空列 + 唯一索引 + 放松 rrn_encrypted NOT NULL
-- PostgreSQL 唯一索引对多 NULL 互不冲突（NULL != NULL），故存量 NULL 行安全，无需 WHERE IS NOT NULL 过滤。
-- V5/V6 受硬门控制，须在 rrn_hash IS NULL 计数归 0 后方可执行。
-- IF NOT EXISTS: initdb 路径의 01_create_schema.sql 도 같은 인덱스명으로 생성하므로 충돌 방지.

ALTER TABLE children   ADD COLUMN IF NOT EXISTS rrn_hash varchar;
ALTER TABLE guardians  ADD COLUMN IF NOT EXISTS rrn_hash varchar;
ALTER TABLE teachers   ADD COLUMN IF NOT EXISTS rrn_hash varchar;

-- 唯一索引：NULL 互不冲突，存量行安全
CREATE UNIQUE INDEX IF NOT EXISTS uq_children_rrn_hash  ON children(rrn_hash);
CREATE UNIQUE INDEX IF NOT EXISTS uq_guardians_rrn_hash ON guardians(rrn_hash);
CREATE UNIQUE INDEX IF NOT EXISTS uq_teachers_rrn_hash  ON teachers(rrn_hash);

-- 放松 rrn_encrypted NOT NULL（새 행은 rrn_hash만 씀）
ALTER TABLE children   ALTER COLUMN rrn_encrypted DROP NOT NULL;
ALTER TABLE guardians  ALTER COLUMN rrn_encrypted DROP NOT NULL;
ALTER TABLE teachers   ALTER COLUMN rrn_encrypted DROP NOT NULL;

-- 열 주석: 암호문 → 단방향 해시
COMMENT ON COLUMN children.rrn_hash   IS '주민등록번호 단방향 해시(검증용, 복호화 불가) — HMAC-SHA-256+pepper';
COMMENT ON COLUMN guardians.rrn_hash  IS '주민등록번호 단방향 해시(검증용, 복호화 불가) — HMAC-SHA-256+pepper';
COMMENT ON COLUMN teachers.rrn_hash   IS '주민등록번호 단방향 해시(검증용, 복호화 불가) — HMAC-SHA-256+pepper';
COMMENT ON COLUMN children.rrn_encrypted  IS '주민등록번호 뒷자리 레거시 BCrypt 해시(단방향, V6 삭제 예정)';
COMMENT ON COLUMN guardians.rrn_encrypted IS '주민등록번호 레거시 BCrypt 해시(단방향, V6 삭제 예정)';
COMMENT ON COLUMN teachers.rrn_encrypted  IS '주민등록번호 레거시 BCrypt 해시(단방향, V6 삭제 예정)';
