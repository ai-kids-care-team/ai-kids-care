from neo4j_connect import driver
import csv
from datetime import datetime


# ============================================================
# guardian 적재 스크립트
# ------------------------------------------------------------
# 목적:
#   guardians.csv 파일을 읽어서 Neo4j의 (:Guardian) 노드로 적재
#
# 실행 예:
#   python load_guardians.py
#
# CSV 예:
#   guardian_id,kindergarten_id,user_id,name,rrn_encrypted,
#   rrn_first6,gender,address,status,created_at,updated_at
# ============================================================


# ============================================================
# 날짜/시간 문자열 정리 함수
# ------------------------------------------------------------
# 예:
#   2025-12-08 14:26:10.000000 +00:00
# → 2025-12-08T14:26:10+00:00
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
# Guardian 노드 생성/업데이트
# ------------------------------------------------------------
# 기준 키:
#   guardian_id
#
# 중복 방지:
#   MERGE 사용
# ============================================================
def create_guardian_node(tx, row):
    print("DEBUG GUARDIAN ROW:", row)

    guardian_id = int(row["guardian_id"])

    tx.run("""
        // ======================================================
        // 1. Guardian 노드 생성 또는 조회
        // ======================================================
        MERGE (g:Guardian {guardian_id: $guardian_id})

        // ======================================================
        // 2. 속성 설정
        // ------------------------------------------------------
        // kindergarten_id, user_id는 나중에 관계 생성에도 활용 가능
        // 예:
        //   (g)-[:BELONGS_TO]->(k:Kindergarten)
        //   (g)-[:HAS_USER]->(u:User)
        // ======================================================
        SET g.kindergarten_id = $kindergarten_id,
            g.user_id = $user_id,
            g.name = $name,
            g.rrn_encrypted = $rrn_encrypted,
            g.rrn_first6 = $rrn_first6,
            g.gender = $gender,
            g.address = $address,
            g.status = $status,
            g.created_at = $created_at,
            g.updated_at = $updated_at
    """,
    guardian_id=guardian_id,
    kindergarten_id=int(row["kindergarten_id"]) if clean_value(row.get("kindergarten_id")) else None,
    user_id=int(row["user_id"]) if clean_value(row.get("user_id")) else None,
    name=clean_value(row.get("name")),
    rrn_encrypted=clean_value(row.get("rrn_encrypted")),
    rrn_first6=clean_value(row.get("rrn_first6")),
    gender=clean_value(row.get("gender")),
    address=clean_value(row.get("address")),
    status=clean_value(row.get("status")),
    created_at=fix_datetime(row.get("created_at")),
    updated_at=fix_datetime(row.get("updated_at"))
    )


# ============================================================
# 제약조건 생성
# ------------------------------------------------------------
# guardian_id 유니크 제약조건
# ============================================================
def create_constraint(tx):
    tx.run("""
        CREATE CONSTRAINT guardian_id_unique IF NOT EXISTS
        FOR (g:Guardian)
        REQUIRE g.guardian_id IS UNIQUE
    """)


# ============================================================
# 메인 실행
# ============================================================
if __name__ == "__main__":
    csv_file_path = "./data/600_guardians.csv"

    with driver.session() as session:
        # --------------------------------------------------------
        # 0. 제약조건 생성
        # --------------------------------------------------------
        session.execute_write(create_constraint)
        print("✅ constraint 생성/확인 완료: Guardian.guardian_id UNIQUE")

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
                    if not row.get("guardian_id"):
                        print("⚠️ SKIP: guardian_id 없음", row)
                        continue

                    # ------------------------------------------------
                    # 노드 적재
                    # ------------------------------------------------
                    session.execute_write(create_guardian_node, row)

                    print(
                        f"✅ Guardian 적재 완료: "
                        f"id={row['guardian_id']}, "
                        f"name={row.get('name')}, "
                        f"user_id={row.get('user_id')}"
                    )

                except Exception as e:
                    print(
                        f"❌ Guardian 적재 실패: "
                        f"id={row.get('guardian_id')}, error={e}"
                    )

    print("🎉 guardians.csv → Guardian 노드 적재 완료")


# ============================================================
# 의미 있는 조회용 Cypher 쿼리 모음
# ------------------------------------------------------------
# 아래 쿼리는 Neo4j Browser / cypher-shell 에서 확인용으로 사용
# ============================================================

