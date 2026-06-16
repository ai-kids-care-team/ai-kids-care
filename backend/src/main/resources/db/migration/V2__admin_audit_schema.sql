-- V2__admin_audit_schema.sql
-- ADR-0021: REJECTED 상태, role/membership 완整性约束, audit_logs 플랫폼 scope + 보충 필드
-- PG 16: ALTER TYPE ... ADD VALUE 는 트랜잭션 내에서 허용 (같은 트랜잭션 내 사용 불가이나 여기서는 사용하지 않음)

-- ============================================================
-- 1. status_enum 에 REJECTED 값 추가
-- ============================================================
ALTER TYPE status_enum ADD VALUE IF NOT EXISTS 'REJECTED';

-- ============================================================
-- 2. user_role_assignments: scope_type/scope_id 합법 조합 CHECK + 부분 유일 인덱스
-- ============================================================
ALTER TABLE user_role_assignments
    ADD CONSTRAINT chk_ura_scope_type_id
        CHECK (
            (scope_type = 'PLATFORM' AND scope_id IS NULL)
            OR (scope_type = 'KINDERGARTEN' AND scope_id IS NOT NULL)
        );

CREATE UNIQUE INDEX uq_ura_one_active_per_user
    ON user_role_assignments (user_id)
    WHERE status = 'ACTIVE';

-- ============================================================
-- 3. user_kindergarten_memberships: 부분 유일 인덱스 (user당 ACTIVE membership 1개)
-- ============================================================
CREATE UNIQUE INDEX uq_ukm_one_active_per_user
    ON user_kindergarten_memberships (user_id)
    WHERE status = 'ACTIVE';

-- ============================================================
-- 4. audit_logs: 플랫폼 scope 지원 + SPEC 필드 보충
-- ============================================================

-- 4-1. scope_type 열 추가 (NOT NULL; 기존 행은 모두 KINDERGARTEN으로 기본값 적용 후 DEFAULT 제거)
ALTER TABLE audit_logs
    ADD COLUMN scope_type user_role_assignment_scope_type NOT NULL DEFAULT 'KINDERGARTEN';

-- 4-2. DEFAULT 제거 (이후 삽입 시 명시 필수)
ALTER TABLE audit_logs
    ALTER COLUMN scope_type DROP DEFAULT;

-- 4-3. kindergarten_id 를 가변(nullable) 로 변경
ALTER TABLE audit_logs
    ALTER COLUMN kindergarten_id DROP NOT NULL;

-- 4-4. user_id 를 가변(nullable) 로 변경
ALTER TABLE audit_logs
    ALTER COLUMN user_id DROP NOT NULL;

-- 4-5. scope 정합성 CHECK: PLATFORM이면 kindergarten_id 반드시 NULL
ALTER TABLE audit_logs
    ADD CONSTRAINT chk_audit_scope_kindergarten
        CHECK (
            (scope_type = 'PLATFORM' AND kindergarten_id IS NULL)
            OR (scope_type = 'KINDERGARTEN' AND kindergarten_id IS NOT NULL)
        );

-- 4-6. 보충 필드 추가
ALTER TABLE audit_logs
    ADD COLUMN effective_role varchar,
    ADD COLUMN result varchar,
    ADD COLUMN correlation_id varchar;
