-- Timezone setup (KST)
SET TIME ZONE 'Asia/Seoul';

DO $$
DECLARE
    db_name text := current_database();
BEGIN
    EXECUTE format('ALTER DATABASE %I SET timezone TO %L', db_name, 'Asia/Seoul');
END $$;



-- 시스템관리자
-- PLATFORM_IT_ADMIN,12,hcheetamb


--행정청
-- SUPERADMIN,1,admin

-- 유치원 - 원장님
-- KINDERGARTEN_ADMIN,101,enozzolii2s
-- KINDERGARTEN_ADMIN,401,ekemmeb4
-- KINDERGARTEN_ADMIN,701,rpattonjg

-- 유치원 - 선생님
-- TEACHER,102,atithecote2t

-- 양육자
-- GUARDIAN,135,lmount3q

-- 양육자
-- GUARDIAN,144,cpaulo3z

