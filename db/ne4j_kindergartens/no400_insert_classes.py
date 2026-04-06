from neo4j_connect import driver
import csv
from datetime import datetime, date


# ============================================================
# class 적재 스크립트
# ------------------------------------------------------------
# 목적:
#   classes.csv 파일을 읽어서 Neo4j의 (:Class) 노드로 적재
#
# 실행 예:
#   python load_classes.py
#
# CSV 예:
#   class_id,kindergarten_id,name,grade,academic_year,
#   start_date,end_date,status,created_at,updated_at
# ============================================================


# ============================================================
# 날짜/시간 문자열 정리 함수
# ------------------------------------------------------------
# 예:
#   2026-03-25 04:31:32.000000 +00:00
# → 2026-03-25T04:31:32+00:00
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
#   2023-03-01 → 2023-03-01
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
# Class 노드 생성/업데이트
# ------------------------------------------------------------
# 기준 키:
#   class_id
#
# 중복 방지:
#   MERGE 사용
# ============================================================
def create_class_node(tx, row):
    print("DEBUG CLASS ROW:", row)

    class_id = int(row["class_id"])

    tx.run("""
        // ======================================================
        // 1. Class 노드 생성 또는 조회
        // ======================================================
        MERGE (c:Class {class_id: $class_id})

        // ======================================================
        // 2. 속성 설정
        // ------------------------------------------------------
        // kindergarten_id는 나중에 관계 생성에도 활용 가능
        // 예:
        //   (c)-[:BELONGS_TO]->(k:Kindergarten)
        // ======================================================
        SET c.kindergarten_id = $kindergarten_id,
            c.name = $name,
            c.grade = $grade,
            c.academic_year = $academic_year,
            c.start_date = $start_date,
            c.end_date = $end_date,
            c.status = $status,
            c.created_at = $created_at,
            c.updated_at = $updated_at
    """,
    class_id=class_id,
    kindergarten_id=int(row["kindergarten_id"]) if clean_value(row.get("kindergarten_id")) else None,
    name=clean_value(row.get("name")),
    grade=clean_value(row.get("grade")),
    academic_year=int(row["academic_year"]) if clean_value(row.get("academic_year")) else None,
    start_date=fix_date(row.get("start_date")),
    end_date=fix_date(row.get("end_date")),
    status=clean_value(row.get("status")),
    created_at=fix_datetime(row.get("created_at")),
    updated_at=fix_datetime(row.get("updated_at"))
    )


# ============================================================
# 제약조건 생성
# ------------------------------------------------------------
# class_id 유니크 제약조건
# ============================================================
def create_constraint(tx):
    tx.run("""
        CREATE CONSTRAINT class_id_unique IF NOT EXISTS
        FOR (c:Class)
        REQUIRE c.class_id IS UNIQUE
    """)


# ============================================================
# 메인 실행
# ============================================================
if __name__ == "__main__":
    csv_file_path = "./data/400_classes.csv"

    with driver.session() as session:
        # --------------------------------------------------------
        # 0. 제약조건 생성
        # --------------------------------------------------------
        session.execute_write(create_constraint)
        print("✅ constraint 생성/확인 완료: Class.class_id UNIQUE")

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
                    if not row.get("class_id"):
                        print("⚠️ SKIP: class_id 없음", row)
                        continue

                    # ------------------------------------------------
                    # 노드 적재
                    # ------------------------------------------------
                    session.execute_write(create_class_node, row)

                    print(
                        f"✅ Class 적재 완료: "
                        f"id={row['class_id']}, "
                        f"name={row.get('name')}, "
                        f"kindergarten_id={row.get('kindergarten_id')}"
                    )

                except Exception as e:
                    print(
                        f"❌ Class 적재 실패: "
                        f"id={row.get('class_id')}, error={e}"
                    )

    print("🎉 classes.csv → Class 노드 적재 완료")


# ============================================================
# 의미 있는 조회용 Cypher 쿼리 모음
# ------------------------------------------------------------
# 아래 쿼리는 Neo4j Browser / cypher-shell 에서 확인용으로 사용
# ============================================================

