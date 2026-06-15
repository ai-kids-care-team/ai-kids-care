from neo4j_connect import driver
import csv
from datetime import datetime, date


# ============================================================
# 최종 트리 관계 재생성 스크립트
# ------------------------------------------------------------
# 목표 구조:
#   Kindergarten -> Teacher -> Class -> Child -> Guardian
#
# 사용 파일:
#   800_child_guardian_relationships.csv
#   900_class_teacher_assignments.csv
#   950_child_class_assignments.csv
#
# 실행 예:
#   python rebuild_tree_relationships.py
# ============================================================

CHILD_GUARDIAN_CSV = "./data/800_child_guardian_relationships.csv"
CLASS_TEACHER_CSV = "./data/900_class_teacher_assignments.csv"
CHILD_CLASS_CSV = "./data/950_child_class_assignments.csv"


# ============================================================
# 공통 유틸 함수
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

    # 잘못 들어간 끝 문자 제거
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


def fix_bool(value):
    value = clean_value(value)
    if value is None:
        return None

    value = value.lower()
    if value == "true":
        return True
    if value == "false":
        return False

    return None


# ============================================================
# 0. 기존 트리 관계 삭제
# ------------------------------------------------------------
# 노드는 유지하고 표시용 트리 관계만 삭제
# ============================================================
def delete_tree_relationships(tx):
    tx.run("""
        MATCH ()-[r:HAS_TEACHER|HAS_CLASS|HAS_CHILD|HAS_GUARDIAN]->()
        DELETE r
    """)


# ============================================================
# 1. Kindergarten -> Teacher
# ------------------------------------------------------------
# Teacher.kindergarten_id 기준
# ============================================================
def create_kindergarten_teacher_relationships(tx):
    tx.run("""
        MATCH (k:Kindergarten)
        MATCH (t:Teacher)
        WHERE k.kindergarten_id = t.kindergarten_id
        MERGE (k)-[:HAS_TEACHER]->(t)
    """)


# ============================================================
# 2. Teacher -> Class
# ------------------------------------------------------------
# 900_class_teacher_assignments.csv 기준
# ============================================================
def create_teacher_class_relationship(tx, row):
    assignment_id = int(row["assignment_id"])
    kindergarten_id = int(row["kindergarten_id"]) if clean_value(row.get("kindergarten_id")) else None
    teacher_id = int(row["teacher_id"])
    class_id = int(row["class_id"])

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
    teacher_id=teacher_id,
    class_id=class_id,
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
# 3. Class -> Child
# ------------------------------------------------------------
# 950_child_class_assignments.csv 기준
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
# 4. Child -> Guardian
# ------------------------------------------------------------
# 80_child_guardian_relationships.csv 기준
# ============================================================
def create_child_guardian_relationship(tx, row):
    kindergarten_id = int(row["kindergarten_id"]) if clean_value(row.get("kindergarten_id")) else None
    child_id = int(row["child_id"])
    guardian_id = int(row["guardian_id"])

    tx.run("""
        MATCH (ch:Child {child_id: $child_id})
        MATCH (g:Guardian {guardian_id: $guardian_id})
        WHERE ($kindergarten_id IS NULL OR ch.kindergarten_id = $kindergarten_id)
          AND ($kindergarten_id IS NULL OR g.kindergarten_id = $kindergarten_id)

        MERGE (ch)-[r:HAS_GUARDIAN]->(g)
        SET r.kindergarten_id = $kindergarten_id,
            r.relationship = $relationship,
            r.is_primary = $is_primary,
            r.priority = $priority,
            r.start_date = $start_date,
            r.end_date = $end_date,
            r.created_at = $created_at,
            r.updated_at = $updated_at
    """,
    kindergarten_id=kindergarten_id,
    child_id=child_id,
    guardian_id=guardian_id,
    relationship=clean_value(row.get("relationship")),
    is_primary=fix_bool(row.get("is_primary")),
    priority=int(row["priority"]) if clean_value(row.get("priority")) else None,
    start_date=fix_date(row.get("start_date")),
    end_date=fix_date(row.get("end_date")),
    created_at=fix_datetime(row.get("created_at")),
    updated_at=fix_datetime(row.get("updated_at"))
    )


# ============================================================
# CSV 처리 공통 함수
# ============================================================
def process_csv(session, csv_file_path, row_handler, required_columns):
    with open(csv_file_path, "r", encoding="utf-8-sig") as file:
        reader = csv.DictReader(file)

        missing_columns = [col for col in required_columns if col not in reader.fieldnames]
        if missing_columns:
            raise ValueError(f"{csv_file_path} 컬럼 부족: {missing_columns}")

        count = 0
        for row in reader:
            session.execute_write(row_handler, row)
            count += 1

        print(f"✅ 처리 완료 - {csv_file_path}: {count}건")


