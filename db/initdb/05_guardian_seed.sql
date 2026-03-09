-- Guardian reference seed data
-- Usage example:
--   docker exec ai-kids-postgres psql -U kids_user -d kids_postgres_db -f /path/to/guardian_seed.sql

BEGIN;

-- 1) kindergarten
INSERT INTO kindergarten (
  kindergarten_id, name, address, region_code, code, contact_name, contact_phone, contact_email, status, created_at, updated_at
)
SELECT
  1001, '해맑은유치원', '서울시 강남구 테헤란로 100', 'KR-11', 'KG-1001',
  '원장 김해맑', '02-1234-5678', 'admin@haemalg.kr', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (
  SELECT 1 FROM kindergarten WHERE kindergarten_id = 1001
);

-- 2) classes
INSERT INTO class_entity (
  class_id, kindergarten_id, name, grade, academic_year, start_date, end_date, status, created_at, updated_at
)
SELECT
  2001, 1001, '햇살반', '5세', 2026, DATE '2026-03-01', DATE '2027-02-28', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (
  SELECT 1 FROM class_entity WHERE class_id = 2001
);

INSERT INTO class_entity (
  class_id, kindergarten_id, name, grade, academic_year, start_date, end_date, status, created_at, updated_at
)
SELECT
  2002, 1001, '별님반', '6세', 2026, DATE '2026-03-01', DATE '2027-02-28', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (
  SELECT 1 FROM class_entity WHERE class_id = 2002
);

-- 3) children (5)
INSERT INTO child (
  child_id, kindergarten_id, class_id, name, child_no, rrn_encrypted, rrn_first6, birth_date, gender, address, enroll_date, leave_date, status, created_at, updated_at
)
SELECT
  3001, 1001, 2001, '김하린', 'C-2026-001', 'enc_rrn_3001', '190101', DATE '2019-01-01', 'F',
  '서울시 강남구 역삼동 101', DATE '2026-03-02', NULL, 'ENROLLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM child WHERE child_id = 3001);

INSERT INTO child (
  child_id, kindergarten_id, class_id, name, child_no, rrn_encrypted, rrn_first6, birth_date, gender, address, enroll_date, leave_date, status, created_at, updated_at
)
SELECT
  3002, 1001, 2001, '이준호', 'C-2026-002', 'enc_rrn_3002', '190315', DATE '2019-03-15', 'M',
  '서울시 강남구 논현동 202', DATE '2026-03-02', NULL, 'ENROLLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM child WHERE child_id = 3002);

INSERT INTO child (
  child_id, kindergarten_id, class_id, name, child_no, rrn_encrypted, rrn_first6, birth_date, gender, address, enroll_date, leave_date, status, created_at, updated_at
)
SELECT
  3003, 1001, 2001, '박서윤', 'C-2026-003', 'enc_rrn_3003', '190707', DATE '2019-07-07', 'F',
  '서울시 강남구 삼성동 303', DATE '2026-03-03', NULL, 'ENROLLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM child WHERE child_id = 3003);

INSERT INTO child (
  child_id, kindergarten_id, class_id, name, child_no, rrn_encrypted, rrn_first6, birth_date, gender, address, enroll_date, leave_date, status, created_at, updated_at
)
SELECT
  3004, 1001, 2002, '최민우', 'C-2026-004', 'enc_rrn_3004', '180920', DATE '2018-09-20', 'M',
  '서울시 강남구 대치동 404', DATE '2026-03-03', NULL, 'ENROLLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM child WHERE child_id = 3004);

INSERT INTO child (
  child_id, kindergarten_id, class_id, name, child_no, rrn_encrypted, rrn_first6, birth_date, gender, address, enroll_date, leave_date, status, created_at, updated_at
)
SELECT
  3005, 1001, 2002, '정지안', 'C-2026-005', 'enc_rrn_3005', '181211', DATE '2018-12-11', 'F',
  '서울시 강남구 청담동 505', DATE '2026-03-04', NULL, 'ENROLLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM child WHERE child_id = 3005);

