"""
PG → Neo4j 派生图 loader（增量 sync + bootstrap 全量重建）。

PostgreSQL 是 system-of-record；Neo4j 是**只读派生关系图**，loader 是 Neo4j 唯一写入者。
本脚本以**长生命周期循环**运行：启动时若图为空 / 无水位则做一次 `DETACH DELETE` 全量重建
（bootstrap），随后进入稳态——每 tick 以每表 high-water mark 拉取 `updated_at >= :wm` 的增量行
`MERGE` upsert，并周期性做全量 id 对账清孤儿（传播硬删除）。软删除经 `updated_at` 增量被
upsert 捕获、节点 status 更新并保留。

INC-003（data-platform spec）：S0/PII 字段绝不投影进图。主防线是每个实体只 SELECT 白名单内的
非 PII 列——全量重建与增量拉取**共用同一份白名单**，增量 SELECT 仅在 WHERE 上加水位谓词、不多取
任何列。水位 meta-node `(:_GraphSyncState {table, watermark})` 仅存表名 + 时间戳（非 PII）。
no000_scrub_sensitive.py（run_all.sh 中先跑）与 LoaderPiiProjectionGuardTest 为防御层。

图模型（保持与既有 loader 逐字段一致）：
  节点:  User, Kindergarten, Teacher, Class, Child, Guardian, Role
  关系:  (User)-[:HAS_ROLE]->(Role)
         (Kindergarten)-[:HAS_TEACHER]->(Teacher)
         (Teacher)-[:HAS_CLASS]->(Class)
         (Class)-[:HAS_CHILD]->(Child)
         (Child)-[:HAS_GUARDIAN]->(Guardian)
"""

import sys
import time
import traceback
from typing import Any, Callable, Dict, List, Optional

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
    POLL_INTERVAL_SEC,
    RECONCILE_EVERY,
)

# 空表 / 无水位时的哨兵水位：早于任何真实行，首个增量 tick 会拉取全部行（MERGE 幂等）。
EPOCH_WATERMARK = "1970-01-01T00:00:00+00:00"


