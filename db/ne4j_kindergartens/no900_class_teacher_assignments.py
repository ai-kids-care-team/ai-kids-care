from neo4j_connect import driver
import csv
from datetime import datetime, date


# ============================================================
# class_teacher_assignments 적재 스크립트
# ------------------------------------------------------------
# 목적:
#   900_class_teacher_assignments.csv 파일을 읽어서
#   Teacher -[:HAS_CLASS]-> Class 관계를 생성
#
# 실행 예:
#   python load_900_class_teacher_assignments.py
#
# CSV 예:
#   assignment_id,kindergarten_id,class_id,teacher_id,role,start_date,
#   end_date,reason,note,status,created_by_user_id,created_at,updated_at
# ============================================================

CSV_FILE_PATH = "./data/900_class_teacher_assignments.csv"


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
# 기존 HAS_CLASS 관계 중 Teacher -> Class 만 삭제
# ------------------------------------------------------------
# 최종 트리 구조에서 Teacher -> Class 관계를 다시 만들기 위함
# Kindergarten -> Class 같은 다른 HAS_CLASS 관계는 삭제하지 않음
# ============================================================
def delete_teacher_class_relationships(tx):
    tx.run("""
        MATCH (t:Teacher)-[r:HAS_CLASS]->(c:Class)
        DELETE r
    """)


# ============================================================
# Teacher -> Class 관계 생성
# ------------------------------------------------------------
# 기준:
#   teacher_id + class_id
#
# 관계 속성:
#   assignment_id, role, start_date, status 등
# ============================================================
def create_teacher_class_relationship(tx, row):
    assignment_id = int(row["assignment_id"])
    kindergarten_id = int(row["kindergarten_id"]) if clean_value(row.get("kindergarten_id")) else None
    class_id = int(row["class_id"])
    teacher_id = int(row["teacher_id"])

    tx.run("""
        MATCH (t:Teacher {teacher_id: $teacher_id})
        MATCH (c:Class {class_id: $class_id})

        WHERE ($kindergarten_id IS NULL OR t.kindergarten_id = $kindergarten_id)
          AND ($kindergarten_id IS NULL OR c.kindergarten_id = $kindergarten_id)

        MERGE (t)-[r:HAS_CLASS]->(c)
        SET r.assignment_id = $assignment_id,
            r.kindergarten_id = $kindergarten_id,
            r.role = $role,
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
    class_id=class_id,
    teacher_id=teacher_id,
    role=clean_value(row.get("role")),
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
        # 0. 기존 Teacher -> Class 관계 삭제
        # --------------------------------------------------------
        session.execute_write(delete_teacher_class_relationships)
        print("🧹 기존 Teacher -> Class 관계 삭제 완료")

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

                    if not row.get("teacher_id"):
                        print("⚠️ SKIP: teacher_id 없음", row)
                        continue

                    if not row.get("class_id"):
                        print("⚠️ SKIP: class_id 없음", row)
                        continue

                    session.execute_write(create_teacher_class_relationship, row)
                    count += 1

                    print(
                        f"✅ Teacher -> Class 적재 완료: "
                        f"assignment_id={row['assignment_id']}, "
                        f"teacher_id={row['teacher_id']}, "
                        f"class_id={row['class_id']}"
                    )

                except Exception as e:
                    print(
                        f"❌ Teacher -> Class 적재 실패: "
                        f"assignment_id={row.get('assignment_id')}, error={e}"
                    )

    print(f"🎉 900_class_teacher_assignments.csv 적재 완료: {count}건")


# ============================================================
# 의미 있는 조회용 Cypher
# ============================================================

"""
============================================================
1. 전체 Teacher -> Class 관계 조회
============================================================
MATCH (t:Teacher)-[r:HAS_CLASS]->(c:Class)
RETURN t, r, c
LIMIT 25;

설명:
- 적재가 실제로 되었는지 빠르게 확인


============================================================
2. 특정 선생님의 반 조회
============================================================
MATCH (t:Teacher {teacher_id: 1})-[r:HAS_CLASS]->(c:Class)
RETURN t.teacher_id, t.name, c.class_id, c.name, r.role, r.status;

설명:
- 한 선생님이 어떤 반을 맡는지 확인


============================================================
3. 특정 반의 선생님 조회
============================================================
MATCH (t:Teacher)-[r:HAS_CLASS]->(c:Class {class_id: 1})
RETURN c.class_id, c.name, t.teacher_id, t.name, r.role, r.status;

설명:
- 한 반에 누가 연결됐는지 확인


============================================================
4. 반별 선생님 수 확인
============================================================
MATCH (t:Teacher)-[:HAS_CLASS]->(c:Class)
RETURN c.class_id, c.name, count(t) AS teacher_count
ORDER BY teacher_count DESC, c.class_id;

설명:
- 한 반에 너무 많은 선생님이 붙었는지 점검


============================================================
5. 선생님별 담당 반 수 확인
============================================================
MATCH (t:Teacher)-[:HAS_CLASS]->(c:Class)
RETURN t.teacher_id, t.name, count(c) AS class_count
ORDER BY class_count DESC, t.teacher_id;

설명:
- 한 선생님이 여러 반을 맡는지 확인


============================================================
6. 상태별 assignment 분포
============================================================
MATCH (:Teacher)-[r:HAS_CLASS]->(:Class)
RETURN r.status AS status, count(*) AS cnt
ORDER BY cnt DESC;

설명:
- ACTIVE / DISABLED 분포 확인


============================================================
7. HOMEROOM만 조회
============================================================
MATCH (t:Teacher)-[r:HAS_CLASS]->(c:Class)
WHERE r.role = 'HOMEROOM'
RETURN t.teacher_id, t.name, c.class_id, c.name, r.start_date, r.end_date;

설명:
- 담임반만 보고 싶을 때 사용


============================================================
8. 관계만 삭제 테스트용
============================================================
MATCH (t:Teacher)-[r:HAS_CLASS]->(c:Class)
DELETE r;

설명:
- Teacher, Class 노드는 유지
- 관계만 다시 적재하고 싶을 때 사용
"""