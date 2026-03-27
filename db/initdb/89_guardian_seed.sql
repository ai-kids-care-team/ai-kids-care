-- Guardian reference seed data
-- Usage example:
--   docker exec ai-kids-postgres psql -U kids_user -d kids_postgres_db -f /path/to/guardian_seed.sql

BEGIN;

-- 1) kindergarten
INSERT INTO kindergartens (kindergarten_id, name, address, region_code, code,business_registration_no, contact_name, contact_phone,
                           contact_email, status, created_at, updated_at)
SELECT 1001,
       '해맑은유치원',
       '서울시 강남구 테헤란로 100',
       'KR-11',
       'KG-1001',
       '1234567890',
       '원장 김해맑',
       '02-1234-5678',
       'admin@haemalg.kr',
       'ACTIVE',
       CURRENT_TIMESTAMP,
       CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1
                  FROM kindergartens
                  WHERE kindergarten_id = 1001);

-- 2) classes
INSERT INTO classes (class_id, kindergarten_id, name, grade, academic_year, start_date, end_date, status, created_at,
                     updated_at)
SELECT 2001,
       1001,
       '햇살반',
       '5세',
       2026,
       DATE '2026-03-01',
       DATE '2027-02-28',
       'ACTIVE',
       CURRENT_TIMESTAMP,
       CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1
                  FROM classes
                  WHERE class_id = 2001);

INSERT INTO classes (class_id, kindergarten_id, name, grade, academic_year, start_date, end_date, status, created_at,
                     updated_at)
SELECT 2002,
       1001,
       '별님반',
       '6세',
       2026,
       DATE '2026-03-01',
       DATE '2027-02-28',
       'ACTIVE',
       CURRENT_TIMESTAMP,
       CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1
                  FROM classes
                  WHERE class_id = 2002);

-- 3) children (5)
-- [RRN 원본 참고 - 주석용]
-- 3001: 200101-4037926
INSERT INTO children (child_id, kindergarten_id, name, child_no, rrn_encrypted, rrn_first6, birth_date, gender, address,
                      enroll_date, leave_date, status, created_at, updated_at)
SELECT 3001,
       1001,
       '김하린',
       'C-2026-001',
       '$2b$12$GvzjrpbBmVUgUAWKSFJ/3uZJghO9MmljN/KvI0SAaqtRT.4/iT.HC',
       '200101',
       DATE '2020-01-01',
       'FEMALE',
       '서울시 강남구 역삼동 101',
       DATE '2026-03-02',
       NULL,
       'ACTIVE',
       CURRENT_TIMESTAMP,
       CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM children WHERE child_id = 3001);

-- 3002: 200315-3045123
INSERT INTO children (child_id, kindergarten_id, name, child_no, rrn_encrypted, rrn_first6, birth_date, gender, address,
                      enroll_date, leave_date, status, created_at, updated_at)
SELECT 3002,
       1001,
       '이준호',
       'C-2026-002',
       '$2b$12$nOA.5pYF7pU5hxLs7wa1Pe6lsIeKcm5pWU94Hewdz5e8R247EhIA2',
       '200315',
       DATE '2020-03-15',
       'MALE',
       '서울시 강남구 논현동 202',
       DATE '2026-03-02',
       NULL,
       'ACTIVE',
       CURRENT_TIMESTAMP,
       CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM children WHERE child_id = 3002);

-- 3003: 200707-4034567
INSERT INTO children (child_id, kindergarten_id, name, child_no, rrn_encrypted, rrn_first6, birth_date, gender, address,
                      enroll_date, leave_date, status, created_at, updated_at)
SELECT 3003,
       1001,
       '박서윤',
       'C-2026-003',
       '$2b$12$tgP47OJqPXQFlBE2AlWj0ud65A73u.g6tgQY8nfXFi60KmC1O5gfu',
       '200707',
       DATE '2020-07-07',
       'FEMALE',
       '서울시 강남구 삼성동 303',
       DATE '2026-03-03',
       NULL,
       'ACTIVE',
       CURRENT_TIMESTAMP,
       CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM children WHERE child_id = 3003);

-- 3004: 200920-3098765
INSERT INTO children (child_id, kindergarten_id, name, child_no, rrn_encrypted, rrn_first6, birth_date, gender, address,
                      enroll_date, leave_date, status, created_at, updated_at)
