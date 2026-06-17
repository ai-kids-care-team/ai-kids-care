-- V5__rrn_hash_enforce.sql
-- ADR-0024 D4: 阶段三 — rrn_hash NOT NULL 강제 + UNIQUE 约束 升级
--
-- 【배포 전제 / Deployment prerequisite - HARD GATE】
--   아래 질의 결과가 모두 0 이어야 한다. 0 이 아니면 이 마이그레이션은 실패한다.
--   SELECT count(*) FROM children  WHERE rrn_hash IS NULL;  -- must be 0
--   SELECT count(*) FROM guardians WHERE rrn_hash IS NULL;  -- must be 0
--   SELECT count(*) FROM teachers  WHERE rrn_hash IS NULL;  -- must be 0
--   현재 persistent DB 에 rrn_hash IS NULL 행이 있으면 V5 실행 전 반드시
--   DB를 fresh initdb 로 재설정하거나 ADR-0024 D7 재검증 흐름으로 회전해야 한다.

-- Step 1: rrn_hash NOT NULL 강제
ALTER TABLE children  ALTER COLUMN rrn_hash SET NOT NULL;
ALTER TABLE guardians ALTER COLUMN rrn_hash SET NOT NULL;
ALTER TABLE teachers  ALTER COLUMN rrn_hash SET NOT NULL;

-- Step 2: V4 에서 생성된 유니크 인덱스 제거 (UNIQUE 제약으로 교체)
-- initdb(01_create_schema.sql) 경로에서도 같은 이름으로 생성되므로 IF EXISTS 로 양쪽 모두 처리
DROP INDEX IF EXISTS uq_children_rrn_hash;
DROP INDEX IF EXISTS uq_guardians_rrn_hash;
DROP INDEX IF EXISTS uq_teachers_rrn_hash;

-- Step 3: UNIQUE 제약 추가 (ADR-0010 §95 UNIQUE(rrn_hash))
ALTER TABLE children  ADD CONSTRAINT uq_children_rrn_hash  UNIQUE (rrn_hash);
ALTER TABLE guardians ADD CONSTRAINT uq_guardians_rrn_hash UNIQUE (rrn_hash);
ALTER TABLE teachers  ADD CONSTRAINT uq_teachers_rrn_hash  UNIQUE (rrn_hash);