# ============================================================
# 节点非 PII 列白名单（INC-003 主防线）
# ------------------------------------------------------------
# 每个标签只 SELECT 这些列。被刻意排除的 PII 列（不在此即不投影）：
#   users:        email, phone, password_hash
#   kindergartens:address, contact_name, contact_phone, contact_email
#   teachers:     gender, emergency_contact_name/phone, rrn_hash, rrn_first6
#   children:     gender, rrn_first6, rrn_hash, birth_date, address
#   guardians:    gender, rrn_hash, rrn_first6, address
# 注: gender(성별) 为 PII，SEC-12 决策不投影到关系图（保留真名，删性别）。
# ============================================================
USER_COLUMNS = ["user_id", "login_id", "status", "last_login_at", "created_at", "updated_at"]
KINDERGARTEN_COLUMNS = [
    "kindergarten_id", "name", "region_code", "code", "business_registration_no",
    "status", "created_at", "updated_at",
]
TEACHER_COLUMNS = [
    "teacher_id", "kindergarten_id", "user_id", "staff_no", "name",
    "level", "start_date", "end_date", "status", "created_at", "updated_at",
]
CLASS_COLUMNS = [
    "class_id", "kindergarten_id", "name", "grade", "academic_year",
    "start_date", "end_date", "status", "created_at", "updated_at",
]
CHILD_COLUMNS = [
    "child_id", "kindergarten_id", "name", "child_no",
    "enroll_date", "leave_date", "status", "created_at", "updated_at",
]
GUARDIAN_COLUMNS = [
    "guardian_id", "kindergarten_id", "user_id", "name",
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
    """table 에서 columns(비-PII 화이트리스트)만 SELECT 하여 정규화된 dict 리스트 반환(全量)."""
    query = "SELECT {cols} FROM {table}".format(cols=", ".join(columns), table=table)
    with conn.cursor(cursor_factory=psycopg2.extras.RealDictCursor) as cursor:
        cursor.execute(query)
        return [normalize(dict(r)) for r in cursor.fetchall()]


def fetch_incremental(
    conn, table: str, columns: List[str], watermark_expr: str, watermark: str
) -> List[Dict[str, Any]]:
    """增量拉取：只多在 WHERE 上加水位谓词，SELECT 列集与全量完全一致（INC-003：不多取任何列）。
    边界用 `>=` + 幂等 MERGE，避免同秒多行在重启/重叠重放时丢失。"""
    query = (
        "SELECT {cols} FROM {table} WHERE {wm_expr} >= %(wm)s ORDER BY {wm_expr}".format(
            cols=", ".join(columns), table=table, wm_expr=watermark_expr,
        )
    )
    with conn.cursor(cursor_factory=psycopg2.extras.RealDictCursor) as cursor:
        cursor.execute(query, {"wm": watermark})
        return [normalize(dict(r)) for r in cursor.fetchall()]


def fetch_max_watermark(conn, table: str, watermark_expr: str) -> str:
    """SELECT max(<watermark_expr>)：初始化 / 校准该表水位。空表 → EPOCH 哨兵。"""
    query = "SELECT max({wm_expr}) AS wm FROM {table}".format(wm_expr=watermark_expr, table=table)
    with conn.cursor() as cursor:
        cursor.execute(query)
        value = cursor.fetchone()[0]
    if value is None:
        return EPOCH_WATERMARK
    return value.isoformat() if hasattr(value, "isoformat") else str(value)


def fetch_ids(conn, table: str, id_columns: List[str]) -> List[Any]:
    """对账用：只取 id 列（极轻）。单列返回标量列表，多列返回 tuple 列表。"""
    query = "SELECT {cols} FROM {table}".format(cols=", ".join(id_columns), table=table)
    with conn.cursor() as cursor:
        cursor.execute(query)
        rows = cursor.fetchall()
    if len(id_columns) == 1:
        return [r[0] for r in rows]
    return [tuple(r) for r in rows]


def run_batched(session, cypher: str, rows: List[Dict[str, Any]], label: str) -> None:
    """rows 를 BATCH_SIZE 단위로 UNWIND 하여 cypher(파라미터 $rows) 실행."""
    total = len(rows)
    for start in range(0, total, BATCH_SIZE):
        batch = rows[start:start + BATCH_SIZE]
        session.execute_write(lambda tx: tx.run(cypher, rows=batch))
    print(f"[{label}] {total} rows loaded")


# ============================================================
# 每行有效变更时刻（水位推进用）——不额外 SELECT，从已白名单化的列派生
# ============================================================
def effective_watermark(table: str, row: Dict[str, Any]) -> Optional[str]:
    """从行内已选列派生「变更时刻」（ISO 字符串，同一 TZ Asia/Seoul → 可字典序比较）。
    user_role_assignments 无 updated_at：用 max(granted_at, revoked_at)。"""
    if table == "user_role_assignments":
        candidates = [row.get("granted_at"), row.get("revoked_at")]
        present = [c for c in candidates if c]
        return max(present) if present else None
    return row.get("updated_at")


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
    "CREATE CONSTRAINT graph_sync_state_table_unique IF NOT EXISTS FOR (s:_GraphSyncState) REQUIRE s.table IS UNIQUE",
]


def clear_graph(session) -> None:
    """전체 그래프 삭제(水位 meta-node 포함). Neo4j 는 권위 데이터가 없는 파생 복제본이므로 안전하며,
    bootstrap 시 클린 슬레이트로 재구축한다."""
    session.execute_write(lambda tx: tx.run("MATCH (n) DETACH DELETE n"))
    print("[clear] existing graph removed")


def ensure_constraints(session) -> None:
    for cypher in CONSTRAINTS:
        session.execute_write(lambda tx, c=cypher: tx.run(c))
    print(f"[constraints] {len(CONSTRAINTS)} ensured")


