INSERT INTO child_class_assignments (
    kindergarten_id, child_id, class_id, start_date, end_date, reason, note, status, created_by_user_id, created_at, updated_at
)
SELECT 
    c.kindergarten_id,
    c.child_id,
    cls.class_id,
    CURRENT_DATE,
    NULL,
    '신입원 배정',
    '초기 셋업',
    'ACTIVE',
    1,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
FROM children c
JOIN LATERAL (
    SELECT class_id FROM classes 
    WHERE kindergarten_id = c.kindergarten_id 
    ORDER BY RANDOM() LIMIT 1
) cls ON true;