-- Timezone setup (KST)
SET TIME ZONE 'Asia/Seoul';

DO $$
DECLARE
    db_name text := current_database();
BEGIN
    EXECUTE format('ALTER DATABASE %I SET timezone TO %L', db_name, 'Asia/Seoul');
END $$;