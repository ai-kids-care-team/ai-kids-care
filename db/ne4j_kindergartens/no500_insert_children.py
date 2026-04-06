from neo4j_connect import driver
import csv
from datetime import datetime, date


# ============================================================
# child 적재 스크립트
# ------------------------------------------------------------
# 목적:
#   children.csv 파일을 읽어서 Neo4j의 (:Child) 노드로 적재
#
# 실행 예:
#   python load_children.py
#
# CSV 예:
#   child_id,kindergarten_id,name,child_no,rrn_first6,rrn_encrypted,
#   birth_date,gender,address,enroll_date,leave_date,status,
#   created_at,updated_at
# ============================================================


# ============================================================
# 날짜/시간 문자열 정리 함수
# ------------------------------------------------------------
# 예:
#   2023-03-01 04:49:09.000000 +00:00
# → 2023-03-01T04:49:09+00:00
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
#   2020-09-21 → 2020-09-21
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
# Child 노드 생성/업데이트
# ------------------------------------------------------------
# 기준 키:
#   child_id
#
# 중복 방지:
#   MERGE 사용
# ============================================================
def create_child_node(tx, row):
    print("DEBUG CHILD ROW:", row)

    child_id = int(row["child_id"])

    tx.run("""
        // ======================================================
        // 1. Child 노드 생성 또는 조회
        // ======================================================
        MERGE (c:Child {child_id: $child_id})

        // ======================================================
        // 2. 속성 설정
        // ------------------------------------------------------
        // kindergarten_id는 나중에 관계 생성에도 활용 가능
        // 예:
        //   (c)-[:BELONGS_TO]->(k:Kindergarten)
        // child_no는 원아 관리번호
        // ======================================================
        SET c.kindergarten_id = $kindergarten_id,
            c.name = $name,
            c.child_no = $child_no,
            c.rrn_first6 = $rrn_first6,
            c.rrn_encrypted = $rrn_encrypted,
            c.birth_date = $birth_date,
            c.gender = $gender,
            c.address = $address,
            c.enroll_date = $enroll_date,
            c.leave_date = $leave_date,
            c.status = $status,
            c.created_at = $created_at,
            c.updated_at = $updated_at
    """,
    child_id=child_id,
    kindergarten_id=int(row["kindergarten_id"]) if clean_value(row.get("kindergarten_id")) else None,
    name=clean_value(row.get("name")),
    child_no=clean_value(row.get("child_no")),
    rrn_first6=clean_value(row.get("rrn_first6")),
    rrn_encrypted=clean_value(row.get("rrn_encrypted")),
    birth_date=fix_date(row.get("birth_date")),
    gender=clean_value(row.get("gender")),
    address=clean_value(row.get("address")),
    enroll_date=fix_date(row.get("enroll_date")),
    leave_date=fix_date(row.get("leave_date")),
    status=clean_value(row.get("status")),
    created_at=fix_datetime(row.get("created_at")),
    updated_at=fix_datetime(row.get("updated_at"))
    )


# ============================================================
# 제약조건 생성
# ------------------------------------------------------------
# child_id 유니크 제약조건
# ============================================================
def create_constraint(tx):
    tx.run("""
        CREATE CONSTRAINT child_id_unique IF NOT EXISTS
        FOR (c:Child)
        REQUIRE c.child_id IS UNIQUE
    """)


# ============================================================
# 메인 실행
# ============================================================
if __name__ == "__main__":
    csv_file_path = "./data/500_children.csv"

    with driver.session() as session:
        # --------------------------------------------------------
        # 0. 제약조건 생성
        # --------------------------------------------------------
        session.execute_write(create_constraint)
        print("✅ constraint 생성/확인 완료: Child.child_id UNIQUE")

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
                    if not row.get("child_id"):
                        print("⚠️ SKIP: child_id 없음", row)
                        continue

                    # ------------------------------------------------
                    # 노드 적재
                    # ------------------------------------------------
                    session.execute_write(create_child_node, row)

                    print(
                        f"✅ Child 적재 완료: "
                        f"id={row['child_id']}, "
                        f"name={row.get('name')}, "
                        f"child_no={row.get('child_no')}"
                    )

                except Exception as e:
                    print(
                        f"❌ Child 적재 실패: "
                        f"id={row.get('child_id')}, error={e}"
                    )

    print("🎉 children.csv → Child 노드 적재 완료")


# ============================================================
# 의미 있는 조회용 Cypher 쿼리 모음
# ------------------------------------------------------------
# 아래 쿼리는 Neo4j Browser / cypher-shell 에서 확인용으로 사용
# ============================================================

