INSERT INTO class_teacher_assignments (
    kindergarten_id, class_id, teacher_id, role, start_date, end_date, reason, note, status, created_by_user_id, created_at, updated_at
)
SELECT 
    c.kindergarten_id,
    c.class_id,
    t.teacher_id,
    '담임',
    CURRENT_DATE,
    NULL,
    '정규 담임 배정',
    '',
    'ACTIVE',
    1,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
FROM classes c
JOIN LATERAL (
    SELECT teacher_id FROM teachers 
    WHERE kindergarten_id = c.kindergarten_id 
    ORDER BY RANDOM() LIMIT 1
) t ON true;