-- 4) guardian user accounts (password: password)
-- Verified bcrypt hash
-- $2a$10$b.n.JJjUqCXeE4oddfSaa.pNAV1bNFVfaGaZJiqemWqWK/c5zSELm
INSERT INTO user_account (
  user_id, user_name, login_id, password_hash, user_email, user_tel, status, last_login_at, created_at, updated_at
)
SELECT
  4001, '보호자 김지은', 'guardian_4001', '$2a$10$b.n.JJjUqCXeE4oddfSaa.pNAV1bNFVfaGaZJiqemWqWK/c5zSELm',
  'guardian4001@example.com', '010-4001-0001', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM user_account WHERE user_id = 4001 OR login_id = 'guardian_4001');

INSERT INTO user_account (
  user_id, user_name, login_id, password_hash, user_email, user_tel, status, last_login_at, created_at, updated_at
)
SELECT
  4002, '보호자 이현우', 'guardian_4002', '$2a$10$b.n.JJjUqCXeE4oddfSaa.pNAV1bNFVfaGaZJiqemWqWK/c5zSELm',
  'guardian4002@example.com', '010-4002-0002', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM user_account WHERE user_id = 4002 OR login_id = 'guardian_4002');

INSERT INTO user_account (
  user_id, user_name, login_id, password_hash, user_email, user_tel, status, last_login_at, created_at, updated_at
)
SELECT
  4003, '보호자 박서진', 'guardian_4003', '$2a$10$b.n.JJjUqCXeE4oddfSaa.pNAV1bNFVfaGaZJiqemWqWK/c5zSELm',
  'guardian4003@example.com', '010-4003-0003', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM user_account WHERE user_id = 4003 OR login_id = 'guardian_4003');

INSERT INTO user_account (
  user_id, user_name, login_id, password_hash, user_email, user_tel, status, last_login_at, created_at, updated_at
)
SELECT
  4004, '보호자 최민정', 'guardian_4004', '$2a$10$b.n.JJjUqCXeE4oddfSaa.pNAV1bNFVfaGaZJiqemWqWK/c5zSELm',
  'guardian4004@example.com', '010-4004-0004', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM user_account WHERE user_id = 4004 OR login_id = 'guardian_4004');

INSERT INTO user_account (
  user_id, user_name, login_id, password_hash, user_email, user_tel, status, last_login_at, created_at, updated_at
)
SELECT
  4005, '보호자 정수빈', 'guardian_4005', '$2a$10$b.n.JJjUqCXeE4oddfSaa.pNAV1bNFVfaGaZJiqemWqWK/c5zSELm',
  'guardian4005@example.com', '010-4005-0005', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM user_account WHERE user_id = 4005 OR login_id = 'guardian_4005');

-- 5) guardians (5)
INSERT INTO guardian (
  guardian_id, kindergarten_id, user_id, name, rrn_encrypted, rrn_first6, gender, phone, email, address, status, created_at, updated_at
)
SELECT
  5001, 1001, 4001, '김지은', 'enc_grr_5001', '880101', 'F',
  '010-4001-0001', 'guardian4001@example.com', '서울시 강남구 역삼동 11', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM guardian WHERE guardian_id = 5001);

INSERT INTO guardian (
  guardian_id, kindergarten_id, user_id, name, rrn_encrypted, rrn_first6, gender, phone, email, address, status, created_at, updated_at
)
SELECT
  5002, 1001, 4002, '이현우', 'enc_grr_5002', '870315', 'M',
  '010-4002-0002', 'guardian4002@example.com', '서울시 강남구 논현동 22', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM guardian WHERE guardian_id = 5002);

INSERT INTO guardian (
  guardian_id, kindergarten_id, user_id, name, rrn_encrypted, rrn_first6, gender, phone, email, address, status, created_at, updated_at
)
SELECT
  5003, 1001, 4003, '박서진', 'enc_grr_5003', '890707', 'F',
  '010-4003-0003', 'guardian4003@example.com', '서울시 강남구 삼성동 33', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM guardian WHERE guardian_id = 5003);

