INSERT INTO user_role_assignments (
    user_id, role, scope_type, scope_id, status, granted_at, granted_by_user_id
)
SELECT 
    user_id,
    'TEACHER',
    'KINDERGARTEN',
    kindergarten_id,
    'ACTIVE',
    CURRENT_TIMESTAMP,
    1
FROM teachers;