# ============================================================
# 메인 실행
# ============================================================
if __name__ == "__main__":
    with driver.session() as session:
        # --------------------------------------------------------
        # 0. 기존 관계 삭제
        # --------------------------------------------------------
        session.execute_write(delete_tree_relationships)
        print("🧹 기존 트리 관계 삭제 완료")

        # --------------------------------------------------------
        # 1. Kindergarten -> Teacher
        # --------------------------------------------------------
        session.execute_write(create_kindergarten_teacher_relationships)
        print("✅ Kindergarten -> Teacher 완료")

        # --------------------------------------------------------
        # 2. Teacher -> Class
        # --------------------------------------------------------
        process_csv(
            session=session,
            csv_file_path=CLASS_TEACHER_CSV,
            row_handler=create_teacher_class_relationship,
            required_columns=["assignment_id", "class_id", "teacher_id"]
        )

        # --------------------------------------------------------
        # 3. Class -> Child
        # --------------------------------------------------------
        process_csv(
            session=session,
            csv_file_path=CHILD_CLASS_CSV,
            row_handler=create_class_child_relationship,
            required_columns=["assignment_id", "class_id", "child_id"]
        )

        # --------------------------------------------------------
        # 4. Child -> Guardian
        # --------------------------------------------------------
        process_csv(
            session=session,
            csv_file_path=CHILD_GUARDIAN_CSV,
            row_handler=create_child_guardian_relationship,
            required_columns=["child_id", "guardian_id"]
        )

    print("🎉 최종 트리 관계 재생성 완료")


# ============================================================
# 샘플 조회용 Cypher 쿼리 모음
# ------------------------------------------------------------
# Neo4j Browser에서 복사해서 확인용으로 사용
# ============================================================

"""
============================================================
1. 최종 트리 전체 샘플 조회
============================================================
MATCH p=(k:Kindergarten)-[:HAS_TEACHER]->(t:Teacher)-[:HAS_CLASS]->(c:Class)-[:HAS_CHILD]->(ch:Child)-[:HAS_GUARDIAN]->(g:Guardian)
RETURN p
LIMIT 20;

설명:
- 최종 목표 구조가 실제로 연결되었는지 한 번에 확인


============================================================
2. 유치원 -> 선생님 조회
============================================================
MATCH (k:Kindergarten)-[:HAS_TEACHER]->(t:Teacher)
RETURN
    k.kindergarten_id AS kindergarten_id,
    k.name AS kindergarten_name,
    t.teacher_id AS teacher_id,
    t.name AS teacher_name
ORDER BY kindergarten_id, teacher_id;

설명:
- 유치원별 선생님 연결 확인


============================================================
3. 선생님 -> Class 조회
============================================================
MATCH (t:Teacher)-[r:HAS_CLASS]->(c:Class)
RETURN
    t.teacher_id AS teacher_id,
    t.name AS teacher_name,
    c.class_id AS class_id,
    c.name AS class_name,
    r.role AS role,
    r.status AS status
ORDER BY teacher_id, class_id;

설명:
- 선생님이 어떤 반을 맡는지 확인


============================================================
4. Class -> Child 조회
============================================================
MATCH (c:Class)-[r:HAS_CHILD]->(ch:Child)
RETURN
    c.class_id AS class_id,
    c.name AS class_name,
    ch.child_id AS child_id,
    ch.name AS child_name,
    r.status AS status
ORDER BY class_id, child_id;

설명:
- 반에 어떤 아이가 연결됐는지 확인


============================================================
5. Child -> Guardian 조회
============================================================
MATCH (ch:Child)-[r:HAS_GUARDIAN]->(g:Guardian)
RETURN
    ch.child_id AS child_id,
    ch.name AS child_name,
    g.guardian_id AS guardian_id,
    g.name AS guardian_name,
    r.relationship AS relationship,
    r.is_primary AS is_primary,
    r.priority AS priority
ORDER BY child_id, priority;

설명:
- 아이와 양육자 연결 확인


============================================================
6. 관계 개수 확인
============================================================
MATCH ()-[r]->()
WHERE type(r) IN ['HAS_TEACHER', 'HAS_CLASS', 'HAS_CHILD', 'HAS_GUARDIAN']
RETURN type(r) AS rel_type, count(*) AS cnt
ORDER BY cnt DESC;

설명:
- 어떤 관계가 몇 건 생성됐는지 확인


============================================================
7. 반별 선생님 수 확인
============================================================
MATCH (t:Teacher)-[:HAS_CLASS]->(c:Class)
RETURN
    c.class_id AS class_id,
    c.name AS class_name,
    count(t) AS teacher_count
ORDER BY teacher_count DESC, class_id;

설명:
- 한 반에 너무 많은 선생님이 연결됐는지 확인


============================================================
8. 반별 아이 수 확인
============================================================
MATCH (c:Class)-[:HAS_CHILD]->(ch:Child)
RETURN
    c.class_id AS class_id,
    c.name AS class_name,
    count(ch) AS child_count
ORDER BY child_count DESC, class_id;

설명:
- 반마다 몇 명의 아이가 연결됐는지 확인


============================================================
9. 양육자 과다 연결 점검
============================================================
MATCH (ch:Child)-[:HAS_GUARDIAN]->(g:Guardian)
WITH g, count(ch) AS child_count
WHERE child_count > 10
RETURN
    g.guardian_id AS guardian_id,
    g.name AS guardian_name,
    child_count
ORDER BY child_count DESC;

설명:
- 예전처럼 한 양육자에 너무 많은 아이가 잘못 연결됐는지 점검


============================================================
10. 관계만 다시 삭제하고 싶을 때
============================================================
MATCH ()-[r:HAS_TEACHER|HAS_CLASS|HAS_CHILD|HAS_GUARDIAN]->()
DELETE r;

설명:
- 노드는 유지
- 관계만 다시 재생성할 때 사용
"""