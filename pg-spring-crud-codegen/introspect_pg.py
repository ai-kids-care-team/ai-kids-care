from dataclasses import dataclass
from typing import Optional, List, Dict
import psycopg

@dataclass
class PgColumn:
    column_name: str
    is_nullable: bool
    data_type: str
    udt_name: str
    char_max_len: Optional[int]
    numeric_precision: Optional[int]
    numeric_scale: Optional[int]
    comment: Optional[str]

def list_columns(conn, schema: str, table: str) -> List[PgColumn]:
    sql = """
    SELECT
      c.column_name,
      (c.is_nullable = 'YES') AS is_nullable,
      c.data_type,
      c.udt_name,
      c.character_maximum_length,
      c.numeric_precision,
      c.numeric_scale,
      pgd.description AS column_comment
    FROM information_schema.columns c
    LEFT JOIN pg_catalog.pg_statio_all_tables st
      ON st.schemaname = c.table_schema AND st.relname = c.table_name
    LEFT JOIN pg_catalog.pg_description pgd
      ON pgd.objoid = st.relid AND pgd.objsubid = c.ordinal_position
    WHERE c.table_schema = %s AND c.table_name = %s
    ORDER BY c.ordinal_position;
    """
    rows = conn.execute(sql, (schema, table)).fetchall()
    return [
        PgColumn(
            column_name=r[0],
            is_nullable=r[1],
            data_type=r[2],
            udt_name=r[3],
            char_max_len=r[4],
            numeric_precision=r[5],
            numeric_scale=r[6],
            comment=r[7],
        )
        for r in rows
    ]

def primary_key_columns(conn, schema: str, table: str) -> set[str]:
    # 用 regclass 要 schema-qualified
    sql = """
    SELECT a.attname AS column_name
    FROM pg_index i
    JOIN pg_class  c ON c.oid = i.indrelid
    JOIN pg_namespace n ON n.oid = c.relnamespace
    JOIN pg_attribute a ON a.attrelid = i.indrelid AND a.attnum = ANY(i.indkey)
    WHERE i.indisprimary
      AND n.nspname = %s
      AND c.relname = %s;
    """
    rows = conn.execute(sql, (schema, table)).fetchall()
    return {r[0] for r in rows}

def foreign_keys(conn, schema: str, table: str) -> Dict[str, Dict[str, str]]:
    sql = """
    SELECT
      kcu.column_name,
      ccu.table_name AS foreign_table_name,
      ccu.column_name AS foreign_column_name
    FROM information_schema.table_constraints tc
    JOIN information_schema.key_column_usage kcu
      ON tc.constraint_name = kcu.constraint_name AND tc.table_schema = kcu.table_schema
    JOIN information_schema.constraint_column_usage ccu
      ON ccu.constraint_name = tc.constraint_name AND ccu.table_schema = tc.table_schema
    WHERE tc.constraint_type = 'FOREIGN KEY'
      AND tc.table_schema = %s
      AND tc.table_name = %s;
    """
    rows = conn.execute(sql, (schema, table)).fetchall()
    out: Dict[str, Dict[str, str]] = {}
    for col, ft, fc in rows:
        out[col] = {"table": ft, "column": fc}
    return out

def list_tables(conn, schema: str) -> List[str]:
    sql = """
    SELECT table_name
    FROM information_schema.tables
    WHERE table_schema = %s AND table_type='BASE TABLE'
    ORDER BY table_name;
    """
    rows = conn.execute(sql, (schema,)).fetchall()
    return [r[0] for r in rows]