INSERT INTO guardian (
  guardian_id, kindergarten_id, user_id, name, rrn_encrypted, rrn_first6, gender, phone, email, address, status, created_at, updated_at
)
SELECT
  5004, 1001, 4004, '최민정', 'enc_grr_5004', '860920', 'F',
  '010-4004-0004', 'guardian4004@example.com', '서울시 강남구 대치동 44', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM guardian WHERE guardian_id = 5004);

INSERT INTO guardian (
  guardian_id, kindergarten_id, user_id, name, rrn_encrypted, rrn_first6, gender, phone, email, address, status, created_at, updated_at
)
SELECT
  5005, 1001, 4005, '정수빈', 'enc_grr_5005', '881211', 'M',
  '010-4005-0005', 'guardian4005@example.com', '서울시 강남구 청담동 55', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM guardian WHERE guardian_id = 5005);

-- 6) child-guardian relationship (5)
INSERT INTO child_guardian_relationship (
  kindergarten_id, child_id, guardian_id, relationship, is_primary, priority, start_date, end_date, created_at, updated_at
)
SELECT
  1001, 3001, 5001, 'MOTHER', TRUE, 1, DATE '2026-03-02', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (
  SELECT 1 FROM child_guardian_relationship WHERE kindergarten_id = 1001 AND child_id = 3001 AND guardian_id = 5001
);

INSERT INTO child_guardian_relationship (
  kindergarten_id, child_id, guardian_id, relationship, is_primary, priority, start_date, end_date, created_at, updated_at
)
SELECT
  1001, 3002, 5002, 'FATHER', TRUE, 1, DATE '2026-03-02', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (
  SELECT 1 FROM child_guardian_relationship WHERE kindergarten_id = 1001 AND child_id = 3002 AND guardian_id = 5002
);

INSERT INTO child_guardian_relationship (
  kindergarten_id, child_id, guardian_id, relationship, is_primary, priority, start_date, end_date, created_at, updated_at
)
SELECT
  1001, 3003, 5003, 'MOTHER', TRUE, 1, DATE '2026-03-03', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (
  SELECT 1 FROM child_guardian_relationship WHERE kindergarten_id = 1001 AND child_id = 3003 AND guardian_id = 5003
);

INSERT INTO child_guardian_relationship (
  kindergarten_id, child_id, guardian_id, relationship, is_primary, priority, start_date, end_date, created_at, updated_at
)
SELECT
  1001, 3004, 5004, 'MOTHER', TRUE, 1, DATE '2026-03-03', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (
  SELECT 1 FROM child_guardian_relationship WHERE kindergarten_id = 1001 AND child_id = 3004 AND guardian_id = 5004
);

INSERT INTO child_guardian_relationship (
  kindergarten_id, child_id, guardian_id, relationship, is_primary, priority, start_date, end_date, created_at, updated_at
)
SELECT
  1001, 3005, 5005, 'FATHER', TRUE, 1, DATE '2026-03-04', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (
  SELECT 1 FROM child_guardian_relationship WHERE kindergarten_id = 1001 AND child_id = 3005 AND guardian_id = 5005
);

-- 7) role assignment (guardian)
INSERT INTO user_role_assignment (
  role_assignment_id, user_id, role, scope_type, scope_id, status, granted_at, granted_by_user_id, revoked_at
)
SELECT
  6001, 4001, 'GUARDIAN', 'KINDERGARTEN', 1001, 'ACTIVE', CURRENT_TIMESTAMP, NULL, NULL
WHERE NOT EXISTS (
  SELECT 1 FROM user_role_assignment
  WHERE user_id = 4001 AND role = 'GUARDIAN' AND scope_type = 'KINDERGARTEN' AND scope_id = 1001 AND status = 'ACTIVE'
);

INSERT INTO user_role_assignment (
  role_assignment_id, user_id, role, scope_type, scope_id, status, granted_at, granted_by_user_id, revoked_at
)
SELECT
  6002, 4002, 'GUARDIAN', 'KINDERGARTEN', 1001, 'ACTIVE', CURRENT_TIMESTAMP, NULL, NULL
WHERE NOT EXISTS (
  SELECT 1 FROM user_role_assignment
  WHERE user_id = 4002 AND role = 'GUARDIAN' AND scope_type = 'KINDERGARTEN' AND scope_id = 1001 AND status = 'ACTIVE'
);

