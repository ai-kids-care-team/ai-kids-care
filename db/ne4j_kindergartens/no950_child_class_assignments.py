from neo4j_connect import driver
import csv
from datetime import datetime, date


# ============================================================
# child_class_assignments 적재 스크립트
# ------------------------------------------------------------
# 목적:
#   950_child_class_assignments.csv 파일을 읽어서
#   Class -[:HAS_CHILD]-> Child 관계를 생성
#
# 실행 예:
#   python load_950_child_class_assignments.py
#
# CSV 예:
#   assignment_id,kindergarten_id,child_id,class_id,start_date,end_date,
#   reason,note,status,created_by_user_id,created_at,updated_at
# ============================================================

CSV_FILE_PATH = "./data/950_child_class_assignments.csv"


# ============================================================
# 공통 유틸
# ============================================================
def clean_value(value):
    if value is None:
        return None

    value = str(value).strip()
    if value == "":
        return None

    return value


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


def fix_date(dt):
    if not dt or dt == "":
        return None

    value = str(dt).strip()

    try:
        return date.fromisoformat(value).isoformat()
    except Exception:
        return value


# ============================================================
# 기존 Class -> Child 관계 삭제
# ------------------------------------------------------------
# 최종 트리 구조에서 Class -> Child 관계를 다시 만들기 위함
# ============================================================
def delete_class_child_relationships(tx):
    tx.run("""
        MATCH (c:Class)-[r:HAS_CHILD]->(ch:Child)
        DELETE r
    """)


# ============================================================
# Class -> Child 관계 생성
# ------------------------------------------------------------
# 기준:
#   class_id + child_id
#
# 관계 속성:
#   assignment_id, start_date, status 등
# ============================================================
def create_class_child_relationship(tx, row):
    assignment_id = int(row["assignment_id"])
    kindergarten_id = int(row["kindergarten_id"]) if clean_value(row.get("kindergarten_id")) else None
    child_id = int(row["child_id"])
    class_id = int(row["class_id"])

    tx.run("""
        MATCH (c:Class {class_id: $class_id})
        MATCH (ch:Child {child_id: $child_id})

        WHERE ($kindergarten_id IS NULL OR c.kindergarten_id = $kindergarten_id)
          AND ($kindergarten_id IS NULL OR ch.kindergarten_id = $kindergarten_id)

        MERGE (c)-[r:HAS_CHILD]->(ch)
        SET r.assignment_id = $assignment_id,
            r.kindergarten_id = $kindergarten_id,
            r.start_date = $start_date,
            r.end_date = $end_date,
            r.reason = $reason,
            r.note = $note,
            r.status = $status,
            r.created_by_user_id = $created_by_user_id,
            r.created_at = $created_at,
            r.updated_at = $updated_at
    """,
    assignment_id=assignment_id,
    kindergarten_id=kindergarten_id,
    child_id=child_id,
    class_id=class_id,
    start_date=fix_date(row.get("start_date")),
    end_date=fix_date(row.get("end_date")),
    reason=clean_value(row.get("reason")),
    note=clean_value(row.get("note")),
    status=clean_value(row.get("status")),
    created_by_user_id=int(row["created_by_user_id"]) if clean_value(row.get("created_by_user_id")) else None,
    created_at=fix_datetime(row.get("created_at")),
    updated_at=fix_datetime(row.get("updated_at"))
    )


# ============================================================
# 메인 실행
# ============================================================
if __name__ == "__main__":
    with driver.session() as session:
        # --------------------------------------------------------
        # 0. 기존 Class -> Child 관계 삭제
        # --------------------------------------------------------
        session.execute_write(delete_class_child_relationships)
        print("🧹 기존 Class -> Child 관계 삭제 완료")

        # --------------------------------------------------------
        # 1. CSV 파일 읽기
        # --------------------------------------------------------
        with open(CSV_FILE_PATH, "r", encoding="utf-8-sig") as file:
            reader = csv.DictReader(file)

            count = 0
            for row in reader:
                try:
                    if not row.get("assignment_id"):
                        print("⚠️ SKIP: assignment_id 없음", row)
                        continue

                    if not row.get("class_id"):
                        print("⚠️ SKIP: class_id 없음", row)
                        continue

                    if not row.get("child_id"):
                        print("⚠️ SKIP: child_id 없음", row)
                        continue

                    session.execute_write(create_class_child_relationship, row)
                    count += 1

                    print(
                        f"✅ Class -> Child 적재 완료: "
                        f"assignment_id={row['assignment_id']}, "
                        f"class_id={row['class_id']}, "
                        f"child_id={row['child_id']}"
                    )

                except Exception as e:
                    print(
                        f"❌ Class -> Child 적재 실패: "
                        f"assignment_id={row.get('assignment_id')}, error={e}"
                    )

    print(f"🎉 950_child_class_assignments.csv 적재 완료: {count}건")


# ============================================================
# 의미 있는 조회용 Cypher
# ============================================================

"""
============================================================
1. 전체 Class -> Child 관계 조회
============================================================
MATCH (c:Class)-[r:HAS_CHILD]->(ch:Child)
RETURN c, r, ch
LIMIT 25;

설명:
- 적재가 실제로 되었는지 빠르게 확인


============================================================
2. 특정 반의 아동 조회
============================================================
MATCH (c:Class {class_id: 1})-[r:HAS_CHILD]->(ch:Child)
RETURN c.class_id, c.name, ch.child_id, ch.name, r.status, r.start_date, r.end_date;

설명:
- 한 반에 어떤 아동이 연결됐는지 확인


============================================================
3. 특정 아동의 반 조회
============================================================
MATCH (c:Class)-[r:HAS_CHILD]->(ch:Child {child_id: 1})
RETURN ch.child_id, ch.name, c.class_id, c.name, r.status, r.start_date, r.end_date;

설명:
- 한 아동이 어느 반에 배정됐는지 확인


============================================================
4. 반별 아동 수 확인
============================================================
MATCH (c:Class)-[:HAS_CHILD]->(ch:Child)
RETURN c.class_id, c.name, count(ch) AS child_count
ORDER BY child_count DESC, c.class_id;

설명:
- 반마다 몇 명이 연결됐는지 확인


============================================================
5. 아동별 반 수 확인
============================================================
MATCH (c:Class)-[:HAS_CHILD]->(ch:Child)
RETURN ch.child_id, ch.name, count(c) AS class_count
ORDER BY class_count DESC, ch.child_id;

설명:
- 한 아동이 여러 반에 중복 연결됐는지 점검


============================================================
6. 상태별 assignment 분포
============================================================
MATCH (:Class)-[r:HAS_CHILD]->(:Child)
RETURN r.status AS status, count(*) AS cnt
ORDER BY cnt DESC;

설명:
- ACTIVE / PENDING / DISABLED 분포 확인


============================================================
7. 관계만 삭제 테스트용
============================================================
MATCH (c:Class)-[r:HAS_CHILD]->(ch:Child)
DELETE r;

설명:
- Class, Child 노드는 유지
- 관계만 다시 적재하고 싶을 때 사용
"""