"""
============================================================
1. 전체 Child 샘플 조회
============================================================
MATCH (c:Child)
RETURN c
LIMIT 10;

설명:
- 적재가 실제로 되었는지 빠르게 확인
- 속성명이 원하는 형태로 들어갔는지 점검


============================================================
2. 전체 Child 수 확인
============================================================
MATCH (c:Child)
RETURN count(c) AS total_children;

설명:
- CSV 행 수와 비교
- 누락 여부 확인


============================================================
3. 특정 Child 단건 조회
============================================================
MATCH (c:Child {child_id: 1})
RETURN c;

설명:
- 단일 데이터 검증
- 샘플 row 확인용


============================================================
4. 보기 좋게 목록 조회
============================================================
MATCH (c:Child)
RETURN
    c.child_id AS child_id,
    c.name AS name,
    c.child_no AS child_no,
    c.gender AS gender,
    c.birth_date AS birth_date,
    c.status AS status,
    c.kindergarten_id AS kindergarten_id
ORDER BY c.child_id;

설명:
- 사람이 읽기 좋은 목록 확인
- 유치원 연결용 키도 같이 점검


============================================================
5. 상태별 Child 수
============================================================
MATCH (c:Child)
RETURN c.status AS status, count(*) AS cnt
ORDER BY cnt DESC;

설명:
- ACTIVE / DISABLED / 휴원 / 퇴원 상태 등 분포 확인


============================================================
6. 성별 분포
============================================================
MATCH (c:Child)
RETURN c.gender AS gender, count(*) AS cnt
ORDER BY cnt DESC;

설명:
- MALE / FEMALE 분포 확인


============================================================
7. 유치원별 Child 수
============================================================
MATCH (c:Child)
RETURN c.kindergarten_id AS kindergarten_id, count(*) AS cnt
ORDER BY cnt DESC, kindergarten_id;

설명:
- 어떤 유치원에 원아가 몇 명 있는지 기본 점검


============================================================
8. child_no 중복 검사
============================================================
MATCH (c:Child)
WHERE c.child_no IS NOT NULL
WITH c.child_no AS child_no, count(*) AS cnt, collect(c.child_id) AS ids
WHERE cnt > 1
RETURN child_no, cnt, ids;

설명:
- 원아번호 중복 여부 확인
- 데이터 정합성 점검


============================================================
9. 주민번호 앞 6자리(rrn_first6) 중복 검사
============================================================
MATCH (c:Child)
WHERE c.rrn_first6 IS NOT NULL
WITH c.rrn_first6 AS rrn_first6, count(*) AS cnt, collect(c.child_id) AS ids
WHERE cnt > 1
RETURN rrn_first6, cnt, ids;

설명:
- 테스트 데이터 중복 여부 확인
- 실제 운영에서는 민감정보 취급 주의


============================================================
10. 주소 없는 Child 찾기
============================================================
MATCH (c:Child)
WHERE c.address IS NULL
RETURN
    c.child_id AS child_id,
    c.name AS name,
    c.child_no AS child_no;

설명:
- 주소 누락 데이터 확인


============================================================
11. 퇴원일(leave_date)이 있는 Child 조회
============================================================
MATCH (c:Child)
WHERE c.leave_date IS NOT NULL
RETURN
    c.child_id AS child_id,
    c.name AS name,
    c.enroll_date AS enroll_date,
    c.leave_date AS leave_date,
    c.status AS status
ORDER BY c.leave_date DESC;

설명:
- 종료/퇴원 데이터 확인


============================================================
12. 최근 수정된 Child 조회
============================================================
MATCH (c:Child)
RETURN
    c.child_id AS child_id,
    c.name AS name,
    c.updated_at AS updated_at
ORDER BY c.updated_at DESC
LIMIT 10;

설명:
- 최근 변경 데이터 확인
- 적재 후 updated_at 정상 여부 점검


============================================================
13. Kindergarten와 조인처럼 확인
============================================================
MATCH (c:Child)
MATCH (k:Kindergarten {kindergarten_id: c.kindergarten_id})
RETURN
    c.child_id AS child_id,
    c.name AS child_name,
    k.kindergarten_id AS kindergarten_id,
    k.name AS kindergarten_name
ORDER BY c.child_id;

설명:
- 아직 관계를 안 만들었어도
  속성값(kindergarten_id)으로 매칭해서 검증 가능


============================================================
14. 특정 이름 검색
============================================================
MATCH (c:Child)
WHERE c.name CONTAINS '수'
RETURN
    c.child_id AS child_id,
    c.name AS name,
    c.child_no AS child_no,
    c.kindergarten_id AS kindergarten_id;

설명:
- 이름 검색 예시
- 한글 검색 확인


============================================================
15. Child 노드 전체 삭제 테스트용
============================================================
MATCH (c:Child)
DETACH DELETE c;

설명:
- 테스트 환경에서만 사용
- 관계가 생긴 뒤에도 같이 지우려면 DETACH DELETE 사용
"""