# ============================================================
# 노드 적재 Cypher (UNWIND 배치 MERGE; SET 절은 화이트리스트의 명시적 복제)
# ============================================================
USER_NODE_CYPHER = """
    UNWIND $rows AS row
    MERGE (u:User {user_id: row.user_id})
    SET u.login_id = row.login_id,
        u.status = row.status,
        u.last_login_at = row.last_login_at,
        u.created_at = row.created_at,
        u.updated_at = row.updated_at
"""
KINDERGARTEN_NODE_CYPHER = """
    UNWIND $rows AS row
    MERGE (k:Kindergarten {kindergarten_id: row.kindergarten_id})
    SET k.name = row.name,
        k.region_code = row.region_code,
        k.code = row.code,
        k.business_registration_no = row.business_registration_no,
        k.status = row.status,
        k.created_at = row.created_at,
        k.updated_at = row.updated_at
"""
TEACHER_NODE_CYPHER = """
    UNWIND $rows AS row
    MERGE (t:Teacher {teacher_id: row.teacher_id})
    SET t.kindergarten_id = row.kindergarten_id,
        t.user_id = row.user_id,
        t.staff_no = row.staff_no,
        t.name = row.name,
        t.level = row.level,
        t.start_date = row.start_date,
        t.end_date = row.end_date,
        t.status = row.status,
        t.created_at = row.created_at,
        t.updated_at = row.updated_at
"""
CLASS_NODE_CYPHER = """
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
"""
CHILD_NODE_CYPHER = """
    UNWIND $rows AS row
    MERGE (c:Child {child_id: row.child_id})
    SET c.kindergarten_id = row.kindergarten_id,
        c.name = row.name,
        c.child_no = row.child_no,
        c.enroll_date = row.enroll_date,
        c.leave_date = row.leave_date,
        c.status = row.status,
        c.created_at = row.created_at,
        c.updated_at = row.updated_at
"""
GUARDIAN_NODE_CYPHER = """
    UNWIND $rows AS row
    MERGE (g:Guardian {guardian_id: row.guardian_id})
    SET g.kindergarten_id = row.kindergarten_id,
        g.user_id = row.user_id,
        g.name = row.name,
        g.status = row.status,
        g.created_at = row.created_at,
        g.updated_at = row.updated_at
"""


# ============================================================
# 관계 적재 Cypher
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


def identity(rows: List[Dict[str, Any]]) -> List[Dict[str, Any]]:
    return rows


# ============================================================
# 表 → (白名单列 / 水位 / label / id / MERGE Cypher) 单一注册表
# ------------------------------------------------------------
# 全量重建(bootstrap) 与 增量 tick 共用同一份配置，保证白名单/Cypher 单一真源。
# ============================================================
# 节点表: (table, columns, label, id_key, node_cypher)
NODE_SPECS = [
    ("users", USER_COLUMNS, "User", "user_id", USER_NODE_CYPHER),
    ("kindergartens", KINDERGARTEN_COLUMNS, "Kindergarten", "kindergarten_id", KINDERGARTEN_NODE_CYPHER),
    ("teachers", TEACHER_COLUMNS, "Teacher", "teacher_id", TEACHER_NODE_CYPHER),
    ("classes", CLASS_COLUMNS, "Class", "class_id", CLASS_NODE_CYPHER),
    ("children", CHILD_COLUMNS, "Child", "child_id", CHILD_NODE_CYPHER),
    ("guardians", GUARDIAN_COLUMNS, "Guardian", "guardian_id", GUARDIAN_NODE_CYPHER),
]

# 关系表: (table, columns, watermark_expr, transform, cypher, label)
# watermark_expr 是 SQL 表达式；user_role_assignments 无 updated_at → 用 granted/revoked 派生。
REL_SPECS = [
    ("user_role_assignments", USER_ROLE_COLUMNS,
     "GREATEST(granted_at, COALESCE(revoked_at, granted_at))", build_role_rows,
     HAS_ROLE_CYPHER, "HAS_ROLE"),
    ("class_teacher_assignments", CLASS_TEACHER_COLUMNS,
     "updated_at", identity, HAS_CLASS_CYPHER, "HAS_CLASS"),
    ("child_class_assignments", CHILD_CLASS_COLUMNS,
     "updated_at", identity, HAS_CHILD_CYPHER, "HAS_CHILD"),
    ("child_guardian_relationships", CHILD_GUARDIAN_COLUMNS,
     "updated_at", identity, HAS_GUARDIAN_CYPHER, "HAS_GUARDIAN"),
]

# 节点表水位表达式统一 = updated_at（6 表均有）。
NODE_WATERMARK_EXPR = "updated_at"


# ============================================================
# 节点属性白名单强制（INC-003 纵深防御，每 tick 生效）
# ------------------------------------------------------------
# 允许集直接派生自各 label 的白名单列——loader 的 SET 子句就是这些列的显式复制，故
# 「投影白名单」与「保留白名单」天然单一真源。任何未在允许集内的节点属性——无论是历史
# loader 版本残留、还是未来某次投影漂移误写——都会在下一 tick 被 strip 掉，从根上杜绝
# 旧 no000 黑名单漂移（漏字段 / 防不存在的死字段如 rrn_encrypted）。
# 被 REMOVE 的属性名取自图中 keys(n)（本 schema 自身标识符，非外部输入），backtick
# 引用后拼接安全。Role / _GraphSyncState 为非 PII 记账节点，不在 6 个 PII 承载 label 内。
# ============================================================
NODE_ALLOWED_PROPS: Dict[str, set] = {
    label: set(columns) for _table, columns, label, _id, _cy in NODE_SPECS
}