INSERT INTO user_role_assignment (
  role_assignment_id, user_id, role, scope_type, scope_id, status, granted_at, granted_by_user_id, revoked_at
)
SELECT
  6003, 4003, 'GUARDIAN', 'KINDERGARTEN', 1001, 'ACTIVE', CURRENT_TIMESTAMP, NULL, NULL
WHERE NOT EXISTS (
  SELECT 1 FROM user_role_assignment
  WHERE user_id = 4003 AND role = 'GUARDIAN' AND scope_type = 'KINDERGARTEN' AND scope_id = 1001 AND status = 'ACTIVE'
);

INSERT INTO user_role_assignment (
  role_assignment_id, user_id, role, scope_type, scope_id, status, granted_at, granted_by_user_id, revoked_at
)
SELECT
  6004, 4004, 'GUARDIAN', 'KINDERGARTEN', 1001, 'ACTIVE', CURRENT_TIMESTAMP, NULL, NULL
WHERE NOT EXISTS (
  SELECT 1 FROM user_role_assignment
  WHERE user_id = 4004 AND role = 'GUARDIAN' AND scope_type = 'KINDERGARTEN' AND scope_id = 1001 AND status = 'ACTIVE'
);

INSERT INTO user_role_assignment (
  role_assignment_id, user_id, role, scope_type, scope_id, status, granted_at, granted_by_user_id, revoked_at
)
SELECT
  6005, 4005, 'GUARDIAN', 'KINDERGARTEN', 1001, 'ACTIVE', CURRENT_TIMESTAMP, NULL, NULL
WHERE NOT EXISTS (
  SELECT 1 FROM user_role_assignment
  WHERE user_id = 4005 AND role = 'GUARDIAN' AND scope_type = 'KINDERGARTEN' AND scope_id = 1001 AND status = 'ACTIVE'
);

-- 8) kindergarten membership
INSERT INTO user_kindergarten_membership (
  membership_id, user_id, kindergarten_id, status, joined_at, left_at, created_at, updated_at
)
SELECT
  7001, 4001, 1001, 'ACTIVE', CURRENT_TIMESTAMP, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (
  SELECT 1 FROM user_kindergarten_membership WHERE user_id = 4001 AND kindergarten_id = 1001 AND status = 'ACTIVE'
);

INSERT INTO user_kindergarten_membership (
  membership_id, user_id, kindergarten_id, status, joined_at, left_at, created_at, updated_at
)
SELECT
  7002, 4002, 1001, 'ACTIVE', CURRENT_TIMESTAMP, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (
  SELECT 1 FROM user_kindergarten_membership WHERE user_id = 4002 AND kindergarten_id = 1001 AND status = 'ACTIVE'
);

INSERT INTO user_kindergarten_membership (
  membership_id, user_id, kindergarten_id, status, joined_at, left_at, created_at, updated_at
)
SELECT
  7003, 4003, 1001, 'ACTIVE', CURRENT_TIMESTAMP, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (
  SELECT 1 FROM user_kindergarten_membership WHERE user_id = 4003 AND kindergarten_id = 1001 AND status = 'ACTIVE'
);

INSERT INTO user_kindergarten_membership (
  membership_id, user_id, kindergarten_id, status, joined_at, left_at, created_at, updated_at
)
SELECT
  7004, 4004, 1001, 'ACTIVE', CURRENT_TIMESTAMP, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (
  SELECT 1 FROM user_kindergarten_membership WHERE user_id = 4004 AND kindergarten_id = 1001 AND status = 'ACTIVE'
);

INSERT INTO user_kindergarten_membership (
  membership_id, user_id, kindergarten_id, status, joined_at, left_at, created_at, updated_at
)
SELECT
  7005, 4005, 1001, 'ACTIVE', CURRENT_TIMESTAMP, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (
  SELECT 1 FROM user_kindergarten_membership WHERE user_id = 4005 AND kindergarten_id = 1001 AND status = 'ACTIVE'
);

COMMIT;
