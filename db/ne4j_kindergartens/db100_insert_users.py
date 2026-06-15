import sys
from typing import Any, Dict, List, Optional

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
# 공통 유틸
# ============================================================
def to_iso_string(value: Any) -> Optional[str]:
    """
    datetime / date / 기타 값을 Neo4j에 넣기 좋은 형태로 문자열 변환
    """
    if value is None:
        return None

    if hasattr(value, "isoformat"):
        return value.isoformat()

    return str(value)


def normalize_user_row(row: Dict[str, Any]) -> Dict[str, Any]:
    """
    PostgreSQL에서 읽은 users row를 Neo4j 저장용 dict로 정규화
    """
    return {
        "user_id": row["user_id"],
        "login_id": row["login_id"],
        "email": row["email"],
        "phone": row["phone"],
        "password_hash": row["password_hash"],
        "status": str(row["status"]) if row["status"] is not None else None,
        "last_login_at": to_iso_string(row["last_login_at"]),
        "created_at": to_iso_string(row["created_at"]),
        "updated_at": to_iso_string(row["updated_at"]),
    }


# ============================================================
# PostgreSQL
# ============================================================
def get_postgres_connection():
    return psycopg2.connect(
        host=PG_HOST,
        port=PG_PORT,
        dbname=PG_NAME,
        user=PG_USER,
        password=PG_PASSWORD,
    )


def fetch_all_users() -> List[Dict[str, Any]]:
    query = """
        SELECT
            user_id,
            login_id,
            email,
            phone,
            password_hash,
            status,
            last_login_at,
            created_at,
            updated_at
        FROM users
        ORDER BY user_id
    """

    conn = None
    try:
        conn = get_postgres_connection()
        with conn.cursor(cursor_factory=psycopg2.extras.RealDictCursor) as cursor:
            cursor.execute(query)
            rows = cursor.fetchall()
            return [normalize_user_row(dict(row)) for row in rows]

    finally:
        if conn:
            conn.close()


# ============================================================
# Neo4j
# ============================================================
def get_neo4j_driver():
    return GraphDatabase.driver(
        NEO4J_URI,
        auth=(NEO4J_USERNAME, NEO4J_PASSWORD),
    )


def create_user_constraint(driver):
    """
    user_id 기준 유니크 제약
    Neo4j 5.x 문법
    """
    cypher = """
    CREATE CONSTRAINT user_user_id_unique IF NOT EXISTS
    FOR (u:User)
    REQUIRE u.user_id IS UNIQUE
    """
    with driver.session() as session:
        session.run(cypher)


def insert_users_batch(tx, users: List[Dict[str, Any]]):
    """
    users 리스트를 한번에 UNWIND로 적재
    - user_id 기준 MERGE
    - 기존 있으면 업데이트
    - 없으면 생성
    """
    cypher = """
    UNWIND $rows AS row
    MERGE (u:User {user_id: row.user_id})
    SET
        u.login_id = row.login_id,
        u.email = row.email,
        u.phone = row.phone,
        u.password_hash = row.password_hash,
        u.status = row.status,
        u.last_login_at = row.last_login_at,
        u.created_at = row.created_at,
        u.updated_at = row.updated_at
    """
    tx.run(cypher, rows=users)


def sync_users_to_neo4j(users: List[Dict[str, Any]]):
    driver = get_neo4j_driver()

    try:
        create_user_constraint(driver)

        total = len(users)
        if total == 0:
            print("PostgreSQL users 테이블에 데이터가 없습니다.")
            return

        with driver.session() as session:
            for start in range(0, total, BATCH_SIZE):
                end = start + BATCH_SIZE
                batch = users[start:end]
                session.execute_write(insert_users_batch, batch)
                print(f"[Neo4j 적재 완료] {start + 1} ~ {min(end, total)} / {total}")

        print(f"전체 동기화 완료: {total}건")

    finally:
        driver.close()


# ============================================================
# 메인
# ============================================================
def main():
    try:
        print("1) PostgreSQL users 조회 시작")
        users = fetch_all_users()
        print(f"PostgreSQL 조회 건수: {len(users)}")

        print("2) Neo4j 적재 시작")
        sync_users_to_neo4j(users)

        print("3) 작업 완료")

    except psycopg2.Error as e:
        print("PostgreSQL 오류 발생")
        print(str(e))
        sys.exit(1)

    except Neo4jError as e:
        print("Neo4j 오류 발생")
        print(str(e))
        sys.exit(1)

    except Exception as e:
        print("알 수 없는 오류 발생")
        print(str(e))
        sys.exit(1)


if __name__ == "__main__":
    main()