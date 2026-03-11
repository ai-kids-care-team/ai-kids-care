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


-- Dashboard metrics table
CREATE TABLE IF NOT EXISTS dashboard_metrics
(
    id          BIGSERIAL PRIMARY KEY,
    metric_name VARCHAR(255)     NOT NULL,
    value       DOUBLE PRECISION NOT NULL,
    unit        VARCHAR(50)      NOT NULL,
    created_at  TIMESTAMP        NOT NULL DEFAULT CURRENT_TIMESTAMP
);


-- Initial dashboard metrics
INSERT INTO dashboard_metrics (metric_name, value, unit, created_at)
VALUES ('CPU 사용률', 65.5, '%', CURRENT_TIMESTAMP),
       ('메모리 사용량', 78.2, '%', CURRENT_TIMESTAMP),
       ('디스크 사용량', 45.0, '%', CURRENT_TIMESTAMP),
       ('네트워크 트래픽', 120.5, 'Mbps', CURRENT_TIMESTAMP),
       ('API 응답 시간', 95.0, 'ms', CURRENT_TIMESTAMP),
       ('활성 사용자', 234.0, '명', CURRENT_TIMESTAMP);