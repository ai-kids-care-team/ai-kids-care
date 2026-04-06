from neo4j_connect import driver
import csv
from datetime import datetime, date


# ============================================================
# teacher 적재 스크립트
# ------------------------------------------------------------
# 목적:
#   teachers.csv 파일을 읽어서 Neo4j의 (:Teacher) 노드로 적재
#
# 실행 예:
#   python load_teachers.py
#
# CSV 예:
#   teacher_id,kindergarten_id,user_id,staff_no,name,gender,
#   emergency_contact_name,emergency_contact_phone,
#   rrn_encrypted,rrn_first6,level,start_date,end_date,
#   status,created_at,updated_at
# ============================================================


# ============================================================
# 날짜/시간 문자열 정리 함수
# ------------------------------------------------------------
# 예:
#   2025-06-04 15:15:29.000000 +00:00
# → 2025-06-04T15:15:29+00:00
# ============================================================
def fix_datetime(dt):
    if not dt or dt == "":
        return None

    value = str(dt).strip()

    # 잘못 들어간 끝 문자 보정
    if value.endswith("T"):
        value = value[:-1]

    try:
        value = value.replace(" ", "T", 1)
        return datetime.fromisoformat(value).isoformat()
    except Exception:
        return value


# ============================================================
# 날짜 문자열 정리 함수
# ------------------------------------------------------------
# 예:
#   2020-01-31 → 2020-01-31
# ============================================================
def fix_date(dt):
    if not dt or dt == "":
        return None

    value = str(dt).strip()

    try:
        return date.fromisoformat(value).isoformat()
    except Exception:
        return value


# ============================================================
# 문자열 정리 함수
# ------------------------------------------------------------
# 공백 제거 후 빈 문자열이면 None 처리
# ============================================================
def clean_value(value):
    if value is None:
        return None

    value = str(value).strip()
    if value == "":
        return None

    return value


# ============================================================
# Teacher 노드 생성/업데이트
# ------------------------------------------------------------
# 기준 키:
#   teacher_id
#
# 중복 방지:
#   MERGE 사용
# ============================================================
def create_teacher_node(tx, row):
    print("DEBUG TEACHER ROW:", row)

    teacher_id = int(row["teacher_id"])

    tx.run("""
        // ======================================================
        // 1. Teacher 노드 생성 또는 조회
        // ======================================================
        MERGE (t:Teacher {teacher_id: $teacher_id})

        // ======================================================
        // 2. 속성 설정
        // ------------------------------------------------------
        // kindergarten_id, user_id는 나중에 관계 생성에도 활용 가능
        // 예:
        //   (t)-[:BELONGS_TO]->(k:Kindergarten)
        //   (t)-[:HAS_USER]->(u:User)
        // ======================================================
        SET t.kindergarten_id = $kindergarten_id,
            t.user_id = $user_id,
            t.staff_no = $staff_no,
            t.name = $name,
            t.gender = $gender,
            t.emergency_contact_name = $emergency_contact_name,
            t.emergency_contact_phone = $emergency_contact_phone,
            t.rrn_encrypted = $rrn_encrypted,
            t.rrn_first6 = $rrn_first6,
            t.level = $level,
            t.start_date = $start_date,
            t.end_date = $end_date,
            t.status = $status,
            t.created_at = $created_at,
            t.updated_at = $updated_at
    """,
    teacher_id=teacher_id,
    kindergarten_id=int(row["kindergarten_id"]) if clean_value(row.get("kindergarten_id")) else None,
    user_id=int(row["user_id"]) if clean_value(row.get("user_id")) else None,
    staff_no=clean_value(row.get("staff_no")),
    name=clean_value(row.get("name")),
    gender=clean_value(row.get("gender")),
    emergency_contact_name=clean_value(row.get("emergency_contact_name")),
    emergency_contact_phone=clean_value(row.get("emergency_contact_phone")),
    rrn_encrypted=clean_value(row.get("rrn_encrypted")),
    rrn_first6=clean_value(row.get("rrn_first6")),
    level=clean_value(row.get("level")),
    start_date=fix_date(row.get("start_date")),
    end_date=fix_date(row.get("end_date")),
    status=clean_value(row.get("status")),
    created_at=fix_datetime(row.get("created_at")),
    updated_at=fix_datetime(row.get("updated_at"))
    )