SELECT 3004,
       1001,
       '최민우',
       'C-2026-004',
       '$2b$12$FpG34DjcXi8nUKH26y/AAeVkzANMNCQSa25Ukvx8ufojDdxENkd6y',
       '200920',
       DATE '2020-09-20',
       'MALE',
       '서울시 강남구 대치동 404',
       DATE '2026-03-03',
       NULL,
       'ACTIVE',
       CURRENT_TIMESTAMP,
       CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM children WHERE child_id = 3004);

-- 3005: 201211-4043210
INSERT INTO children (child_id, kindergarten_id, name, child_no, rrn_encrypted, rrn_first6, birth_date, gender, address,
                      enroll_date, leave_date, status, created_at, updated_at)
SELECT 3005,
       1001,
       '정지안',
       'C-2026-005',
       '$2b$12$VFSbUPq3/RXGZqZPqjnuqeKcW.ehyVOR7FYCJhuhU9dLV8cY8Q1cO',
       '201211',
       DATE '2020-12-11',
       'FEMALE',
       '서울시 강남구 청담동 505',
       DATE '2026-03-04',
       NULL,
       'ACTIVE',
       CURRENT_TIMESTAMP,
       CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM children WHERE child_id = 3005);

-- 4) GUARDIAN user accounts (password: password)
-- Verified bcrypt hash
-- $2a$10$b.n.JJjUqCXeE4oddfSaa.pNAV1bNFVfaGaZJiqemWqWK/c5zSELm
-- guardian_4001 plaintext password: password
INSERT INTO users (user_id, login_id, password_hash, email, phone, status, last_login_at, created_at, updated_at)
SELECT 4001,
       'guardian_4001',
       '$2a$10$b.n.JJjUqCXeE4oddfSaa.pNAV1bNFVfaGaZJiqemWqWK/c5zSELm',
       'guardian4001@example.com',
       '010-4001-0001',
       'ACTIVE',
       CURRENT_TIMESTAMP,
       CURRENT_TIMESTAMP,
       CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM users WHERE user_id = 4001 OR login_id = 'guardian_4001');

-- guardian_4002 plaintext password: password
INSERT INTO users (user_id, login_id, password_hash, email, phone, status, last_login_at, created_at, updated_at)
SELECT 4002,
       'guardian_4002',
       '$2a$10$b.n.JJjUqCXeE4oddfSaa.pNAV1bNFVfaGaZJiqemWqWK/c5zSELm',
       'guardian4002@example.com',
       '010-4002-0002',
       'ACTIVE',
       CURRENT_TIMESTAMP,
       CURRENT_TIMESTAMP,
       CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM users WHERE user_id = 4002 OR login_id = 'guardian_4002');

-- guardian_4003 plaintext password: password
INSERT INTO users (user_id, login_id, password_hash, email, phone, status, last_login_at, created_at, updated_at)
SELECT 4003,
       'guardian_4003',
       '$2a$10$b.n.JJjUqCXeE4oddfSaa.pNAV1bNFVfaGaZJiqemWqWK/c5zSELm',
       'guardian4003@example.com',
       '010-4003-0003',
       'ACTIVE',
       CURRENT_TIMESTAMP,
       CURRENT_TIMESTAMP,
       CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM users WHERE user_id = 4003 OR login_id = 'guardian_4003');

-- guardian_4004 plaintext password: password
INSERT INTO users (user_id, login_id, password_hash, email, phone, status, last_login_at, created_at, updated_at)
SELECT 4004,
       'guardian_4004',
       '$2a$10$b.n.JJjUqCXeE4oddfSaa.pNAV1bNFVfaGaZJiqemWqWK/c5zSELm',
       'guardian4004@example.com',
       '010-4004-0004',
       'ACTIVE',
       CURRENT_TIMESTAMP,
       CURRENT_TIMESTAMP,
       CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM users WHERE user_id = 4004 OR login_id = 'guardian_4004');

-- guardian_4005 plaintext password: password
INSERT INTO users (user_id, login_id, password_hash, email, phone, status, last_login_at, created_at, updated_at)
SELECT 4005,
       'guardian_4005',
       '$2a$10$b.n.JJjUqCXeE4oddfSaa.pNAV1bNFVfaGaZJiqemWqWK/c5zSELm',
       'guardian4005@example.com',
       '010-4005-0005',
       'ACTIVE',
       CURRENT_TIMESTAMP,
       CURRENT_TIMESTAMP,
       CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM users WHERE user_id = 4005 OR login_id = 'guardian_4005');

