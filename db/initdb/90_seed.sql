-- Initial accounts
-- admin / user plaintext password: password
-- BCrypt verified with org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder

INSERT INTO users (login_id, password_hash, email, phone, status, created_at, updated_at)
SELECT 'admin',
       '$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG',
       'admin@example.com',
       '010-0000-0000',
       'ACTIVE',
       CURRENT_TIMESTAMP,
       CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM users WHERE login_id = 'admin');

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

INSERT INTO users (login_id, password_hash, email, phone, status, created_at, updated_at)
SELECT 'user',
       '$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG',
       'user@example.com',
       '010-1111-1111',
       'ACTIVE',
       CURRENT_TIMESTAMP,
       CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM users WHERE login_id = 'user');
