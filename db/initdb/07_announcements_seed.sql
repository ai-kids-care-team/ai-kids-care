-- ---------------------------------------------------------------------
-- announcements seed (UTF-8 file execution)
-- - Windows/PowerShell 인코딩 이슈를 피하기 위해 파일 실행(-f) 방식 사용
-- ---------------------------------------------------------------------
INSERT INTO announcements (
  author_id,
  title,
  body,
  status,
  starts_at,
  published_at,
  view_count,
  is_pinned
)
SELECT
  u.user_id,
  '2026년 AI Kids Care 서비스 오픈 안내',
  '안녕하세요, AI Kids Care 운영팀입니다.

2026년 3월 1일부터 AI Kids Care 서비스가 정식 오픈되었습니다.

AI Kids Care는 양육자, 유치원, 행정청, 시스템 관리자가 함께하는 통합 육아 지원 플랫폼으로,
우리 아이들의 건강하고 행복한 성장을 위해 최신 AI 기술을 활용합니다.

주요 기능:
1. 양육자 지원 서비스
2. 유치원 관리 시스템
3. 행정청 연계 기능
4. 통합 관제 대시보드

서비스 이용을 원하시는 분들은 회원가입 후 이용하실 수 있습니다.

감사합니다.',
  'ACTIVE',
  '2026-01-01 00:00:00+09',
  '2026-03-10 00:00:00+09',
  152,
  FALSE
FROM users u
WHERE u.user_id = COALESCE(
  (SELECT user_id FROM users WHERE login_id = 'admin' ORDER BY user_id LIMIT 1),
  (SELECT user_id FROM users ORDER BY user_id LIMIT 1)
)
AND NOT EXISTS (
  SELECT 1
  FROM announcements
  WHERE author_id = u.user_id
    AND title = '2026년 AI Kids Care 서비스 오픈 안내'
);

INSERT INTO announcements (
  author_id,
  title,
  body,
  status,
  starts_at,
  published_at,
  view_count,
  is_pinned
)
SELECT
  u.user_id,
  '개인정보 처리방침 개정 안내',
  '안녕하세요, AI Kids Care 운영팀입니다.

개인정보 처리방침이 최신 법령 및 내부 정책에 따라 개정되었습니다.
주요 변경사항은 서비스 내 개인정보 처리 범위와 보관 기간 안내 문구 보완입니다.

변경된 약관은 공지일 기준으로 즉시 적용되며, 자세한 내용은 약관 페이지에서 확인하실 수 있습니다.

감사합니다.',
  'ACTIVE',
  '2026-01-01 00:00:00+09',
  '2026-03-08 00:00:00+09',
  98,
  FALSE
FROM users u
WHERE u.user_id = COALESCE(
  (SELECT user_id FROM users WHERE login_id = 'admin' ORDER BY user_id LIMIT 1),
  (SELECT user_id FROM users ORDER BY user_id LIMIT 1)
)
AND NOT EXISTS (
  SELECT 1
  FROM announcements
  WHERE author_id = u.user_id
    AND title = '개인정보 처리방침 개정 안내'
);

INSERT INTO announcements (
  author_id,
  title,
  body,
  status,
  starts_at,
  published_at,
  view_count,
  is_pinned
)
SELECT
  u.user_id,
  '회원가입 시 주의사항 안내',
  '안녕하세요, AI Kids Care 운영팀입니다.

회원가입 시 정확한 보호자/교직원 정보를 입력해 주세요.
입력 정보가 실제 기관 정보와 다를 경우 일부 기능 사용이 제한될 수 있습니다.

원활한 서비스 이용을 위해 가입 후 프로필 정보도 함께 확인 부탁드립니다.

감사합니다.',
  'ACTIVE',
  '2026-01-01 00:00:00+09',
  '2026-03-05 00:00:00+09',
  76,
  FALSE
FROM users u
WHERE u.user_id = COALESCE(
  (SELECT user_id FROM users WHERE login_id = 'admin' ORDER BY user_id LIMIT 1),
  (SELECT user_id FROM users ORDER BY user_id LIMIT 1)
)
AND NOT EXISTS (
  SELECT 1
  FROM announcements
  WHERE author_id = u.user_id
    AND title = '회원가입 시 주의사항 안내'
);

