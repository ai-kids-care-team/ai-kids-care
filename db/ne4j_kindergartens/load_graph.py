"""
PG → Neo4j 一次性 ETL（one-shot derived-graph loader）。

PostgreSQL 是 system-of-record；Neo4j 是只读派生关系图。本脚本连接 PG、清空旧图、按
非 PII 列白名单重建节点与关系，使图严格反映**运行时刻**的 PG 状态。退役了原先按实体拆分、
从 ./data/*.csv 静态快照建图的一堆脚本。

INC-003（data-platform spec）：S0/PII 字段绝不投影进图节点。主防线是每个实体只 SELECT
白名单内的非 PII 列——PII 列根本不进 SQL 结果、不进 Python 行、无从绑定到 Cypher。
no000_scrub_sensitive.py（run_all.sh 中先跑）与 LoaderPiiProjectionGuardTest 为防御层。

图模型（保持与既有 CSV-loader 逐字段一致）：
  节点:  User, Kindergarten, Teacher, Class, Child, Guardian, Role
  关系:  (User)-[:HAS_ROLE]->(Role)
         (Kindergarten)-[:HAS_TEACHER]->(Teacher)
         (Teacher)-[:HAS_CLASS]->(Class)
         (Class)-[:HAS_CHILD]->(Child)
         (Child)-[:HAS_GUARDIAN]->(Guardian)
"""

import sys
from typing import Any, Dict, List

import psycopg2
import psycopg2.extras
from neo4j import GraphDatabase
from neo4j.exceptions import Neo4jError

from config import (
    PG_HOST,
    PG_PORT,
    PG_NAME,
    PG_USER,
    PG_PASSWORD,
    NEO4J_URI,
    NEO4J_USERNAME,
    NEO4J_PASSWORD,
    BATCH_SIZE,
)


# ============================================================
# 节点非 PII 列白名单（INC-003 主防线）
# ------------------------------------------------------------
# 每个标签只 SELECT 这些列。被刻意排除的 PII 列（不在此即不投影）：
#   users:        email, phone, password_hash
#   kindergartens:address, contact_phone, contact_email
#   teachers:     emergency_contact_name/phone, rrn_hash, rrn_first6
#   children:     rrn_first6, rrn_hash, birth_date, address
#   guardians:    rrn_hash, rrn_first6, address
# ============================================================
USER_COLUMNS = ["user_id", "login_id", "status", "last_login_at", "created_at", "updated_at"]
KINDERGARTEN_COLUMNS = [
    "kindergarten_id", "name", "region_code", "code", "business_registration_no",
    "contact_name", "status", "created_at", "updated_at",
]
TEACHER_COLUMNS = [
    "teacher_id", "kindergarten_id", "user_id", "staff_no", "name", "gender",
    "level", "start_date", "end_date", "status", "created_at", "updated_at",
]
CLASS_COLUMNS = [
    "class_id", "kindergarten_id", "name", "grade", "academic_year",
    "start_date", "end_date", "status", "created_at", "updated_at",
]
CHILD_COLUMNS = [
    "child_id", "kindergarten_id", "name", "child_no", "gender",
    "enroll_date", "leave_date", "status", "created_at", "updated_at",
]
GUARDIAN_COLUMNS = [
    "guardian_id", "kindergarten_id", "user_id", "name", "gender",
    "status", "created_at", "updated_at",
]
# 关系来源表（无 PII）
USER_ROLE_COLUMNS = [
    "role_assignment_id", "user_id", "role", "scope_type", "scope_id",
    "status", "granted_at", "granted_by_user_id", "revoked_at", "revoked_by_user_id",
]
CLASS_TEACHER_COLUMNS = [
    "assignment_id", "kindergarten_id", "class_id", "teacher_id", "role",
    "start_date", "end_date", "reason", "note", "status",
    "created_by_user_id", "created_at", "updated_at",
]
CHILD_CLASS_COLUMNS = [
    "assignment_id", "kindergarten_id", "child_id", "class_id",
    "start_date", "end_date", "reason", "note", "status",
    "created_by_user_id", "created_at", "updated_at",
]
CHILD_GUARDIAN_COLUMNS = [
    "kindergarten_id", "child_id", "guardian_id", "relationship", "is_primary",
    "priority", "start_date", "end_date", "created_at", "updated_at",
]


