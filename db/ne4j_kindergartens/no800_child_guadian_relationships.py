from neo4j_connect import driver
import csv
from datetime import datetime, date


# ============================================================
# child_guardian_relationships 적재 스크립트
# ------------------------------------------------------------
# 목적:
#   80_child_guardian_relationships.csv 파일을 읽어서
#   Child - HAS_GUARDIAN -> Guardian 관계를 생성
#
# 실행 예:
#   python load_child_guardian_relationships.py
#
# CSV 예:
#   kindergarten_id,child_id,guardian_id,relationship,is_primary,
#   priority,start_date,end_date,created_at,updated_at
# ============================================================


# ============================================================
# 날짜/시간 문자열 정리 함수
# ------------------------------------------------------------
# 예:
#   2025-07-23 16:25:05.000000 +00:00
# → 2025-07-23T16:25:05+00:00
# ============================================================
def fix_datetime(dt):
    if not dt or dt == "":
        return None

    value = str(dt).strip()

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
#   2025-07-24 → 2025-07-24
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
# 문자열 true/false -> bool 변환
# ------------------------------------------------------------
# 예:
#   "true"  -> True
#   "false" -> False
#   ""      -> None
# ============================================================
def fix_bool(value):
    value = clean_value(value)
    if value is None:
        return None

    lower_value = value.lower()
    if lower_value == "true":
        return True
    if lower_value == "false":
        return False

    return None


# ============================================================
# 기존 HAS_GUARDIAN 관계 삭제
# ------------------------------------------------------------
# 잘못 연결된 관계를 정리하고 다시 생성할 때 사용
# Child, Guardian 노드는 유지
# ============================================================
def delete_existing_has_guardian_relationships(tx):
    tx.run("""
        MATCH ()-[r:HAS_GUARDIAN]->()
        DELETE r
    """)


# ============================================================
# Child - Guardian 관계 생성
# ------------------------------------------------------------
# 전제:
#   Child 노드와 Guardian 노드는 이미 적재되어 있어야 함
#
# 기준:
#   child_id + guardian_id
#
# 관계 속성:
#   relationship, is_primary, priority, start_date, end_date 등
# ============================================================
def create_child_guardian_relationship(tx, row):
    print("DEBUG CHILD_GUARDIAN ROW:", row)

    child_id = int(row["child_id"])
    guardian_id = int(row["guardian_id"])
    kindergarten_id = int(row["kindergarten_id"]) if clean_value(row.get("kindergarten_id")) else None

    tx.run("""
        // ======================================================
        // 1. Child, Guardian 노드 조회
        // ------------------------------------------------------
        // kindergarten_id도 함께 검증
        // ======================================================
        MATCH (c:Child {child_id: $child_id})
        MATCH (g:Guardian {guardian_id: $guardian_id})

        WHERE ($kindergarten_id IS NULL OR c.kindergarten_id = $kindergarten_id)
          AND ($kindergarten_id IS NULL OR g.kindergarten_id = $kindergarten_id)

        // ======================================================
        // 2. 관계 생성/업데이트
        // ======================================================
        MERGE (c)-[r:HAS_GUARDIAN]->(g)
        SET r.kindergarten_id = $kindergarten_id,
            r.relationship = $relationship,
            r.is_primary = $is_primary,
            r.priority = $priority,
            r.start_date = $start_date,
            r.end_date = $end_date,
            r.created_at = $created_at,
            r.updated_at = $updated_at
    """,
    child_id=child_id,
    guardian_id=guardian_id,
    kindergarten_id=kindergarten_id,
    relationship=clean_value(row.get("relationship")),
    is_primary=fix_bool(row.get("is_primary")),
    priority=int(row["priority"]) if clean_value(row.get("priority")) else None,
    start_date=fix_date(row.get("start_date")),
    end_date=fix_date(row.get("end_date")),
    created_at=fix_datetime(row.get("created_at")),
    updated_at=fix_datetime(row.get("updated_at"))
    )