def strip_disallowed_props(session, label: str, allowed: set) -> List[str]:
    """strip 掉 label 节点上任何不在 allowed 白名单内的属性；返回被清理的属性名列表。
    先用 keys(n) 收集图中实际出现的越权属性名（DISTINCT），再一次 REMOVE 全清（幂等）。"""
    extra = session.execute_read(
        lambda tx: tx.run(
            f"MATCH (n:`{label}`) UNWIND keys(n) AS k WITH DISTINCT k "
            "WHERE NOT k IN $allowed RETURN collect(k) AS extra",
            allowed=list(allowed),
        ).single()["extra"]
    )
    if not extra:
        return []
    removes = ", ".join(f"n.`{k}`" for k in extra)
    session.execute_write(lambda tx: tx.run(f"MATCH (n:`{label}`) REMOVE {removes}"))
    print(f"[scrub][{label}] 越权属性已清除: {', '.join(extra)}", file=sys.stderr)
    return list(extra)


def enforce_node_prop_whitelist(session) -> int:
    """对 6 个 PII 承载 label 逐一 strip 越权属性（白名单强制）。全量重建后 + 每增量 tick 调用。
    单 label 隔离：某 label 失败仅记录，不阻断其他 label 与主循环。返回被清理的属性总数。"""
    stripped = 0
    for label, allowed in NODE_ALLOWED_PROPS.items():
        try:
            stripped += len(strip_disallowed_props(session, label, allowed))
        except Exception as exc:  # noqa: BLE001 — 单 label 隔离，不阻断其他 label / 主循环
            print(f"[scrub][{label}] 白名单强制失败: {exc}", file=sys.stderr)
    return stripped


# ============================================================
# 水位 meta-node 读写 (:_GraphSyncState {table, watermark}) —— 仅非 PII 记账属性
# ============================================================
def read_watermark(session, table: str) -> Optional[str]:
    rec = session.execute_read(
        lambda tx: tx.run(
            "MATCH (s:_GraphSyncState {table: $t}) RETURN s.watermark AS wm", t=table
        ).single()
    )
    return rec["wm"] if rec else None


def write_watermark(session, table: str, watermark: str) -> None:
    session.execute_write(
        lambda tx: tx.run(
            "MERGE (s:_GraphSyncState {table: $t}) SET s.watermark = $wm",
            t=table, wm=watermark,
        )
    )


def graph_needs_bootstrap(session) -> bool:
    """空图（无数据节点）或无任何水位 meta-node → 需要 bootstrap 全量重建。"""
    data_nodes = session.execute_read(
        lambda tx: tx.run(
            "MATCH (n) WHERE NOT n:_GraphSyncState RETURN count(n) AS c"
        ).single()["c"]
    )
    sync_states = session.execute_read(
        lambda tx: tx.run("MATCH (s:_GraphSyncState) RETURN count(s) AS c").single()["c"]
    )
    return data_nodes == 0 or sync_states == 0


# ============================================================
# Bootstrap: one-shot 全量重建 + 初始化各表水位
# ============================================================
def full_rebuild(session, pg_conn) -> None:
    print("[bootstrap] 全量重建开始（DETACH DELETE + 白名单重载）")
    nodes = {table: fetch(pg_conn, table, cols) for table, cols, _, _, _ in NODE_SPECS}
    rel_rows = {
        table: transform(fetch(pg_conn, table, cols))
        for table, cols, _wm, transform, _cy, _lbl in REL_SPECS
    }

    clear_graph(session)
    ensure_constraints(session)

    for table, _cols, label, _id, cypher in NODE_SPECS:
        run_batched(session, cypher, nodes[table], label)

    session.execute_write(lambda tx: tx.run(HAS_TEACHER_CYPHER))
    print("[HAS_TEACHER] derived from Teacher.kindergarten_id")
    for table, _cols, _wm, _tf, cypher, label in REL_SPECS:
        run_batched(session, cypher, rel_rows[table], label)

    # 白名单强制：clean slate 后节点属性理应已合规，此调用是对本 loader 版本自身的兜底
    # （若图非空重建、或 SET 子句意外漂移，也在此被拦下）。
    enforce_node_prop_whitelist(session)

    init_watermarks(session, pg_conn)
    print("[bootstrap] 全量重建完成")