# ============================================================
# 공통 유틸
# ============================================================
def normalize(row: Dict[str, Any]) -> Dict[str, Any]:
    """date/datetime/time → isoformat 문자열; 그 외는 그대로(enum 은 psycopg2 가 str 로 반환)."""
    out: Dict[str, Any] = {}
    for key, value in row.items():
        out[key] = value.isoformat() if hasattr(value, "isoformat") else value
    return out


def get_pg_connection():
    return psycopg2.connect(
        host=PG_HOST, port=PG_PORT, dbname=PG_NAME, user=PG_USER, password=PG_PASSWORD,
    )


def fetch(conn, table: str, columns: List[str]) -> List[Dict[str, Any]]:
    """table 에서 columns(비-PII 화이트리스트)만 SELECT 하여 정규화된 dict 리스트 반환."""
    query = "SELECT {cols} FROM {table}".format(cols=", ".join(columns), table=table)
    with conn.cursor(cursor_factory=psycopg2.extras.RealDictCursor) as cursor:
        cursor.execute(query)
        return [normalize(dict(r)) for r in cursor.fetchall()]


def run_batched(session, cypher: str, rows: List[Dict[str, Any]], label: str) -> None:
    """rows 를 BATCH_SIZE 단위로 UNWIND 하여 cypher(파라미터 $rows) 실행."""
    total = len(rows)
    for start in range(0, total, BATCH_SIZE):
        batch = rows[start:start + BATCH_SIZE]
        session.execute_write(lambda tx: tx.run(cypher, rows=batch))
    print(f"[{label}] {total} rows loaded")


# ============================================================
# Neo4j 스키마 / 초기화
# ============================================================
CONSTRAINTS = [
    "CREATE CONSTRAINT user_user_id_unique IF NOT EXISTS FOR (u:User) REQUIRE u.user_id IS UNIQUE",
    "CREATE CONSTRAINT kindergarten_id_unique IF NOT EXISTS FOR (k:Kindergarten) REQUIRE k.kindergarten_id IS UNIQUE",
    "CREATE CONSTRAINT teacher_id_unique IF NOT EXISTS FOR (t:Teacher) REQUIRE t.teacher_id IS UNIQUE",
    "CREATE CONSTRAINT class_id_unique IF NOT EXISTS FOR (c:Class) REQUIRE c.class_id IS UNIQUE",
    "CREATE CONSTRAINT child_id_unique IF NOT EXISTS FOR (c:Child) REQUIRE c.child_id IS UNIQUE",
    "CREATE CONSTRAINT guardian_id_unique IF NOT EXISTS FOR (g:Guardian) REQUIRE g.guardian_id IS UNIQUE",
    "CREATE CONSTRAINT role_role_key_unique IF NOT EXISTS FOR (r:Role) REQUIRE r.role_key IS UNIQUE",
]


def clear_graph(session) -> None:
    """전체 그래프 삭제. Neo4j 는 권위 데이터가 없는 파생 복제본이므로 안전하며,
    PG 에서 삭제된 엔티티가 고아 노드로 남지 않도록 매 실행 시 클린 슬레이트로 재구축한다."""
    session.execute_write(lambda tx: tx.run("MATCH (n) DETACH DELETE n"))
    print("[clear] existing graph removed")


def ensure_constraints(session) -> None:
    for cypher in CONSTRAINTS:
        session.execute_write(lambda tx, c=cypher: tx.run(c))
    print(f"[constraints] {len(CONSTRAINTS)} ensured")