-- 5) guardians (5)
-- [RRN 원본 참고 - 주석용]
-- 5001: 880101-2881123
INSERT INTO guardians (guardian_id, kindergarten_id, user_id, name, rrn_encrypted, rrn_first6, gender, address, status,
                       created_at, updated_at)
SELECT 5001,
       1001,
       4001,
       '김지은',
       'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIyODgxMTIzIiwiaWF0IjoxNzczOTcxOTM0LCJleHAiOjQxMDI0NDQ4MDB9.5AiJyXrX99RAzW-3hblymV73g_HGEq2b73nnIzYNDFE',
       '880101',
       'FEMALE',
       '서울시 강남구 역삼동 11',
       'ACTIVE',
       CURRENT_TIMESTAMP,
       CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM guardians WHERE guardian_id = 5001);

-- 5002: 870315-1734567
INSERT INTO guardians (guardian_id, kindergarten_id, user_id, name, rrn_encrypted, rrn_first6, gender, address, status,
                       created_at, updated_at)
SELECT 5002,
       1001,
       4002,
       '이현우',
       'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxNzM0NTY3IiwiaWF0IjoxNzczOTcxOTM0LCJleHAiOjQxMDI0NDQ4MDB9.LQaNSFId1OvBq_9BSBw-8hFKZiw9kgyTPrbd-6MzgF4',
       '870315',
       'MALE',
       '서울시 강남구 논현동 22',
       'ACTIVE',
       CURRENT_TIMESTAMP,
       CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM guardians WHERE guardian_id = 5002);

-- 5003: 890707-2896543
INSERT INTO guardians (guardian_id, kindergarten_id, user_id, name, rrn_encrypted, rrn_first6, gender, address, status,
                       created_at, updated_at)
SELECT 5003,
       1001,
       4003,
       '박서진',
       'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIyODk2NTQzIiwiaWF0IjoxNzczOTcxOTM0LCJleHAiOjQxMDI0NDQ4MDB9.QkS1mmYZzNAQOKv85qpOSc8NKEjjQlrSG4Iu_EHYMkc',
       '890707',
       'FEMALE',
       '서울시 강남구 삼성동 33',
       'ACTIVE',
       CURRENT_TIMESTAMP,
       CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM guardians WHERE guardian_id = 5003);

-- 5004: 860920-2867788
INSERT INTO guardians (guardian_id, kindergarten_id, user_id, name, rrn_encrypted, rrn_first6, gender, address, status,
                       created_at, updated_at)
SELECT 5004,
       1001,
       4004,
       '최민정',
       'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIyODY3Nzg4IiwiaWF0IjoxNzczOTcxOTM0LCJleHAiOjQxMDI0NDQ4MDB9.9ZiujLrQZYH6d2b0TdApl6blN56ovgzgffk-24Wr5vs',
       '860920',
       'FEMALE',
       '서울시 강남구 대치동 44',
       'ACTIVE',
       CURRENT_TIMESTAMP,
       CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM guardians WHERE guardian_id = 5004);

-- 5005: 881211-1800199
INSERT INTO guardians (guardian_id, kindergarten_id, user_id, name, rrn_encrypted, rrn_first6, gender, address, status,
                       created_at, updated_at)
SELECT 5005,
       1001,
       4005,
       '정수빈',
       'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxODAwMTk5IiwiaWF0IjoxNzczOTcxOTM0LCJleHAiOjQxMDI0NDQ4MDB9.SkYMCP9HVk2Hy-CZS0ArmxeDDIdiLMlB_ODvXQEoslE',
       '881211',
       'MALE',
       '서울시 강남구 청담동 55',
       'ACTIVE',
       CURRENT_TIMESTAMP,
       CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM guardians WHERE guardian_id = 5005);

