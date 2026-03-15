-- Initial accounts (password: password - BCrypt encoded)
INSERT INTO users (login_id, password_hash, email, phone, status, created_at, updated_at)
SELECT 'admin',
       '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
       'admin@example.com',
       '010-0000-0000',
       'ACTIVE',
       CURRENT_TIMESTAMP,
       CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM users WHERE login_id = 'admin');

INSERT INTO users (login_id, password_hash, email, phone, status, created_at, updated_at)
SELECT 'user',
       '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
       'user@example.com',
       '010-1111-1111',
       'ACTIVE',
       CURRENT_TIMESTAMP,
       CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM users WHERE login_id = 'user');