# ============================================================
# 노드 적재 (UNWIND 배치 MERGE; SET 절은 화이트리스트의 명시적 복제)
# ============================================================
NODE_LOADERS = [
    ("users", USER_COLUMNS, "User", """
        UNWIND $rows AS row
        MERGE (u:User {user_id: row.user_id})
        SET u.login_id = row.login_id,
            u.status = row.status,
            u.last_login_at = row.last_login_at,
            u.created_at = row.created_at,
            u.updated_at = row.updated_at
    """),
    ("kindergartens", KINDERGARTEN_COLUMNS, "Kindergarten", """
        UNWIND $rows AS row
        MERGE (k:Kindergarten {kindergarten_id: row.kindergarten_id})
        SET k.name = row.name,
            k.region_code = row.region_code,
            k.code = row.code,
            k.business_registration_no = row.business_registration_no,
            k.contact_name = row.contact_name,
            k.status = row.status,
            k.created_at = row.created_at,
            k.updated_at = row.updated_at
    """),
    ("teachers", TEACHER_COLUMNS, "Teacher", """
        UNWIND $rows AS row
        MERGE (t:Teacher {teacher_id: row.teacher_id})
        SET t.kindergarten_id = row.kindergarten_id,
            t.user_id = row.user_id,
            t.staff_no = row.staff_no,
            t.name = row.name,
            t.gender = row.gender,
            t.level = row.level,
            t.start_date = row.start_date,
            t.end_date = row.end_date,
            t.status = row.status,
            t.created_at = row.created_at,
            t.updated_at = row.updated_at
    """),
    ("classes", CLASS_COLUMNS, "Class", """
        UNWIND $rows AS row
        MERGE (c:Class {class_id: row.class_id})
        SET c.kindergarten_id = row.kindergarten_id,
            c.name = row.name,
            c.grade = row.grade,
            c.academic_year = row.academic_year,
            c.start_date = row.start_date,
            c.end_date = row.end_date,
            c.status = row.status,
            c.created_at = row.created_at,
            c.updated_at = row.updated_at
    """),
    ("children", CHILD_COLUMNS, "Child", """
        UNWIND $rows AS row
        MERGE (c:Child {child_id: row.child_id})
        SET c.kindergarten_id = row.kindergarten_id,
            c.name = row.name,
            c.child_no = row.child_no,
            c.gender = row.gender,
            c.enroll_date = row.enroll_date,
            c.leave_date = row.leave_date,
            c.status = row.status,
            c.created_at = row.created_at,
            c.updated_at = row.updated_at
    """),
    ("guardians", GUARDIAN_COLUMNS, "Guardian", """
        UNWIND $rows AS row
        MERGE (g:Guardian {guardian_id: row.guardian_id})
        SET g.kindergarten_id = row.kindergarten_id,
            g.user_id = row.user_id,
            g.name = row.name,
            g.gender = row.gender,
            g.status = row.status,
            g.created_at = row.created_at,
            g.updated_at = row.updated_at
    """),
]


# ============================================================
# 관계 적재
# ============================================================
HAS_ROLE_CYPHER = """
    UNWIND $rows AS row
    MATCH (u:User {user_id: row.user_id})
    MERGE (r:Role {role_key: row.role_key})
    SET r.role = row.role,
        r.scope_type = row.scope_type,
        r.scope_id = row.scope_id
    MERGE (u)-[hr:HAS_ROLE]->(r)
    SET hr.role_assignment_id = row.role_assignment_id,
        hr.status = row.status,
        hr.granted_at = row.granted_at,
        hr.granted_by_user_id = row.granted_by_user_id,
        hr.revoked_at = row.revoked_at,
        hr.revoked_by_user_id = row.revoked_by_user_id
"""

# Kindergarten -> Teacher : Teacher.kindergarten_id 기준 (테이블 없이 노드 속성으로 도출)
HAS_TEACHER_CYPHER = """
    MATCH (k:Kindergarten)
    MATCH (t:Teacher)
    WHERE k.kindergarten_id = t.kindergarten_id
    MERGE (k)-[:HAS_TEACHER]->(t)
"""

HAS_CLASS_CYPHER = """
    UNWIND $rows AS row
    MATCH (t:Teacher {teacher_id: row.teacher_id})
    MATCH (c:Class {class_id: row.class_id})
    WHERE (row.kindergarten_id IS NULL OR t.kindergarten_id = row.kindergarten_id)
      AND (row.kindergarten_id IS NULL OR c.kindergarten_id = row.kindergarten_id)
    MERGE (t)-[rel:HAS_CLASS]->(c)
    SET rel.assignment_id = row.assignment_id,
        rel.kindergarten_id = row.kindergarten_id,
        rel.role = row.role,
        rel.start_date = row.start_date,
        rel.end_date = row.end_date,
        rel.reason = row.reason,
        rel.note = row.note,
        rel.status = row.status,
        rel.created_by_user_id = row.created_by_user_id,
        rel.created_at = row.created_at,
        rel.updated_at = row.updated_at
"""