-- 5-1 TEACHER user accounts (password: password)
-- teacher_4101 plaintext password: password
INSERT INTO users (user_id, login_id, password_hash, email, phone, status, last_login_at, created_at, updated_at)
SELECT 4101,
       'teacher_4101',
       '$2a$10$b.n.JJjUqCXeE4oddfSaa.pNAV1bNFVfaGaZJiqemWqWK/c5zSELm',
       'teacher4101@example.com',
       '010-4101-0001',
       'ACTIVE',
       CURRENT_TIMESTAMP,
       CURRENT_TIMESTAMP,
       CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM users WHERE user_id = 4101 OR login_id = 'teacher_4101');

-- teacher_4102 plaintext password: password
INSERT INTO users (user_id, login_id, password_hash, email, phone, status, last_login_at, created_at, updated_at)
SELECT 4102,
       'teacher_4102',
       '$2a$10$b.n.JJjUqCXeE4oddfSaa.pNAV1bNFVfaGaZJiqemWqWK/c5zSELm',
       'teacher4102@example.com',
       '010-4102-0002',
       'ACTIVE',
       CURRENT_TIMESTAMP,
       CURRENT_TIMESTAMP,
       CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM users WHERE user_id = 4102 OR login_id = 'teacher_4102');

-- teacher_4103 plaintext password: password
INSERT INTO users (user_id, login_id, password_hash, email, phone, status, last_login_at, created_at, updated_at)
SELECT 4103,
       'teacher_4103',
       '$2a$10$b.n.JJjUqCXeE4oddfSaa.pNAV1bNFVfaGaZJiqemWqWK/c5zSELm',
       'teacher4103@example.com',
       '010-4103-0003',
       'ACTIVE',
       CURRENT_TIMESTAMP,
       CURRENT_TIMESTAMP,
       CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM users WHERE user_id = 4103 OR login_id = 'teacher_4103');

-- 5-2) teachers (3)
-- [RRN 원본 참고 - 주석용]
-- 5201: 900101-1456789
INSERT INTO teachers (teacher_id, kindergarten_id, user_id, staff_no, name, gender, emergency_contact_name, emergency_contact_phone,
                      rrn_encrypted, rrn_first6, level, start_date, end_date, status, created_at, updated_at)
SELECT 5201,
       1001,
       4101,
       'T-1001',
       '한지민',
       'FEMALE',
       '한보호',
       '010-7101-1001',
       'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxNDU2Nzg5IiwiaWF0IjoxNzczOTc1MDYyLCJleHAiOjQxMDI0NDQ4MDB9.KLwgVqWndWuzW-QJDktAVTWS4JdyDBuTWM4Nwmilt74',
       '900101',
       'DIRECTOR',
       DATE '2024-03-01',
       NULL,
       'ACTIVE',
       CURRENT_TIMESTAMP,
       CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM teachers WHERE teacher_id = 5201);

-- 5202: 910202-2345678
INSERT INTO teachers (teacher_id, kindergarten_id, user_id, staff_no, name, gender, emergency_contact_name, emergency_contact_phone,
                      rrn_encrypted, rrn_first6, level, start_date, end_date, status, created_at, updated_at)
SELECT 5202,
       1001,
       4102,
       'T-1002',
       '오성민',
       'MALE',
       '오보호',
       '010-7102-1002',
       'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIyMzQ1Njc4IiwiaWF0IjoxNzczOTc1MDYyLCJleHAiOjQxMDI0NDQ4MDB9.1TOTW2AyxRChQb_YgGOopbXTrsc6SBkR-O4W3kfymhI',
       '910202',
       'TEACHER',
       DATE '2024-03-01',
       NULL,
       'ACTIVE',
       CURRENT_TIMESTAMP,
       CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM teachers WHERE teacher_id = 5202);

-- 5203: 920303-3567890
INSERT INTO teachers (teacher_id, kindergarten_id, user_id, staff_no, name, gender, emergency_contact_name, emergency_contact_phone,
                      rrn_encrypted, rrn_first6, level, start_date, end_date, status, created_at, updated_at)
SELECT 5203,
       1001,
       4103,
       'T-1003',
       '김나연',
       'FEMALE',
       '김보호',
       '010-7103-1003',
       'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIzNTY3ODkwIiwiaWF0IjoxNzczOTc1MDYyLCJleHAiOjQxMDI0NDQ4MDB9.8s9rkW0rgo7wANSqhRuu3gDEXyyXToZE2GpeidUsgFA',
       '920303',
       'TEACHER',
       DATE '2025-03-01',
       NULL,
       'ACTIVE',
       CURRENT_TIMESTAMP,
       CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM teachers WHERE teacher_id = 5203);

