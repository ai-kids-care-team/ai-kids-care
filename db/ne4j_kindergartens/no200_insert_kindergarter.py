from neo4j_connect import driver
import csv
from datetime import datetime


# ============================================================
# kindergarten 적재 스크립트
# ------------------------------------------------------------
# 목적:
#   kindergartens.csv 파일을 읽어서 Neo4j의 (:Kindergarten) 노드로 적재
#
# 실행 예:
#   python load_kindergartens.py
#
# CSV 예:
#   kindergarten_id,name,address,region_code,code,business_registration_no,
#   contact_name,contact_phone,contact_email,status,created_at,updated_at
# ============================================================


# ============================================================
# 날짜 문자열 정리 함수
# ------------------------------------------------------------
# 예:
#   2026-02-24 18:02:17.000000 +00:00
# → 2026-02-24T18:02:17+00:00
# ============================================================
def fix_datetime(dt):
    if not dt or dt == "":
        return None

    value = str(dt).strip()

    # 잘못 들어간 끝 문자 보정
    if value.endswith("T"):
        value = value[:-1]

    try:
        # 첫 번째 공백만 T로 치환
        value = value.replace(" ", "T", 1)
        return datetime.fromisoformat(value).isoformat()
    except Exception:
        # 파싱 실패 시 원문 유지
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
# Kindergarten 노드 생성/업데이트
# ------------------------------------------------------------
# 기준 키:
#   kindergarten_id
#
# 중복 방지:
#   MERGE 사용
# ============================================================
def create_kindergarten_node(tx, row):
    print("DEBUG KG ROW:", row)

    kindergarten_id = int(row["kindergarten_id"])

    tx.run("""
        // ======================================================
        // 1. Kindergarten 노드 생성 또는 조회
        // ======================================================
        MERGE (k:Kindergarten {kindergarten_id: $kindergarten_id})

        // ======================================================
        // 2. 속성 설정
        // ======================================================
        SET k.name = $name,
            k.address = $address,
            k.region_code = $region_code,
            k.code = $code,
            k.business_registration_no = $business_registration_no,
            k.contact_name = $contact_name,
            k.contact_phone = $contact_phone,
            k.contact_email = $contact_email,
            k.status = $status,
            k.created_at = $created_at,
            k.updated_at = $updated_at
    """,
    kindergarten_id=kindergarten_id,
    name=clean_value(row.get("name")),
    address=clean_value(row.get("address")),
    region_code=clean_value(row.get("region_code")),
    code=clean_value(row.get("code")),
    business_registration_no=clean_value(row.get("business_registration_no")),
    contact_name=clean_value(row.get("contact_name")),
    contact_phone=clean_value(row.get("contact_phone")),
    contact_email=clean_value(row.get("contact_email")),
    status=clean_value(row.get("status")),
    created_at=fix_datetime(row.get("created_at")),
    updated_at=fix_datetime(row.get("updated_at"))
    )


# ============================================================
# 제약조건 생성
# ------------------------------------------------------------
# kindergarten_id 유니크 제약조건
# - 적재 전에 1번 실행되도록 구성
# - 이미 있으면 유지
# ============================================================
def create_constraint(tx):
    tx.run("""
        CREATE CONSTRAINT kindergarten_id_unique IF NOT EXISTS
        FOR (k:Kindergarten)
        REQUIRE k.kindergarten_id IS UNIQUE
    """)


# ============================================================
# 메인 실행
# ============================================================
if __name__ == "__main__":
    csv_file_path = "./data/200_kindergartens.csv"

    with driver.session() as session:
        # --------------------------------------------------------
        # 0. 제약조건 생성
        # --------------------------------------------------------
        session.execute_write(create_constraint)
        print("✅ constraint 생성/확인 완료: Kindergarten.kindergarten_id UNIQUE")

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
                    if not row.get("kindergarten_id"):
                        print("⚠️ SKIP: kindergarten_id 없음", row)
                        continue

                    # ------------------------------------------------
                    # 노드 적재
                    # ------------------------------------------------
                    session.execute_write(create_kindergarten_node, row)

                    print(
                        f"✅ Kindergarten 적재 완료: "
                        f"id={row['kindergarten_id']}, "
                        f"name={row.get('name')}"
                    )

                except Exception as e:
                    print(
                        f"❌ Kindergarten 적재 실패: "
                        f"id={row.get('kindergarten_id')}, error={e}"
                    )

    print("🎉 kindergartens.csv → Kindergarten 노드 적재 완료")


# ============================================================
# 의미 있는 조회용 Cypher 쿼리 모음
# ------------------------------------------------------------
# 아래 쿼리는 Neo4j Browser / cypher-shell 에서 확인용으로 사용
# ============================================================

