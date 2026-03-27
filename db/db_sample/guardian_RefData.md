# Guardian 샘플 데이터 작업 정리

## 목적
- 양육자 회원가입/로그인 및 아동-보호자 연관 기능을 테스트할 수 있도록 최소 참조 데이터(Reference Data)를 구성했다.
- FK 제약을 만족하는 순서로 `kindergarten -> class_entity -> child -> user_account -> guardian -> child_guardian_relationship -> user_role_assignment -> user_kindergarten_membership` 데이터를 채웠다.

## 반영 파일
- `db/initdb/03_KINDERGARTEN.sql`
- `db/initdb/89_guardian_seed.sql`

## 변경 이력
- 2026-03-07: 샘플 INSERT 중복 제거 완료 (`03_KINDERGARTEN.sql`에서 샘플 블록 제거, `guardian_seed.sql` 단일 유지)
- 2026-03-08: 샘플 SQL 파일을 `db/db_sample/guardian_seed.sql`에서 `db/initdb/89_guardian_seed.sql`로 이동 및 리네임

## 실행 방법
### 1) Docker(PostgreSQL 컨테이너)에서 실행
```bash
docker exec -i ai-kids-postgres psql -U kids_user -d kids_postgres_db < db/initdb/89_guardian_seed.sql
```

### 2) 로컬 psql로 실행 (선택)
```bash
psql -h localhost -p 5432 -U kids_user -d kids_postgres_db -f db/initdb/89_guardian_seed.sql
```

## 빠른 검증 쿼리
```sql
-- child
SELECT COUNT(*) AS child_cnt
FROM child
WHERE child_id BETWEEN 3001 AND 3005;

-- guardian
SELECT COUNT(*) AS guardian_cnt
FROM guardian
WHERE guardian_id BETWEEN 5001 AND 5005;

-- child_guardian_relationship
SELECT COUNT(*) AS rel_cnt
FROM child_guardian_relationship
WHERE kindergarten_id = 1001
  AND child_id BETWEEN 3001 AND 3005
  AND guardian_id BETWEEN 5001 AND 5005;

-- role assignment
SELECT COUNT(*) AS role_cnt
FROM user_role_assignment
WHERE user_id BETWEEN 4001 AND 4005
  AND role = 'GUARDIAN'
  AND scope_type = 'KINDERGARTEN'
  AND scope_id = 1001
  AND status = 'ACTIVE';

-- membership
SELECT COUNT(*) AS membership_cnt
FROM user_kindergarten_membership
WHERE user_id BETWEEN 4001 AND 4005
  AND kindergarten_id = 1001
  AND status = 'ACTIVE';
```

## 작업 흐름
1. **CHILD 샘플 5명 추가 준비**
   - `child`는 `kindergarten_id`, `(kindergarten_id, class_id)` FK를 가지므로 선행 데이터가 필요했다.
   - 먼저 `kindergarten(1001)` 1건, `class_entity(2001, 2002)` 2건을 추가했다.

2. **CHILD 샘플 5명 추가**
   - `child_id=3001~3005`로 5건 추가.
   - `status=ENROLLED`, `created_at/updated_at=CURRENT_TIMESTAMP`로 구성.
   - 주민번호 관련 컬럼은 샘플 문자열(`rrn_encrypted`, `rrn_first6`)로 채움.

3. **보호자 계정(user_account) 5건 추가**
   - `user_id=4001~4005`, `login_id=guardian_4001~guardian_4005`.
   - `status=ACTIVE`, 시간 컬럼은 `CURRENT_TIMESTAMP`.
   - 비밀번호는 테스트용 `password`로 로그인 가능하도록 BCrypt 해시를 사용.

4. **guardian 5건 추가**
   - `guardian_id=5001~5005`, `kindergarten_id=1001`, `user_id=4001~4005`로 1:1 매핑.
   - `status=ACTIVE`, 시간 컬럼은 `CURRENT_TIMESTAMP`.

5. **아동-보호자 관계(child_guardian_relationship) 5건 추가**
   - 아동 `3001~3005` 각각에 보호자 `5001~5005`를 연결.
   - `is_primary=true`, `priority=1`.
   - 관계값은 샘플로 `MOTHER/FATHER` 사용.

6. **권한(user_role_assignment) 추가**
   - `role_assignment_id=6001~6005`.
   - `user_id=4001~4005`에 `role=GUARDIAN`, `scope_type=KINDERGARTEN`, `scope_id=1001`, `status=ACTIVE` 부여.

7. **소속(user_kindergarten_membership) 추가**
   - `membership_id=7001~7005`.
   - `user_id=4001~4005`를 `kindergarten_id=1001`에 `ACTIVE`로 등록.

## 데이터 키 범위(요약)
- `kindergarten_id`: `1001`
- `class_id`: `2001~2002`
- `child_id`: `3001~3005`
- `user_id(guardian account)`: `4001~4005`
- `guardian_id`: `5001~5005`
- `role_assignment_id`: `6001~6005`
- `membership_id`: `7001~7005`

## 검증 포인트
- `child` 5건 존재 및 반(`class_id`) 연결 확인
- `guardian` 5건 존재 및 `user_account` FK 연결 확인
- `child_guardian_relationship` 5건 존재 확인
- `user_role_assignment`에서 5명 모두 `GUARDIAN` 권한 보유 확인
- `user_kindergarten_membership`에서 5명 모두 `ACTIVE` 소속 확인
- 로그인 API 검증:
  - `guardian_4001~guardian_4005 / password` 로그인 성공
  - 응답 role이 `GUARDIAN`으로 내려오는 것 확인

## 회원가입 추가 매핑(양육자)
- `rrn_first6` : 프론트 입력값 그대로 `guardian.rrn_first6` 저장
- `rrn_encrypted` : 주민등록번호 뒷자리(7자리)를 백엔드에서 해시 처리 후 `guardian.rrn_encrypted` 저장
- `gender` : 주민번호 뒷자리 첫 숫자 기준으로 저장 (요구사항 기준: 남자 `F`, 여자 `M`)
- `is_primary` : `주된 보호자` 체크값을 `child_guardian_relationship.is_primary`로 저장
- `relationship` : 셀렉트 영문 코드 저장, `OTHER` 선택 시 사용자 입력 텍스트 저장

## 재실행 안정성
- 샘플 INSERT는 대부분 `WHERE NOT EXISTS` 패턴으로 구성해 중복 삽입을 방지했다.
- 동일 스크립트를 재실행해도 기존 샘플 데이터와 충돌하지 않도록 설계했다.
