-- 감사 편지 샘플 (21_users_seed, 24_kindergartens_seed, 30_teachers_seed, 29_guardians_seed 이후 실행)
-- sender_user_id 121 = guardian-kg1 (보호자, 원1)
-- target_type 'TEACHER' → target_id = teachers.teacher_id

INSERT INTO appreciation_letters (letter_id, kindergarten_id, sender_user_id, target_type, target_id, title, content, is_public, status, created_at, updated_at) VALUES
(1, 1, 121, 'TEACHER', 1, '늘 따뜻하게 맞아 주셔서 감사합니다', $$첫 등원 날 아이가 많이 울었는데, 선생님께서 차분히 달래 주시고 하루 종일 연락도 잘 주셔서 안심했습니다. 바쁘신 와중에도 한 아이 한 아이를 세심히 보살펴 주셔서 진심으로 감사드립니다.$$, true, 'ACTIVE', '2026-03-22 09:15:00+09', '2026-03-22 09:15:00+09');