-- 6) child-GUARDIAN relationship (5)
INSERT INTO child_guardian_relationships (kindergarten_id, child_id, guardian_id, relationship, is_primary, priority,
                                          start_date, end_date, created_at, updated_at)
SELECT 1001,
       3001,
       5001,
       'MOTHER',
       TRUE,
       1,
       DATE '2026-03-02',
       NULL,
       CURRENT_TIMESTAMP,
       CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1
                  FROM child_guardian_relationships
                  WHERE kindergarten_id = 1001 AND child_id = 3001 AND guardian_id = 5001);

INSERT INTO child_guardian_relationships (kindergarten_id, child_id, guardian_id, relationship, is_primary, priority,
                                          start_date, end_date, created_at, updated_at)
SELECT 1001,
       3002,
       5002,
       'FATHER',
       TRUE,
       1,
       DATE '2026-03-02',
       NULL,
       CURRENT_TIMESTAMP,
       CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1
                  FROM child_guardian_relationships
                  WHERE kindergarten_id = 1001 AND child_id = 3002 AND guardian_id = 5002);

INSERT INTO child_guardian_relationships (kindergarten_id, child_id, guardian_id, relationship, is_primary, priority,
                                          start_date, end_date, created_at, updated_at)
SELECT 1001,
       3003,
       5003,
       'MOTHER',
       TRUE,
       1,
       DATE '2026-03-03',
       NULL,
       CURRENT_TIMESTAMP,
       CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1
                  FROM child_guardian_relationships
                  WHERE kindergarten_id = 1001 AND child_id = 3003 AND guardian_id = 5003);

INSERT INTO child_guardian_relationships (kindergarten_id, child_id, guardian_id, relationship, is_primary, priority,
                                          start_date, end_date, created_at, updated_at)
SELECT 1001,
       3004,
       5004,
       'MOTHER',
       TRUE,
       1,
       DATE '2026-03-03',
       NULL,
       CURRENT_TIMESTAMP,
       CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1
                  FROM child_guardian_relationships
                  WHERE kindergarten_id = 1001 AND child_id = 3004 AND guardian_id = 5004);

INSERT INTO child_guardian_relationships (kindergarten_id, child_id, guardian_id, relationship, is_primary, priority,
                                          start_date, end_date, created_at, updated_at)
SELECT 1001,
       3005,
       5005,
       'FATHER',
       TRUE,
       1,
       DATE '2026-03-04',
       NULL,
       CURRENT_TIMESTAMP,
       CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1
                  FROM child_guardian_relationships
                  WHERE kindergarten_id = 1001 AND child_id = 3005 AND guardian_id = 5005);

-- 7) role assignment (GUARDIAN)
INSERT INTO user_role_assignments (role_assignment_id, user_id, role, scope_type, scope_id, status, granted_at,
                                   granted_by_user_id, revoked_at)
SELECT 6001,
       4001,
       'GUARDIAN',
       'KINDERGARTEN',
       1001,
       'ACTIVE',
       CURRENT_TIMESTAMP,
       NULL,
       NULL
WHERE NOT EXISTS (SELECT 1
                  FROM user_role_assignments
                  WHERE user_id = 4001
                    AND role = 'GUARDIAN'
                    AND scope_type = 'KINDERGARTEN'
                    AND scope_id = 1001
                    AND status = 'ACTIVE');

INSERT INTO user_role_assignments (role_assignment_id, user_id, role, scope_type, scope_id, status, granted_at,
                                   granted_by_user_id, revoked_at)
SELECT 6002,
       4002,
       'GUARDIAN',
       'KINDERGARTEN',
       1001,
       'ACTIVE',
       CURRENT_TIMESTAMP,
       NULL,
       NULL
WHERE NOT EXISTS (SELECT 1
                  FROM user_role_assignments
                  WHERE user_id = 4002
                    AND role = 'GUARDIAN'
                    AND scope_type = 'KINDERGARTEN'
                    AND scope_id = 1001
                    AND status = 'ACTIVE');

INSERT INTO user_role_assignments (role_assignment_id, user_id, role, scope_type, scope_id, status, granted_at,
                                   granted_by_user_id, revoked_at)