def init_watermarks(session, pg_conn) -> None:
    for table, _cols, _label, _id, _cy in NODE_SPECS:
        wm = fetch_max_watermark(pg_conn, table, NODE_WATERMARK_EXPR)
        write_watermark(session, table, wm)
    for table, _cols, wm_expr, _tf, _cy, _lbl in REL_SPECS:
        wm = fetch_max_watermark(pg_conn, table, wm_expr)
        write_watermark(session, table, wm)
    print("[watermark] 各表水位已初始化")


# ============================================================
# 稳态：增量 tick + 周期对账
# ============================================================
def _sync_one(
    session, pg_conn, table: str, columns: List[str], watermark_expr: str,
    transform: Callable[[List[Dict[str, Any]]], List[Dict[str, Any]]],
    cypher: str, label: str,
) -> int:
    """单表增量 upsert：拉取 >= 水位的行 → MERGE → 推进水位到本批 max。
    失败语义：抛异常则不推进水位（调用方捕获），下 tick 重试（MERGE 幂等）。返回处理行数。"""
    watermark = read_watermark(session, table) or EPOCH_WATERMARK
    raw = fetch_incremental(pg_conn, table, columns, watermark_expr, watermark)
    if not raw:
        return 0
    rows = transform(raw)
    run_batched(session, cypher, rows, f"{label}·incr")
    batch_max = max(
        (wm for wm in (effective_watermark(table, r) for r in raw) if wm),
        default=None,
    )
    if batch_max is not None:
        write_watermark(session, table, batch_max)
    return len(raw)


def incremental_tick(session, pg_conn) -> int:
    """一个增量 tick：各表独立 try/except——某表失败仅记录、不推进该表水位、不影响其他表。"""
    changed = 0
    teachers_changed = False
    for table, columns, _label, _id, cypher in NODE_SPECS:
        try:
            n = _sync_one(session, pg_conn, table, columns, NODE_WATERMARK_EXPR,
                          identity, cypher, table)
            changed += n
            if table == "teachers" and n:
                teachers_changed = True
        except Exception as exc:  # noqa: BLE001 — 单表隔离：记录并继续，不推进该表水位
            print(f"[tick][{table}] 失败（水位不前进，下 tick 重试）: {exc}", file=sys.stderr)

    # Teacher 行变更 → 重建 HAS_TEACHER（由 Teacher.kindergarten_id 导出，MERGE 幂等）
    if teachers_changed:
        try:
            session.execute_write(lambda tx: tx.run(HAS_TEACHER_CYPHER))
            print("[HAS_TEACHER·incr] re-derived")
        except Exception as exc:  # noqa: BLE001
            print(f"[tick][HAS_TEACHER] 失败: {exc}", file=sys.stderr)

    for table, columns, wm_expr, transform, cypher, label in REL_SPECS:
        try:
            changed += _sync_one(session, pg_conn, table, columns, wm_expr,
                                 transform, cypher, label)
        except Exception as exc:  # noqa: BLE001
            print(f"[tick][{table}] 失败（水位不前进，下 tick 重试）: {exc}", file=sys.stderr)

    # 每 tick 兜底：strip 掉任何越权节点属性（历史残留 / 投影漂移）。MERGE+SET 只覆盖不删除，
    # 故此白名单强制是让「移除某投影列」在稳态下真正生效、并每 tick 持续保护的关键防线。
    try:
        enforce_node_prop_whitelist(session)
    except Exception as exc:  # noqa: BLE001 — 兜底不阻断 tick
        print(f"[tick][scrub] 白名单强制失败: {exc}", file=sys.stderr)

    return changed