# ============================================================
# 메인 실행
# ============================================================
if __name__ == "__main__":
    csv_file_path = "./data/800_child_guardian_relationships.csv"

    with driver.session() as session:
        # --------------------------------------------------------
        # 0. 기존 Guardian 관계 삭제
        # --------------------------------------------------------
        session.execute_write(delete_existing_has_guardian_relationships)
        print("🧹 기존 HAS_GUARDIAN 관계 삭제 완료")

        # --------------------------------------------------------
        # 1. CSV 파일 읽기
        # --------------------------------------------------------
        with open(csv_file_path, "r", encoding="utf-8-sig") as file:
            reader = csv.DictReader(file)

            for row in reader:
                try:
                    if not row.get("child_id"):
                        print("⚠️ SKIP: child_id 없음", row)
                        continue

                    if not row.get("guardian_id"):
                        print("⚠️ SKIP: guardian_id 없음", row)
                        continue

                    session.execute_write(create_child_guardian_relationship, row)

                    print(
                        f"✅ Child-Guardian 관계 적재 완료: "
                        f"child_id={row['child_id']}, "
                        f"guardian_id={row['guardian_id']}, "
                        f"relationship={row.get('relationship')}"
                    )

                except Exception as e:
                    print(
                        f"❌ Child-Guardian 관계 적재 실패: "
                        f"child_id={row.get('child_id')}, "
                        f"guardian_id={row.get('guardian_id')}, "
                        f"error={e}"
                    )

    print("🎉 80_child_guardian_relationships.csv → HAS_GUARDIAN 관계 적재 완료")


# ============================================================
# 의미 있는 조회용 Cypher 쿼리 모음
# ============================================================

"""
============================================================
1. 전체 Child-Guardian 관계 조회
============================================================
MATCH (c:Child)-[r:HAS_GUARDIAN]->(g:Guardian)
RETURN c, r, g
LIMIT 25;

설명:
- 적재가 실제로 되었는지 빠르게 확인


============================================================
2. Child별 보호자 수 확인
============================================================
MATCH (c:Child)-[:HAS_GUARDIAN]->(g:Guardian)
RETURN c.child_id AS child_id, c.name AS child_name, count(g) AS guardian_count
ORDER BY guardian_count DESC, child_id;

설명:
- 아동 1명에 보호자가 몇 명 연결됐는지 확인


============================================================
3. Guardian별 아동 수 확인
============================================================
MATCH (c:Child)-[:HAS_GUARDIAN]->(g:Guardian)
RETURN g.guardian_id AS guardian_id, g.name AS guardian_name, count(c) AS child_count
ORDER BY child_count DESC, guardian_id;

설명:
- 보호자 1명에 몇 명의 아동이 연결됐는지 확인
- 형제자매 구조가 있으면 2명 이상 가능


============================================================
4. 대표 보호자(is_primary=true) 조회
============================================================
MATCH (c:Child)-[r:HAS_GUARDIAN]->(g:Guardian)
WHERE r.is_primary = true
RETURN c.child_id, c.name, g.guardian_id, g.name, r.relationship, r.priority
ORDER BY c.child_id;

설명:
- 각 아동의 대표 보호자 확인


============================================================
5. 보호자 관계 종류별 분포
============================================================
MATCH (:Child)-[r:HAS_GUARDIAN]->(:Guardian)
RETURN r.relationship AS relationship, count(*) AS cnt
ORDER BY cnt DESC;

설명:
- FATHER, MOTHER, GRANDMOTHER 등 분포 확인


============================================================
6. 특정 아동의 보호자 조회
============================================================
MATCH (c:Child {child_id: 1})-[r:HAS_GUARDIAN]->(g:Guardian)
RETURN c.name AS child_name, g.name AS guardian_name, r.relationship, r.is_primary, r.priority;

설명:
- 한 명의 아동에 연결된 보호자 확인


============================================================
7. 특정 보호자의 아동 조회
============================================================
MATCH (c:Child)-[r:HAS_GUARDIAN]->(g:Guardian {guardian_id: 1})
RETURN g.name AS guardian_name, c.child_id, c.name AS child_name, r.relationship, r.is_primary;

설명:
- 한 명의 보호자가 담당하는 아동 확인


============================================================
8. 잘못된 과다 연결 탐지
============================================================
MATCH (c:Child)-[:HAS_GUARDIAN]->(g:Guardian)
WITH g, count(c) AS child_count
WHERE child_count > 10
RETURN g.guardian_id, g.name, child_count;

설명:
- 예전처럼 비정상적으로 많은 아동이 한 보호자에 연결됐는지 확인


============================================================
9. 관계만 삭제 테스트용
============================================================
MATCH ()-[r:HAS_GUARDIAN]->()
DELETE r;

설명:
- Child, Guardian 노드는 유지
- 관계만 다시 적재하고 싶을 때 사용
"""