HAS_CHILD_CYPHER = """
    UNWIND $rows AS row
    MATCH (c:Class {class_id: row.class_id})
    MATCH (ch:Child {child_id: row.child_id})
    WHERE (row.kindergarten_id IS NULL OR c.kindergarten_id = row.kindergarten_id)
      AND (row.kindergarten_id IS NULL OR ch.kindergarten_id = row.kindergarten_id)
    MERGE (c)-[rel:HAS_CHILD]->(ch)
    SET rel.assignment_id = row.assignment_id,
        rel.kindergarten_id = row.kindergarten_id,
        rel.start_date = row.start_date,
        rel.end_date = row.end_date,
        rel.reason = row.reason,
        rel.note = row.note,
        rel.status = row.status,
        rel.created_by_user_id = row.created_by_user_id,
        rel.created_at = row.created_at,
        rel.updated_at = row.updated_at
"""

HAS_GUARDIAN_CYPHER = """
    UNWIND $rows AS row
    MATCH (ch:Child {child_id: row.child_id})
    MATCH (g:Guardian {guardian_id: row.guardian_id})
    WHERE (row.kindergarten_id IS NULL OR ch.kindergarten_id = row.kindergarten_id)
      AND (row.kindergarten_id IS NULL OR g.kindergarten_id = row.kindergarten_id)
    MERGE (ch)-[rel:HAS_GUARDIAN]->(g)
    SET rel.kindergarten_id = row.kindergarten_id,
        rel.relationship = row.relationship,
        rel.is_primary = row.is_primary,
        rel.priority = row.priority,
        rel.start_date = row.start_date,
        rel.end_date = row.end_date,
        rel.created_at = row.created_at,
        rel.updated_at = row.updated_at
"""


def build_role_rows(raw: List[Dict[str, Any]]) -> List[Dict[str, Any]]:
    """user_role_assignments 행에 Role 노드의 합성 유니크 키(role_key)를 부여."""
    rows = []
    for r in raw:
        scope_id = r.get("scope_id")
        role_key = "{role}|{scope_type}|{scope_id}".format(
            role=r.get("role"),
            scope_type=r.get("scope_type"),
            scope_id=scope_id if scope_id is not None else "NULL",
        )
        rows.append({**r, "role_key": role_key})
    return rows


# ============================================================
# 메인
# ============================================================
def main() -> None:
    pg_conn = None
    neo4j_driver = None
    try:
        print("1) PostgreSQL 연결")
        pg_conn = get_pg_connection()

        print("2) PostgreSQL 조회 (비-PII 화이트리스트)")
        nodes = {table: fetch(pg_conn, table, cols) for table, cols, _, _ in NODE_LOADERS}
        user_roles = build_role_rows(fetch(pg_conn, "user_role_assignments", USER_ROLE_COLUMNS))
        class_teachers = fetch(pg_conn, "class_teacher_assignments", CLASS_TEACHER_COLUMNS)
        child_classes = fetch(pg_conn, "child_class_assignments", CHILD_CLASS_COLUMNS)
        child_guardians = fetch(pg_conn, "child_guardian_relationships", CHILD_GUARDIAN_COLUMNS)

        print("3) Neo4j 연결 및 그래프 재구축")
        neo4j_driver = GraphDatabase.driver(NEO4J_URI, auth=(NEO4J_USERNAME, NEO4J_PASSWORD))
        with neo4j_driver.session() as session:
            clear_graph(session)
            ensure_constraints(session)

            for table, _cols, label, cypher in NODE_LOADERS:
                run_batched(session, cypher, nodes[table], label)

            run_batched(session, HAS_ROLE_CYPHER, user_roles, "HAS_ROLE")
            session.execute_write(lambda tx: tx.run(HAS_TEACHER_CYPHER))
            print("[HAS_TEACHER] derived from Teacher.kindergarten_id")
            run_batched(session, HAS_CLASS_CYPHER, class_teachers, "HAS_CLASS")
            run_batched(session, HAS_CHILD_CYPHER, child_classes, "HAS_CHILD")
            run_batched(session, HAS_GUARDIAN_CYPHER, child_guardians, "HAS_GUARDIAN")

        print("4) 완료: PG → Neo4j 동기화 성공")

    except psycopg2.Error as exc:
        print("PostgreSQL 오류:", exc, file=sys.stderr)
        sys.exit(1)
    except Neo4jError as exc:
        print("Neo4j 오류:", exc, file=sys.stderr)
        sys.exit(1)
    except Exception as exc:  # noqa: BLE001 — one-shot loader: any failure must exit non-zero
        print("알 수 없는 오류:", exc, file=sys.stderr)
        sys.exit(1)
    finally:
        if pg_conn is not None:
            pg_conn.close()
        if neo4j_driver is not None:
            neo4j_driver.close()


if __name__ == "__main__":
    main()