def reconcile_deletes(session, pg_conn) -> None:
    """全量 id 对账清孤儿——传播 PG 硬删除（updated_at 观测不到已删行）。图极小，可每 tick 跑。"""
    # 1) 节点孤儿：live id 集合外的节点 DETACH DELETE
    for table, _cols, label, id_key, _cy in NODE_SPECS:
        live_ids = fetch_ids(pg_conn, table, [id_key])
        session.execute_write(
            lambda tx, lbl=label, k=id_key, ids=live_ids: tx.run(
                f"MATCH (n:{lbl}) WHERE NOT n.{k} IN $ids DETACH DELETE n", ids=ids
            )
        )

    # 2) HAS_ROLE 边：按 role_assignment_id 对账；随后清理无入边的孤儿 Role 节点
    live_role_ids = fetch_ids(pg_conn, "user_role_assignments", ["role_assignment_id"])
    session.execute_write(
        lambda tx: tx.run(
            "MATCH ()-[r:HAS_ROLE]->() WHERE NOT r.role_assignment_id IN $ids DELETE r",
            ids=live_role_ids,
        )
    )
    session.execute_write(
        lambda tx: tx.run("MATCH (r:Role) WHERE NOT ()-[:HAS_ROLE]->(r) DETACH DELETE r")
    )

    # 3) HAS_CLASS / HAS_CHILD 边：按 assignment_id 对账
    for table, edge in [("class_teacher_assignments", "HAS_CLASS"),
                        ("child_class_assignments", "HAS_CHILD")]:
        live = fetch_ids(pg_conn, table, ["assignment_id"])
        session.execute_write(
            lambda tx, e=edge, ids=live: tx.run(
                f"MATCH ()-[r:{e}]->() WHERE NOT r.assignment_id IN $ids DELETE r", ids=ids
            )
        )

    # 4) HAS_GUARDIAN 边：无单一 id → 按 (child_id, guardian_id) 存在性对账
    live_pairs = fetch_ids(pg_conn, "child_guardian_relationships", ["child_id", "guardian_id"])
    live_pair_keys = [f"{c}-{g}" for c, g in live_pairs]
    session.execute_write(
        lambda tx: tx.run(
            "MATCH (ch:Child)-[r:HAS_GUARDIAN]->(g:Guardian) "
            "WHERE NOT (toString(ch.child_id) + '-' + toString(g.guardian_id)) IN $pairs "
            "DELETE r",
            pairs=live_pair_keys,
        )
    )

    # 5) HAS_TEACHER 边：Teacher 换园后旧边过时 → 清理 kindergarten 不匹配的边
    session.execute_write(
        lambda tx: tx.run(
            "MATCH (k:Kindergarten)-[r:HAS_TEACHER]->(t:Teacher) "
            "WHERE k.kindergarten_id <> t.kindergarten_id DELETE r"
        )
    )
    print("[reconcile] 孤儿节点/边对账完成")


# ============================================================
# 메인 —— 长生命周期循环
# ============================================================
def run_sync_loop() -> None:
    neo4j_driver = GraphDatabase.driver(NEO4J_URI, auth=(NEO4J_USERNAME, NEO4J_PASSWORD))
    tick = 0
    try:
        # Bootstrap 判定（独立短连接）
        boot_conn = get_pg_connection()
        try:
            with neo4j_driver.session() as session:
                ensure_constraints(session)
                if graph_needs_bootstrap(session):
                    full_rebuild(session, boot_conn)
                else:
                    print("[startup] 已有图 + 水位，跳过 bootstrap，进入增量稳态")
        finally:
            boot_conn.close()

        print(f"[loop] 进入稳态：POLL_INTERVAL_SEC={POLL_INTERVAL_SEC} RECONCILE_EVERY={RECONCILE_EVERY}")
        while True:
            tick += 1
            pg_conn = None
            try:
                pg_conn = get_pg_connection()
                with neo4j_driver.session() as session:
                    changed = incremental_tick(session, pg_conn)
                    if RECONCILE_EVERY > 0 and tick % RECONCILE_EVERY == 0:
                        reconcile_deletes(session, pg_conn)
                    if changed:
                        print(f"[tick {tick}] {changed} rows upserted")
            except Exception as exc:  # noqa: BLE001 — tick 级兜底：记录、退避、下 tick 重试
                print(f"[tick {tick}] 异常（退避后重试）: {exc}", file=sys.stderr)
                traceback.print_exc()
            finally:
                if pg_conn is not None:
                    pg_conn.close()
            time.sleep(POLL_INTERVAL_SEC)
    finally:
        neo4j_driver.close()


def main() -> None:
    try:
        run_sync_loop()
    except KeyboardInterrupt:
        print("\n[shutdown] 收到中断，退出 sync loop")
    except psycopg2.Error as exc:
        print("PostgreSQL 오류:", exc, file=sys.stderr)
        sys.exit(1)
    except Neo4jError as exc:
        print("Neo4j 오류:", exc, file=sys.stderr)
        sys.exit(1)


if __name__ == "__main__":
    main()
