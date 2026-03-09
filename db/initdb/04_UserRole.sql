CREATE TABLE user_role (
role_id          BIGSERIAL PRIMARY KEY,
role_code        VARCHAR(40) NOT NULL UNIQUE,
role_name        VARCHAR(80) NOT NULL,
role_desc        VARCHAR(200),
sort_order       INT NOT NULL DEFAULT 0,
is_active        BOOLEAN NOT NULL DEFAULT TRUE,
created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),

-- 5개만 허용(고정)
CONSTRAINT chk_role_code
CHECK (role_code IN (
'GUARDIAN',
'TEACHER',
'KINDERGARTEN_ADMIN',
'PLATFORM_IT_ADMIN',
'SUPERADMIN'
))
);

CREATE INDEX idx_user_role_active ON user_role(is_active);
CREATE INDEX idx_user_role_sort ON user_role(sort_order);


INSERT INTO user_role (role_code, role_name, role_desc, sort_order)
VALUES
('GUARDIAN',            '양육자',       '아동 등록/조회, 병원 선택 등 일반 사용자', 10),
('TEACHER',             '유치원',       '유치원 관계자(교사) 기능 접근',           20),
('KINDERGARTEN_ADMIN',  '유치원원장',   '유치원 관리자(원장) 기능 접근',           30),
('PLATFORM_IT_ADMIN',   '시스템관리자', '플랫폼 운영/기술 관리 기능 접근',          40),
('SUPERADMIN',          '행정청',       '행정청 권한(최상위) 기능 접근',             50);




COMMENT ON COLUMN user_role.role_id IS '권한 고유 ID';

COMMENT ON COLUMN user_role.role_code IS '권한 코드 (GUARDIAN / TEACHER / KINDERGARTEN_ADMIN / PLATFORM_IT_ADMIN / SUPERADMIN)';

COMMENT ON COLUMN user_role.role_name IS '권한 이름 (양육자 / 유치원 / 유치원원장 / 시스템관리자 / 행정청)';

COMMENT ON COLUMN user_role.role_desc IS '권한 설명';

COMMENT ON COLUMN user_role.sort_order IS '권한 정렬 순서';

COMMENT ON COLUMN user_role.is_active IS '권한 사용 여부';

COMMENT ON COLUMN user_role.created_at IS '생성 일시';

COMMENT ON COLUMN user_role.updated_at IS '수정 일시';