# ============================================================
# 제약조건 생성
# ------------------------------------------------------------
# teacher_id 유니크 제약조건
# ============================================================
def create_constraint(tx):
    tx.run("""
        CREATE CONSTRAINT teacher_id_unique IF NOT EXISTS
        FOR (t:Teacher)
        REQUIRE t.teacher_id IS UNIQUE
    """)


# ============================================================
# 메인 실행
# ============================================================
if __name__ == "__main__":
    csv_file_path = "./data/300_teachers.csv"

    with driver.session() as session:
        # --------------------------------------------------------
        # 0. 제약조건 생성
        # --------------------------------------------------------
        session.execute_write(create_constraint)
        print("✅ constraint 생성/확인 완료: Teacher.teacher_id UNIQUE")

        # --------------------------------------------------------
        # 1. CSV 파일 읽기
        # --------------------------------------------------------
        with open(csv_file_path, "r", encoding="utf-8-sig") as file:
            reader = csv.DictReader(file)

            for row in reader:
                try:
                    # ------------------------------------------------
                    # 필수키 없는 행 스킵
                    # ------------------------------------------------
                    if not row.get("teacher_id"):
                        print("⚠️ SKIP: teacher_id 없음", row)
                        continue

                    # ------------------------------------------------
                    # 노드 적재
                    # ------------------------------------------------
                    session.execute_write(create_teacher_node, row)

                    print(
                        f"✅ Teacher 적재 완료: "
                        f"id={row['teacher_id']}, "
                        f"name={row.get('name')}, "
                        f"staff_no={row.get('staff_no')}"
                    )

                except Exception as e:
                    print(
                        f"❌ Teacher 적재 실패: "
                        f"id={row.get('teacher_id')}, error={e}"
                    )

    print("🎉 teachers.csv → Teacher 노드 적재 완료")


# ============================================================
# 의미 있는 조회용 Cypher 쿼리 모음
# ------------------------------------------------------------
# 아래 쿼리는 Neo4j Browser / cypher-shell 에서 확인용으로 사용
# ============================================================