"""
============================================================
1. 전체 유치원 샘플 조회
============================================================
MATCH (k:Kindergarten)
RETURN k
LIMIT 10;

설명:
- 적재가 실제로 되었는지 빠르게 확인
- 속성명이 원하는 형태로 들어갔는지 점검


============================================================
2. 전체 유치원 수 확인
============================================================
MATCH (k:Kindergarten)
RETURN count(k) AS total_kindergartens;

설명:
- CSV 행 수와 비교
- 누락 여부 확인


============================================================
3. 특정 유치원 단건 조회
============================================================
MATCH (k:Kindergarten {kindergarten_id: 1})
RETURN k;

설명:
- 단일 데이터 검증
- 샘플 row 확인용


============================================================
4. 이름과 주소만 보기 좋게 조회
============================================================
MATCH (k:Kindergarten)
RETURN
    k.kindergarten_id AS kindergarten_id,
    k.name AS name,
    k.address AS address
ORDER BY k.kindergarten_id;

설명:
- 사람이 읽기 좋은 목록 확인
- 중복 이름인데 주소가 다른 경우 확인 가능


============================================================
5. 같은 이름의 유치원 찾기
============================================================
MATCH (k:Kindergarten)
WITH k.name AS name, count(*) AS cnt, collect(k.kindergarten_id) AS ids
WHERE cnt > 1
RETURN name, cnt, ids
ORDER BY cnt DESC, name;

설명:
- 예: '하늘유치원'처럼 이름이 같은 데이터 확인
- 이름이 아니라 kindergarten_id를 기준으로 관리해야 함


============================================================
6. 상태별 유치원 수
============================================================
MATCH (k:Kindergarten)
RETURN k.status AS status, count(*) AS cnt
ORDER BY cnt DESC;

설명:
- ACTIVE / INACTIVE / PENDING 등 상태 분포 확인


============================================================
7. 지역코드별 유치원 수
============================================================
MATCH (k:Kindergarten)
RETURN k.region_code AS region_code, count(*) AS cnt
ORDER BY cnt DESC;

설명:
- 지역 분포 확인
- region_code 누락 데이터도 함께 보일 수 있음


============================================================
8. region_code가 없는 유치원 조회
============================================================
MATCH (k:Kindergarten)
WHERE k.region_code IS NULL
RETURN
    k.kindergarten_id AS kindergarten_id,
    k.name AS name,
    k.address AS address;

설명:
- 샘플 데이터처럼 region_code가 비어 있는 경우 확인
- 데이터 보정 대상 찾기


============================================================
9. code가 없는 유치원 조회
============================================================
MATCH (k:Kindergarten)
WHERE k.code IS NULL
RETURN
    k.kindergarten_id AS kindergarten_id,
    k.name AS name,
    k.address AS address;

설명:
- 내부 코드 누락 데이터 검증


============================================================
10. 사업자등록번호 중복 검사
============================================================
MATCH (k:Kindergarten)
WHERE k.business_registration_no IS NOT NULL
WITH k.business_registration_no AS brn, count(*) AS cnt, collect(k.kindergarten_id) AS ids
WHERE cnt > 1
RETURN brn, cnt, ids;

설명:
- business_registration_no 중복 여부 확인
- 데이터 정합성 점검


============================================================
11. 담당자 연락처 없는 유치원 찾기
============================================================
MATCH (k:Kindergarten)
WHERE k.contact_phone IS NULL OR k.contact_email IS NULL
RETURN
    k.kindergarten_id AS kindergarten_id,
    k.name AS name,
    k.contact_name AS contact_name,
    k.contact_phone AS contact_phone,
    k.contact_email AS contact_email;

설명:
- 운영 연락처 누락 데이터 확인


============================================================
12. 최근 수정된 유치원 순으로 조회
============================================================
MATCH (k:Kindergarten)
RETURN
    k.kindergarten_id AS kindergarten_id,
    k.name AS name,
    k.updated_at AS updated_at
ORDER BY k.updated_at DESC
LIMIT 10;

설명:
- 최근 변경 데이터 확인
- 적재 후 updated_at 정상 여부 점검


============================================================
13. address에 '서울'이 들어간 유치원 조회
============================================================
MATCH (k:Kindergarten)
WHERE k.address CONTAINS '서울'
RETURN
    k.kindergarten_id AS kindergarten_id,
    k.name AS name,
    k.address AS address;

설명:
- 주소 텍스트 기반 간단 검색 예시


============================================================
14. 노드만 삭제 테스트용
============================================================
MATCH (k:Kindergarten)
DETACH DELETE k;

설명:
- Kindergarten 노드 전체 삭제
- 테스트 환경에서만 사용
- 관계가 생긴 뒤에도 같이 지우려면 DETACH DELETE 필요
"""