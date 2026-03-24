INSERT INTO class_room_assignments (
    kindergarten_id, class_id, room_id, start_at, end_at, purpose, note, status, created_by_user_id, created_at, updated_at
)
SELECT 
    c.kindergarten_id,
    c.class_id,
    r.room_id,
    CURRENT_TIMESTAMP,
    NULL,
    '정규반 교실',
    '',
    'ACTIVE',
    1,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
FROM classes c
JOIN LATERAL (
    SELECT room_id FROM rooms 
    WHERE kindergarten_id = c.kindergarten_id AND room_type = '교실'
    ORDER BY RANDOM() LIMIT 1
) r ON true;