"""
============================================================
1. 전체 Guardian 샘플 조회
============================================================
MATCH (g:Guardian)
RETURN g
LIMIT 10;

설명:
- 적재가 실제로 되었는지 빠르게 확인
- 속성명이 원하는 형태로 들어갔는지 점검


============================================================
2. 전체 Guardian 수 확인
============================================================
MATCH (g:Guardian)
RETURN count(g) AS total_guardians;

설명:
- CSV 행 수와 비교
- 누락 여부 확인


============================================================
3. 특정 Guardian 단건 조회
============================================================
MATCH (g:Guardian {guardian_id: 1})
RETURN g;

설명:
- 단일 데이터 검증
- 샘플 row 확인용


============================================================
4. 보기 좋게 목록 조회
============================================================
MATCH (g:Guardian)
RETURN
    g.guardian_id AS guardian_id,
    g.name AS name,
    g.gender AS gender,
    g.status AS status,
    g.kindergarten_id AS kindergarten_id,
    g.user_id AS user_id
ORDER BY g.guardian_id;

설명:
- 사람이 읽기 좋은 목록 확인
- 유치원/유저 연결용 키도 같이 점검


============================================================
5. 상태별 Guardian 수
============================================================
MATCH (g:Guardian)
RETURN g.status AS status, count(*) AS cnt
ORDER BY cnt DESC;

설명:
- ACTIVE / DISABLED 등 상태 분포 확인


============================================================
6. 성별 분포
============================================================
MATCH (g:Guardian)
RETURN g.gender AS gender, count(*) AS cnt
ORDER BY cnt DESC;

설명:
- MALE / FEMALE 분포 확인


============================================================
7. 유치원별 Guardian 수
============================================================
MATCH (g:Guardian)
RETURN g.kindergarten_id AS kindergarten_id, count(*) AS cnt
ORDER BY cnt DESC, kindergarten_id;

설명:
- 어떤 유치원에 보호자가 몇 명 있는지 기본 점검


============================================================
8. user_id 없는 Guardian 찾기
============================================================
MATCH (g:Guardian)
WHERE g.user_id IS NULL
RETURN
    g.guardian_id AS guardian_id,
    g.name AS name;

설명:
- Guardian ↔ User 연결 누락 가능성 점검


============================================================
9. 주민번호 앞 6자리(rrn_first6) 중복 검사
============================================================
MATCH (g:Guardian)
WHERE g.rrn_first6 IS NOT NULL
WITH g.rrn_first6 AS rrn_first6, count(*) AS cnt, collect(g.guardian_id) AS ids
WHERE cnt > 1
RETURN rrn_first6, cnt, ids;

설명:
- 테스트 데이터 중복 여부 확인
- 실제 운영에서는 민감정보 취급 주의


============================================================
10. 주소 없는 Guardian 찾기
============================================================
MATCH (g:Guardian)
WHERE g.address IS NULL
RETURN
    g.guardian_id AS guardian_id,
    g.name AS name,
    g.user_id AS user_id;

설명:
- 주소 누락 데이터 확인


============================================================
11. 최근 수정된 Guardian 조회
============================================================
MATCH (g:Guardian)
RETURN
    g.guardian_id AS guardian_id,
    g.name AS name,
    g.updated_at AS updated_at
ORDER BY g.updated_at DESC
LIMIT 10;

설명:
- 최근 변경 데이터 확인
- 적재 후 updated_at 정상 여부 점검


============================================================
12. Kindergarten와 조인처럼 확인
============================================================
MATCH (g:Guardian)
MATCH (k:Kindergarten {kindergarten_id: g.kindergarten_id})
RETURN
    g.guardian_id AS guardian_id,
    g.name AS guardian_name,
    k.kindergarten_id AS kindergarten_id,
    k.name AS kindergarten_name
ORDER BY g.guardian_id;

설명:
- 아직 관계를 안 만들었어도
  속성값(kindergarten_id)으로 매칭해서 검증 가능


============================================================
13. User와 조인처럼 확인
============================================================
MATCH (g:Guardian)
MATCH (u:User {user_id: g.user_id})
RETURN
    g.guardian_id AS guardian_id,
    g.name AS guardian_name,
    u.user_id AS user_id,
    u.login_id AS login_id,
    u.email AS email
ORDER BY g.guardian_id;

설명:
- Guardian ↔ User 연결 전 사전 검증용


============================================================
14. 특정 이름 검색
============================================================
MATCH (g:Guardian)
WHERE g.name CONTAINS '홍'
RETURN
    g.guardian_id AS guardian_id,
    g.name AS name,
    g.user_id AS user_id,
    g.kindergarten_id AS kindergarten_id;

설명:
- 이름 검색 예시
- 한글 검색 확인


============================================================
15. Guardian 노드 전체 삭제 테스트용
============================================================
MATCH (g:Guardian)
DETACH DELETE g;

설명:
- 테스트 환경에서만 사용
- 관계가 생긴 뒤에도 같이 지우려면 DETACH DELETE 사용
"""