"""
============================================================
1. 전체 Class 샘플 조회
============================================================
MATCH (c:Class)
RETURN c
LIMIT 10;

설명:
- 적재가 실제로 되었는지 빠르게 확인
- 속성명이 원하는 형태로 들어갔는지 점검


============================================================
2. 전체 Class 수 확인
============================================================
MATCH (c:Class)
RETURN count(c) AS total_classes;

설명:
- CSV 행 수와 비교
- 누락 여부 확인


============================================================
3. 특정 Class 단건 조회
============================================================
MATCH (c:Class {class_id: 1})
RETURN c;

설명:
- 단일 데이터 검증
- 샘플 row 확인용


============================================================
4. 보기 좋게 목록 조회
============================================================
MATCH (c:Class)
RETURN
    c.class_id AS class_id,
    c.name AS name,
    c.grade AS grade,
    c.academic_year AS academic_year,
    c.status AS status,
    c.kindergarten_id AS kindergarten_id
ORDER BY c.class_id;

설명:
- 사람이 읽기 좋은 목록 확인
- 유치원 연결용 키도 같이 점검


============================================================
5. 상태별 Class 수
============================================================
MATCH (c:Class)
RETURN c.status AS status, count(*) AS cnt
ORDER BY cnt DESC;

설명:
- ACTIVE / DISABLED 분포 확인


============================================================
6. 학년도별 Class 수
============================================================
MATCH (c:Class)
RETURN c.academic_year AS academic_year, count(*) AS cnt
ORDER BY academic_year;

설명:
- 2023 / 2024 / 2025 학년도 분포 확인


============================================================
7. 학년(grade)별 Class 수
============================================================
MATCH (c:Class)
RETURN c.grade AS grade, count(*) AS cnt
ORDER BY cnt DESC, grade;

설명:
- 만3세 / 만4세 / 만5세 분포 확인


============================================================
8. 유치원별 Class 수
============================================================
MATCH (c:Class)
RETURN c.kindergarten_id AS kindergarten_id, count(*) AS cnt
ORDER BY cnt DESC, kindergarten_id;

설명:
- 어떤 유치원에 반이 몇 개 있는지 기본 점검


============================================================
9. 이름 중복 검사
============================================================
MATCH (c:Class)
WHERE c.name IS NOT NULL
WITH c.name AS name, count(*) AS cnt, collect(c.class_id) AS ids
WHERE cnt > 1
RETURN name, cnt, ids
ORDER BY cnt DESC, name;

설명:
- 반 이름 중복 여부 확인
- name을 유니크하게 바꿨다면 결과가 없어야 정상


============================================================
10. 종료일(end_date)이 지난 반 조회
============================================================
MATCH (c:Class)
WHERE c.end_date IS NOT NULL
RETURN
    c.class_id AS class_id,
    c.name AS name,
    c.start_date AS start_date,
    c.end_date AS end_date,
    c.status AS status
ORDER BY c.end_date ASC;

설명:
- 종료된 반 / 비활성 반 점검


============================================================
11. 최근 수정된 Class 조회
============================================================
MATCH (c:Class)
RETURN
    c.class_id AS class_id,
    c.name AS name,
    c.updated_at AS updated_at
ORDER BY c.updated_at DESC
LIMIT 10;

설명:
- 최근 변경 데이터 확인
- 적재 후 updated_at 정상 여부 점검


============================================================
12. Kindergarten와 조인처럼 확인
============================================================
MATCH (c:Class)
MATCH (k:Kindergarten {kindergarten_id: c.kindergarten_id})
RETURN
    c.class_id AS class_id,
    c.name AS class_name,
    k.kindergarten_id AS kindergarten_id,
    k.name AS kindergarten_name
ORDER BY c.class_id;

설명:
- 아직 관계를 안 만들었어도
  속성값(kindergarten_id)으로 매칭해서 검증 가능


============================================================
13. 특정 이름 검색
============================================================
MATCH (c:Class)
WHERE c.name CONTAINS '햇'
RETURN
    c.class_id AS class_id,
    c.name AS name,
    c.kindergarten_id AS kindergarten_id;

설명:
- 이름 검색 예시
- 한글 검색 확인


============================================================
14. Class 노드 전체 삭제 테스트용
============================================================
MATCH (c:Class)
DETACH DELETE c;

설명:
- 테스트 환경에서만 사용
- 관계가 생긴 뒤에도 같이 지우려면 DETACH DELETE 사용
"""