"""
============================================================
1. 전체 Teacher 샘플 조회
============================================================
MATCH (t:Teacher)
RETURN t
LIMIT 10;

설명:
- 적재가 실제로 되었는지 빠르게 확인
- 속성명이 원하는 형태로 들어갔는지 점검


============================================================
2. 전체 Teacher 수 확인
============================================================
MATCH (t:Teacher)
RETURN count(t) AS total_teachers;

설명:
- CSV 행 수와 비교
- 누락 여부 확인


============================================================
3. 특정 Teacher 단건 조회
============================================================
MATCH (t:Teacher {teacher_id: 1})
RETURN t;

설명:
- 단일 데이터 검증
- 샘플 row 확인용


============================================================
4. 보기 좋게 목록 조회
============================================================
MATCH (t:Teacher)
RETURN
    t.teacher_id AS teacher_id,
    t.name AS name,
    t.staff_no AS staff_no,
    t.level AS level,
    t.status AS status,
    t.kindergarten_id AS kindergarten_id,
    t.user_id AS user_id
ORDER BY t.teacher_id;

설명:
- 사람이 읽기 좋은 목록 확인
- 유치원/유저 연결용 키도 같이 점검


============================================================
5. 상태별 Teacher 수
============================================================
MATCH (t:Teacher)
RETURN t.status AS status, count(*) AS cnt
ORDER BY cnt DESC;

설명:
- ACTIVE / INACTIVE / 퇴사 상태 등 분포 확인


============================================================
6. 직급(level)별 Teacher 수
============================================================
MATCH (t:Teacher)
RETURN t.level AS level, count(*) AS cnt
ORDER BY cnt DESC, level;

설명:
- DIRECTOR / VICE_DIRECTOR / TEACHER 등 분포 확인


============================================================
7. 유치원별 Teacher 수
============================================================
MATCH (t:Teacher)
RETURN t.kindergarten_id AS kindergarten_id, count(*) AS cnt
ORDER BY cnt DESC, kindergarten_id;

설명:
- 어떤 유치원에 선생님이 몇 명 있는지 기본 점검
- 나중에 Kindergarten 관계 생성 전 검증용


============================================================
8. user_id 없는 Teacher 찾기
============================================================
MATCH (t:Teacher)
WHERE t.user_id IS NULL
RETURN
    t.teacher_id AS teacher_id,
    t.name AS name,
    t.staff_no AS staff_no;

설명:
- Teacher ↔ User 연결 누락 가능성 점검


============================================================
9. staff_no 중복 검사
============================================================
MATCH (t:Teacher)
WHERE t.staff_no IS NOT NULL
WITH t.staff_no AS staff_no, count(*) AS cnt, collect(t.teacher_id) AS ids
WHERE cnt > 1
RETURN staff_no, cnt, ids;

설명:
- 직원번호 중복 여부 확인
- 데이터 정합성 점검


============================================================
10. 주민번호 앞 6자리(rrn_first6) 중복 검사
============================================================
MATCH (t:Teacher)
WHERE t.rrn_first6 IS NOT NULL
WITH t.rrn_first6 AS rrn_first6, count(*) AS cnt, collect(t.teacher_id) AS ids
WHERE cnt > 1
RETURN rrn_first6, cnt, ids;

설명:
- 테스트 데이터 중복 여부 확인
- 실제 운영에서는 민감정보 취급 주의


============================================================
11. 비상연락처 없는 Teacher 찾기
============================================================
MATCH (t:Teacher)
WHERE t.emergency_contact_name IS NULL OR t.emergency_contact_phone IS NULL
RETURN
    t.teacher_id AS teacher_id,
    t.name AS name,
    t.emergency_contact_name AS emergency_contact_name,
    t.emergency_contact_phone AS emergency_contact_phone;

설명:
- 운영 연락 정보 누락 데이터 확인


============================================================
12. 재직 시작일 기준 정렬
============================================================
MATCH (t:Teacher)
RETURN
    t.teacher_id AS teacher_id,
    t.name AS name,
    t.start_date AS start_date,
    t.end_date AS end_date,
    t.status AS status
ORDER BY t.start_date ASC;

설명:
- 오래 근무한 순 / 입사일 순 확인


============================================================
13. 퇴사일(end_date)이 있는 Teacher 조회
============================================================
MATCH (t:Teacher)
WHERE t.end_date IS NOT NULL
RETURN
    t.teacher_id AS teacher_id,
    t.name AS name,
    t.start_date AS start_date,
    t.end_date AS end_date,
    t.status AS status
ORDER BY t.end_date DESC;

설명:
- 종료/퇴사 데이터 확인


============================================================
14. 최근 수정된 Teacher 순으로 조회
============================================================
MATCH (t:Teacher)
RETURN
    t.teacher_id AS teacher_id,
    t.name AS name,
    t.updated_at AS updated_at
ORDER BY t.updated_at DESC
LIMIT 10;

설명:
- 최근 변경 데이터 확인
- 적재 후 updated_at 정상 여부 점검


============================================================
15. 이름으로 검색
============================================================
MATCH (t:Teacher)
WHERE t.name CONTAINS '정'
RETURN
    t.teacher_id AS teacher_id,
    t.name AS name,
    t.staff_no AS staff_no;

설명:
- 이름 검색 예시
- 한글 포함 여부 확인


============================================================
16. Kindergarten와 조인처럼 확인
============================================================
MATCH (t:Teacher)
MATCH (k:Kindergarten {kindergarten_id: t.kindergarten_id})
RETURN
    t.teacher_id AS teacher_id,
    t.name AS teacher_name,
    k.kindergarten_id AS kindergarten_id,
    k.name AS kindergarten_name
ORDER BY t.teacher_id;

설명:
- 아직 관계를 안 만들었어도
  속성값(kindergarten_id)으로 매칭해서 검증 가능


============================================================
17. User와 조인처럼 확인
============================================================
MATCH (t:Teacher)
MATCH (u:User {user_id: t.user_id})
RETURN
    t.teacher_id AS teacher_id,
    t.name AS teacher_name,
    u.user_id AS user_id,
    u.login_id AS login_id,
    u.email AS email
ORDER BY t.teacher_id;

설명:
- Teacher ↔ User 연결 전 사전 검증용


============================================================
18. Teacher 노드 전체 삭제 테스트용
============================================================
MATCH (t:Teacher)
DETACH DELETE t;

설명:
- 테스트 환경에서만 사용
- 관계가 생긴 뒤에도 같이 지우려면 DETACH DELETE 사용
"""