SELECT 6003,
       4003,
       'GUARDIAN',
       'KINDERGARTEN',
       1001,
       'ACTIVE',
       CURRENT_TIMESTAMP,
       NULL,
       NULL
WHERE NOT EXISTS (SELECT 1
                  FROM user_role_assignments
                  WHERE user_id = 4003
                    AND role = 'GUARDIAN'
                    AND scope_type = 'KINDERGARTEN'
                    AND scope_id = 1001
                    AND status = 'ACTIVE');

INSERT INTO user_role_assignments (role_assignment_id, user_id, role, scope_type, scope_id, status, granted_at,
                                   granted_by_user_id, revoked_at)
SELECT 6004,
       4004,
       'GUARDIAN',
       'KINDERGARTEN',
       1001,
       'ACTIVE',
       CURRENT_TIMESTAMP,
       NULL,
       NULL
WHERE NOT EXISTS (SELECT 1
                  FROM user_role_assignments
                  WHERE user_id = 4004
                    AND role = 'GUARDIAN'
                    AND scope_type = 'KINDERGARTEN'
                    AND scope_id = 1001
                    AND status = 'ACTIVE');

INSERT INTO user_role_assignments (role_assignment_id, user_id, role, scope_type, scope_id, status, granted_at,
                                   granted_by_user_id, revoked_at)
SELECT 6005,
       4005,
       'GUARDIAN',
       'KINDERGARTEN',
       1001,
       'ACTIVE',
       CURRENT_TIMESTAMP,
       NULL,
       NULL
WHERE NOT EXISTS (SELECT 1
                  FROM user_role_assignments
                  WHERE user_id = 4005
                    AND role = 'GUARDIAN'
                    AND scope_type = 'KINDERGARTEN'
                    AND scope_id = 1001
                    AND status = 'ACTIVE');

INSERT INTO user_role_assignments
    (user_id, role, scope_type, scope_id, status, granted_at, granted_by_user_id, revoked_at)
SELECT admin_user.user_id,
       'KINDERGARTEN_ADMIN',
       'KINDERGARTEN',
       1001,
       'ACTIVE',
       CURRENT_TIMESTAMP,
       NULL,
       NULL
FROM (SELECT user_id FROM users WHERE login_id = 'admin' ORDER BY user_id LIMIT 1) AS admin_user
WHERE NOT EXISTS (SELECT 1
                  FROM user_role_assignments ura
                  WHERE ura.user_id = admin_user.user_id
                    AND ura.role = 'KINDERGARTEN_ADMIN'
                    AND ura.scope_type = 'KINDERGARTEN'
                    AND ura.scope_id = 1001
                    AND ura.status = 'ACTIVE');

-- 7-1 role assignment (TEACHER)
INSERT INTO user_role_assignments (role_assignment_id, user_id, role, scope_type, scope_id, status, granted_at,
                                   granted_by_user_id, revoked_at)
SELECT 6101,
       4101,
       'TEACHER',
       'KINDERGARTEN',
       1001,
       'ACTIVE',
       CURRENT_TIMESTAMP,
       NULL,
       NULL
WHERE NOT EXISTS (SELECT 1
                  FROM user_role_assignments
                  WHERE user_id = 4101
                    AND role = 'TEACHER'
                    AND scope_type = 'KINDERGARTEN'
                    AND scope_id = 1001
                    AND status = 'ACTIVE');

INSERT INTO user_role_assignments (role_assignment_id, user_id, role, scope_type, scope_id, status, granted_at,
                                   granted_by_user_id, revoked_at)
SELECT 6102,
       4102,
       'TEACHER',
       'KINDERGARTEN',
       1001,
       'ACTIVE',
       CURRENT_TIMESTAMP,
       NULL,
       NULL
WHERE NOT EXISTS (SELECT 1
                  FROM user_role_assignments
                  WHERE user_id = 4102
                    AND role = 'TEACHER'
                    AND scope_type = 'KINDERGARTEN'
                    AND scope_id = 1001
                    AND status = 'ACTIVE');

INSERT INTO user_role_assignments (role_assignment_id, user_id, role, scope_type, scope_id, status, granted_at,
                                   granted_by_user_id, revoked_at)
SELECT 6103,
       4103,
       'TEACHER',
       'KINDERGARTEN',
       1001,
       'ACTIVE',
       CURRENT_TIMESTAMP,
       NULL,
       NULL
