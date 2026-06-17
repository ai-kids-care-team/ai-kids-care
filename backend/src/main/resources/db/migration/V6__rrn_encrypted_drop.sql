-- V6__rrn_encrypted_drop.sql
-- ADR-0024 D4: 阶段三 — rrn_encrypted 열 삭제 (불가역 / irreversible)
--
-- 【배포 전제 / Deployment prerequisite】
--   V5 가 먼저 성공적으로 적용되어 있어야 한다 (rrn_hash NOT NULL 강제 완료).
--   이 마이그레이션은 불가역 (DROP COLUMN). 롤백 불가.
--   레거시 BCrypt 데이터가 완전히 제거된다.

ALTER TABLE children  DROP COLUMN IF EXISTS rrn_encrypted;
ALTER TABLE guardians DROP COLUMN IF EXISTS rrn_encrypted;
ALTER TABLE teachers  DROP COLUMN IF EXISTS rrn_encrypted;