INSERT INTO announcements (
  author_id,
  title,
  body,
  status,
  starts_at,
  published_at,
  view_count,
  is_pinned
)
SELECT
  u.user_id,
  '양육자 및 유치원 등록 절차 안내',
  '안녕하세요, AI Kids Care 운영팀입니다.

양육자 계정 등록 후 유치원 연동 신청을 진행하시면 서비스 기능을 모두 이용하실 수 있습니다.
유치원 측 승인 완료 후 반/원아 정보 확인 기능이 활성화됩니다.

등록 과정에서 문의사항이 있으면 고객센터로 연락 부탁드립니다.

감사합니다.',
  'ACTIVE',
  '2026-01-01 00:00:00+09',
  '2026-03-01 00:00:00+09',
  64,
  FALSE
FROM users u
WHERE u.user_id = COALESCE(
  (SELECT user_id FROM users WHERE login_id = 'admin' ORDER BY user_id LIMIT 1),
  (SELECT user_id FROM users ORDER BY user_id LIMIT 1)
)
AND NOT EXISTS (
  SELECT 1
  FROM announcements
  WHERE author_id = u.user_id
    AND title = '양육자 및 유치원 등록 절차 안내'
);

INSERT INTO announcements (
  author_id,
  title,
  body,
  status,
  starts_at,
  published_at,
  view_count,
  is_pinned
)
SELECT
  u.user_id,
  '시스템 점검 일정 공지',
  '안녕하세요, AI Kids Care 운영팀입니다.

보다 안정적인 서비스 제공을 위해 아래 일정으로 시스템 점검이 진행됩니다.
- 점검 일시: 2026-02-28 01:00 ~ 03:00 (KST)
- 점검 범위: 공지/알림 및 일부 조회 기능

점검 시간 동안 서비스 이용이 일시적으로 제한될 수 있습니다.
이용에 불편을 드려 죄송합니다.',
  'ACTIVE',
  '2026-01-01 00:00:00+09',
  '2026-02-28 00:00:00+09',
  45,
  FALSE
FROM users u
WHERE u.user_id = COALESCE(
  (SELECT user_id FROM users WHERE login_id = 'admin' ORDER BY user_id LIMIT 1),
  (SELECT user_id FROM users ORDER BY user_id LIMIT 1)
)
AND NOT EXISTS (
  SELECT 1
  FROM announcements
  WHERE author_id = u.user_id
    AND title = '시스템 점검 일정 공지'
);

INSERT INTO announcements (
  author_id,
  title,
  body,
  status,
  starts_at,
  published_at,
  view_count,
  is_pinned
)
SELECT
  u.user_id,
  '유치원 원장 대상 보험료 할인 안내',
  '안녕하세요, AI Kids Care 운영팀입니다.

AI Kids Care 시스템을 도입하여 운영 중인 유치원을 대상으로
제휴 보험사 보험료 할인 혜택이 제공될 예정입니다.

적용 대상: AI Kids Care 정상 운영 기관
적용 기준: 제휴 보험사 심사 및 약관 기준 충족 시
안내 예정: 세부 할인율 및 제출 서류는 추후 별도 공지

원장님께서는 서비스 운영 상태와 보험 갱신 일정을 미리 확인해 주시기 바랍니다.

감사합니다.',
  'ACTIVE',
  '2026-01-01 00:00:00+09',
  '2026-03-17 00:00:00+09',
  0,
  TRUE
FROM users u
WHERE u.user_id = COALESCE(
  (SELECT user_id FROM users WHERE login_id = 'admin' ORDER BY user_id LIMIT 1),
  (SELECT user_id FROM users ORDER BY user_id LIMIT 1)
)
AND NOT EXISTS (
  SELECT 1
  FROM announcements
  WHERE author_id = u.user_id
    AND title = '유치원 원장 대상 보험료 할인 안내'
);