WHERE NOT EXISTS (SELECT 1
                  FROM user_role_assignments
                  WHERE user_id = 4103
                    AND role = 'TEACHER'
                    AND scope_type = 'KINDERGARTEN'
                    AND scope_id = 1001
                    AND status = 'ACTIVE');


-- 8) kindergarten membership
INSERT INTO user_kindergarten_memberships (membership_id, user_id, kindergarten_id, status, joined_at, left_at,
                                           created_at, updated_at)
SELECT 7001,
       4001,
       1001,
       'ACTIVE',
       CURRENT_TIMESTAMP,
       NULL,
       CURRENT_TIMESTAMP,
       CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1
                  FROM user_kindergarten_memberships
                  WHERE user_id = 4001 AND kindergarten_id = 1001 AND status = 'ACTIVE');

INSERT INTO user_kindergarten_memberships (membership_id, user_id, kindergarten_id, status, joined_at, left_at,
                                           created_at, updated_at)
SELECT 7002,
       4002,
       1001,
       'ACTIVE',
       CURRENT_TIMESTAMP,
       NULL,
       CURRENT_TIMESTAMP,
       CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1
                  FROM user_kindergarten_memberships
                  WHERE user_id = 4002 AND kindergarten_id = 1001 AND status = 'ACTIVE');

INSERT INTO user_kindergarten_memberships (membership_id, user_id, kindergarten_id, status, joined_at, left_at,
                                           created_at, updated_at)
SELECT 7003,
       4003,
       1001,
       'ACTIVE',
       CURRENT_TIMESTAMP,
       NULL,
       CURRENT_TIMESTAMP,
       CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1
                  FROM user_kindergarten_memberships
                  WHERE user_id = 4003 AND kindergarten_id = 1001 AND status = 'ACTIVE');

INSERT INTO user_kindergarten_memberships (membership_id, user_id, kindergarten_id, status, joined_at, left_at,
                                           created_at, updated_at)
SELECT 7004,
       4004,
       1001,
       'ACTIVE',
       CURRENT_TIMESTAMP,
       NULL,
       CURRENT_TIMESTAMP,
       CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1
                  FROM user_kindergarten_memberships
                  WHERE user_id = 4004 AND kindergarten_id = 1001 AND status = 'ACTIVE');

INSERT INTO user_kindergarten_memberships (membership_id, user_id, kindergarten_id, status, joined_at, left_at,
                                           created_at, updated_at)
SELECT 7005,
       4005,
       1001,
       'ACTIVE',
       CURRENT_TIMESTAMP,
       NULL,
       CURRENT_TIMESTAMP,
       CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1
                  FROM user_kindergarten_memberships
                  WHERE user_id = 4005 AND kindergarten_id = 1001 AND status = 'ACTIVE');

-- 8-1) kindergarten membership (TEACHER)
INSERT INTO user_kindergarten_memberships (membership_id, user_id, kindergarten_id, status, joined_at, left_at,
                                           created_at, updated_at)
SELECT 7101,
       4101,
       1001,
       'ACTIVE',
       CURRENT_TIMESTAMP,
       NULL,
       CURRENT_TIMESTAMP,
       CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1
                  FROM user_kindergarten_memberships
                  WHERE user_id = 4101 AND kindergarten_id = 1001 AND status = 'ACTIVE');

INSERT INTO user_kindergarten_memberships (membership_id, user_id, kindergarten_id, status, joined_at, left_at,
                                           created_at, updated_at)
SELECT 7102,
       4102,
       1001,
       'ACTIVE',
       CURRENT_TIMESTAMP,
       NULL,
       CURRENT_TIMESTAMP,
       CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1
                  FROM user_kindergarten_memberships
                  WHERE user_id = 4102 AND kindergarten_id = 1001 AND status = 'ACTIVE');

INSERT INTO user_kindergarten_memberships (membership_id, user_id, kindergarten_id, status, joined_at, left_at,
                                           created_at, updated_at)
SELECT 7103,
       4103,
       1001,
       'ACTIVE',
       CURRENT_TIMESTAMP,
       NULL,
       CURRENT_TIMESTAMP,
       CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1
                  FROM user_kindergarten_memberships
                  WHERE user_id = 4103 AND kindergarten_id = 1001 AND status = 'ACTIVE');

COMMIT;
