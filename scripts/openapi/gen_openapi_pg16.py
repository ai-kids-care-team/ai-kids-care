#!/usr/bin/env python3
# -*- coding: utf-8 -*-

"""
PostgreSQL 16 -> OpenAPI 3.0.2 generator
- Reads tables/columns/PK/FK/enums/comments from pg_catalog
- Generates basic CRUD endpoints + schemas

Deps:
  pip install psycopg[binary] PyYAML

Run:
  python gen_openapi_pg16.py \
    --dsn "postgresql://postgres:postgres@localhost:5432/postgres" \
    --schemas public \
    --base-path /api/v1 \
    --title "KG CCTV Platform API" \
    --version "0.1.0" \
    --server-url "http://localhost:8080" \
    --out openapi.yaml
"""

from __future__ import annotations

import argparse
import re
from dataclasses import dataclass
from typing import Dict, List, Optional, Tuple

import yaml
import psycopg


# -----------------------------
# Models
# -----------------------------

@dataclass
class Column:
    name: str
    pg_type: str  # format_type(...) output
    udt_name: str  # underlying type name (enum name, etc.)
    is_nullable: bool
    default: Optional[str]
    comment: Optional[str]


@dataclass
class ForeignKey:
    columns: List[str]
    ref_schema: str
    ref_table: str
    ref_columns: List[str]


@dataclass
class Table:
    schema: str
    name: str
    comment: Optional[str]
    columns: List[Column]
    pk_columns: List[str]
    fks: List[ForeignKey]


# -----------------------------
# Queries
# -----------------------------

SQL_TABLES = """
             SELECT n.nspname                          AS schema_name,
                    c.relname                          AS table_name,
                    obj_description(c.oid, 'pg_class') AS table_comment
             FROM pg_class c
                      JOIN pg_namespace n ON n.oid = c.relnamespace
             WHERE c.relkind = 'r'
               AND n.nspname = ANY (%(schemas)s)
             ORDER BY n.nspname, c.relname; \
             """

SQL_COLUMNS = """
              SELECT n.nspname                                       AS schema_name,
                     c.relname                                       AS table_name,
                     a.attname                                       AS column_name,
                     pg_catalog.format_type(a.atttypid, a.atttypmod) AS data_type,
                     t.typname                                       AS udt_name,
                     NOT a.attnotnull                                AS is_nullable,
                     pg_get_expr(ad.adbin, ad.adrelid)               AS column_default,
                     col_description(a.attrelid, a.attnum)           AS column_comment
              FROM pg_attribute a
                       JOIN pg_class c ON c.oid = a.attrelid
                       JOIN pg_namespace n ON n.oid = c.relnamespace
                       JOIN pg_type t ON t.oid = a.atttypid
                       LEFT JOIN pg_attrdef ad ON ad.adrelid = a.attrelid AND ad.adnum = a.attnum
              WHERE c.relkind = 'r'
                AND a.attnum > 0
                AND NOT a.attisdropped
                AND n.nspname = ANY (%(schemas)s)
              ORDER BY n.nspname, c.relname, a.attnum; \
              """

SQL_PKS = """
          SELECT n.nspname AS schema_name,
                 c.relname AS table_name,
                 a.attname AS column_name,
                 ck.ord    AS ord
          FROM pg_constraint con
                   JOIN pg_class c ON c.oid = con.conrelid
                   JOIN pg_namespace n ON n.oid = c.relnamespace
                   JOIN unnest(con.conkey) WITH ORDINALITY AS ck(attnum, ord) ON TRUE
                   JOIN pg_attribute a ON a.attrelid = c.oid AND a.attnum = ck.attnum
          WHERE con.contype = 'p'
            AND n.nspname = ANY (%(schemas)s)
          ORDER BY schema_name, table_name, ck.ord; \
          """

# FK grouped per constraint
SQL_FKS = """
          SELECT ns.nspname                             AS schema_name,
                 cls.relname                            AS table_name,
                 con.conname                            AS constraint_name,
                 array_agg(att.attname ORDER BY u1.ord) AS column_names,
                 nst.nspname                            AS ref_schema_name,
                 clt.relname                            AS ref_table_name,
                 array_agg(at2.attname ORDER BY u2.ord) AS ref_column_names
          FROM pg_constraint con
                   JOIN pg_class cls ON cls.oid = con.conrelid
                   JOIN pg_namespace ns ON ns.oid = cls.relnamespace
                   JOIN pg_class clt ON clt.oid = con.confrelid
                   JOIN pg_namespace nst ON nst.oid = clt.relnamespace
                   JOIN unnest(con.conkey) WITH ORDINALITY AS u1(attnum, ord) ON TRUE
                   JOIN unnest(con.confkey) WITH ORDINALITY AS u2(attnum, ord) ON u2.ord = u1.ord
                   JOIN pg_attribute att ON att.attrelid = cls.oid AND att.attnum = u1.attnum
                   JOIN pg_attribute at2 ON at2.attrelid = clt.oid AND at2.attnum = u2.attnum
          WHERE con.contype = 'f'
            AND ns.nspname = ANY (%(schemas)s)
          GROUP BY ns.nspname, cls.relname, con.conname, nst.nspname, clt.relname
          ORDER BY schema_name, table_name, constraint_name; \
          """

# ENUM values for types in our schemas
SQL_ENUMS = """
            SELECT n.nspname       AS enum_schema,
                   t.typname       AS enum_name,
                   e.enumlabel     AS enum_value,
                   e.enumsortorder AS sort_order
            FROM pg_type t
                     JOIN pg_namespace n ON n.oid = t.typnamespace
                     JOIN pg_enum e ON e.enumtypid = t.oid
            WHERE t.typtype = 'e'
              AND n.nspname = ANY (%(schemas)s)
            ORDER BY enum_schema, enum_name, sort_order; \
            """


# -----------------------------
# Helpers
# -----------------------------

def to_pascal(s: str) -> str:
    parts = re.split(r"[^a-zA-Z0-9]+", s)
    parts = [p for p in parts if p]
    return "".join(p[:1].upper() + p[1:] for p in parts) if parts else s


def schema_name(table: Table, suffix: str) -> str:
    if table.schema == "public":
        return f"{to_pascal(table.name)}{suffix}"
    return f"{to_pascal(table.schema)}{to_pascal(table.name)}{suffix}"


def _strip_type_modifiers(pg_type: str) -> str:
    return re.sub(r"\s*\(.*\)\s*$", "", pg_type.strip().lower())


def is_server_generated(default_expr: Optional[str]) -> bool:
    if not default_expr:
        return False
    d = default_expr.lower()
    return (
            "nextval(" in d
            or "gen_random_uuid()" in d
            or "uuid_generate_v" in d
            or "now()" in d
            or "current_timestamp" in d
    )


def is_identity(column_default: Optional[str]) -> bool:
    # Identity columns usually show as "generated by default as identity" in information_schema,
    # but via pg_attrdef it may not show. We can still treat BIGINT PKs as server-generated if PK + no explicit default.
    return False


# -----------------------------
# Type mapping (incl. enums)
# -----------------------------

def pg_type_to_oas(col: Column, enums: Dict[Tuple[str, str], List[str]], schema: str) -> Dict:
    """
    Map PostgreSQL type -> OpenAPI schema fragment
    - If column is enum: {type: string, enum: [...]}
    """
    # enum detection: udt_name is the enum type name; check if exists in enums
    enum_key = (schema, col.udt_name)
    if enum_key in enums:
        return {"type": "string", "enum": enums[enum_key]}

    t = _strip_type_modifiers(col.pg_type)

    if t == "uuid":
        return {"type": "string", "format": "uuid"}
    if t in ("bigint", "int8"):
        return {"type": "integer", "format": "int64"}
    if t in ("integer", "int", "int4", "smallint", "int2"):
        return {"type": "integer", "format": "int32"}
    if t in ("boolean", "bool"):
        return {"type": "boolean"}
    if t in ("real", "float4"):
        return {"type": "number", "format": "float"}
    if t in ("double precision", "float8"):
        return {"type": "number", "format": "double"}
    if t in ("numeric", "decimal"):
        # safer default to preserve precision
        return {"type": "string", "description": "Decimal string (to preserve precision)."}
    if t in ("text", "character varying", "varchar", "character", "char"):
        return {"type": "string"}
    if t == "date":
        return {"type": "string", "format": "date"}
    if t in ("timestamp", "timestamp without time zone", "timestamptz", "timestamp with time zone"):
        return {"type": "string", "format": "date-time"}
    if t in ("json", "jsonb"):
        return {"type": "object", "additionalProperties": True}
    if t == "bytea":
        return {"type": "string", "format": "byte"}

    return {"type": "string", "description": f"Unmapped PostgreSQL type: {col.pg_type} (udt={col.udt_name})"}


# -----------------------------
# OpenAPI components
# -----------------------------

def make_api_error_schema() -> Dict:
    return {
        "type": "object",
        "required": ["code", "message"],
        "properties": {
            "code": {"type": "string"},
            "message": {"type": "string"},
            "details": {"type": "object", "additionalProperties": True},
            "traceId": {"type": "string"},
        },
    }


def make_page_result_schema(item_ref: str) -> Dict:
    return {
        "type": "object",
        "required": ["items", "page", "size", "total"],
        "properties": {
            "items": {"type": "array", "items": {"$ref": item_ref}},
            "page": {"type": "integer", "format": "int32", "minimum": 0},
            "size": {"type": "integer", "format": "int32", "minimum": 1},
            "total": {"type": "integer", "format": "int64", "minimum": 0},
        },
    }


def build_table_schemas(table: Table, enums: Dict[Tuple[str, str], List[str]]) -> Dict[str, Dict]:
    resp_name = schema_name(table, "Response")
    create_name = schema_name(table, "CreateRequest")
    update_name = schema_name(table, "UpdateRequest")

    # Response schema
    resp_props: Dict[str, Dict] = {}
    resp_required: List[str] = []
    for col in table.columns:
        prop = pg_type_to_oas(col, enums, table.schema)
        if col.comment:
            prop["description"] = col.comment
        resp_props[col.name] = prop
        if not col.is_nullable:
            resp_required.append(col.name)

    resp_schema: Dict = {"type": "object", "properties": resp_props}
    if resp_required:
        resp_schema["required"] = resp_required
    if table.comment:
        resp_schema["description"] = table.comment

    # Create schema: exclude PK and server-generated timestamps/defaults
    excluded = set(table.pk_columns)
    create_props: Dict[str, Dict] = {}
    create_required: List[str] = []
    for col in table.columns:
        if col.name in excluded:
            continue
        if is_server_generated(col.default):
            continue

        prop = pg_type_to_oas(col, enums, table.schema)
        if col.comment:
            prop["description"] = col.comment
        create_props[col.name] = prop

        if (not col.is_nullable) and (not col.default) and (col.name not in excluded):
            create_required.append(col.name)

    create_schema: Dict = {"type": "object", "properties": create_props}
    if create_required:
        create_schema["required"] = create_required

    # Update schema: same props, all optional
    update_schema: Dict = {"type": "object", "properties": create_props}

    return {
        resp_name: resp_schema,
        create_name: create_schema,
        update_name: update_schema,
    }


def make_common_responses() -> Dict:
    return {
        "BadRequest": {
            "description": "Bad Request",
            "content": {"application/json": {"schema": {"$ref": "#/components/schemas/ErrorResponse"}}},
        },
        "NotFound": {
            "description": "Not Found",
            "content": {"application/json": {"schema": {"$ref": "#/components/schemas/ErrorResponse"}}},
        },
        "Conflict": {
            "description": "Conflict",
            "content": {"application/json": {"schema": {"$ref": "#/components/schemas/ErrorResponse"}}},
        },
        "InternalError": {
            "description": "Internal Server Error",
            "content": {"application/json": {"schema": {"$ref": "#/components/schemas/ErrorResponse"}}},
        },
    }


# -----------------------------
# Paths (CRUD)
# -----------------------------

def tag_name(table: Table) -> str:
    return to_pascal(table.name)


def pick_single_pk(table: Table) -> Optional[str]:
    return table.pk_columns[0] if len(table.pk_columns) == 1 else None


def pk_schema_for(table: Table, enums: Dict[Tuple[str, str], List[str]]) -> Dict:
    pk = pick_single_pk(table)
    if not pk:
        return {"type": "string"}
    col = next((c for c in table.columns if c.name == pk), None)
    if not col:
        return {"type": "string"}
    return pg_type_to_oas(col, enums, table.schema)


def make_list_op(table: Table, resp_name: str) -> Dict:
    return {
        "tags": [tag_name(table)],
        "operationId": f"list{to_pascal(table.name)}",
        "summary": f"List {table.name}",
        "parameters": [
            {"name": "page", "in": "query", "schema": {"type": "integer", "format": "int32", "minimum": 0}},
            {"name": "size", "in": "query", "schema": {"type": "integer", "format": "int32", "minimum": 1}},
            {"name": "sort", "in": "query", "schema": {"type": "string"}, "description": "e.g. created_at,desc"},
        ],
        "responses": {
            "200": {
                "description": "OK",
                "content": {"application/json": {"schema": {"$ref": f"#/components/schemas/{resp_name}Page"}}},
            },
            "400": {"$ref": "#/components/responses/BadRequest"},
            "500": {"$ref": "#/components/responses/InternalError"},
        },
    }


def make_create_op(table: Table, create_name: str, resp_name: str) -> Dict:
    return {
        "tags": [tag_name(table)],
        "operationId": f"create{to_pascal(table.name)}",
        "summary": f"Create {table.name}",
        "requestBody": {
            "required": True,
            "content": {"application/json": {"schema": {"$ref": f"#/components/schemas/{create_name}"}}},
        },
        "responses": {
            "201": {"description": "Created",
                    "content": {"application/json": {"schema": {"$ref": f"#/components/schemas/{resp_name}"}}}},
            "400": {"$ref": "#/components/responses/BadRequest"},
            "409": {"$ref": "#/components/responses/Conflict"},
            "500": {"$ref": "#/components/responses/InternalError"},
        },
    }


def make_get_op(table: Table, resp_name: str, pk_schema: Dict) -> Dict:
    return {
        "tags": [tag_name(table)],
        "operationId": f"get{to_pascal(table.name)}",
        "summary": f"Get {table.name} by id",
        "parameters": [{"name": "id", "in": "path", "required": True, "schema": pk_schema}],
        "responses": {
            "200": {"description": "OK",
                    "content": {"application/json": {"schema": {"$ref": f"#/components/schemas/{resp_name}"}}}},
            "404": {"$ref": "#/components/responses/NotFound"},
            "500": {"$ref": "#/components/responses/InternalError"},
        },
    }


def make_put_op(table: Table, update_name: str, resp_name: str, pk_schema: Dict) -> Dict:
    return {
        "tags": [tag_name(table)],
        "operationId": f"update{to_pascal(table.name)}",
        "summary": f"Update {table.name} by id",
        "parameters": [{"name": "id", "in": "path", "required": True, "schema": pk_schema}],
        "requestBody": {
            "required": True,
            "content": {"application/json": {"schema": {"$ref": f"#/components/schemas/{update_name}"}}},
        },
        "responses": {
            "200": {"description": "OK",
                    "content": {"application/json": {"schema": {"$ref": f"#/components/schemas/{resp_name}"}}}},
            "400": {"$ref": "#/components/responses/BadRequest"},
            "404": {"$ref": "#/components/responses/NotFound"},
            "409": {"$ref": "#/components/responses/Conflict"},
            "500": {"$ref": "#/components/responses/InternalError"},
        },
    }


def make_delete_op(table: Table, pk_schema: Dict) -> Dict:
    return {
        "tags": [tag_name(table)],
        "operationId": f"delete{to_pascal(table.name)}",
        "summary": f"Delete {table.name} by id",
        "parameters": [{"name": "id", "in": "path", "required": True, "schema": pk_schema}],
        "responses": {
            "204": {"description": "No Content"},
            "404": {"$ref": "#/components/responses/NotFound"},
            "500": {"$ref": "#/components/responses/InternalError"},
        },
    }


def make_get_by_key_op(table: Table, resp_name: str, key_params: List[Dict]) -> Dict:
    return {
        "tags": [tag_name(table)],
        "operationId": f"get{to_pascal(table.name)}ByKey",
        "summary": f"Get {table.name} by composite key",
        "parameters": key_params,
        "responses": {
            "200": {"description": "OK",
                    "content": {"application/json": {"schema": {"$ref": f"#/components/schemas/{resp_name}"}}}},
            "404": {"$ref": "#/components/responses/NotFound"},
            "500": {"$ref": "#/components/responses/InternalError"},
        },
    }


def make_delete_by_key_op(table: Table, key_params: List[Dict]) -> Dict:
    return {
        "tags": [tag_name(table)],
        "operationId": f"delete{to_pascal(table.name)}ByKey",
        "summary": f"Delete {table.name} by composite key",
        "parameters": key_params,
        "responses": {
            "204": {"description": "No Content"},
            "404": {"$ref": "#/components/responses/NotFound"},
            "500": {"$ref": "#/components/responses/InternalError"},
        },
    }


def build_paths_for_table(table: Table, base_path: str, enums: Dict[Tuple[str, str], List[str]]) -> Dict[str, Dict]:
    resp_name = schema_name(table, "Response")
    create_name = schema_name(table, "CreateRequest")
    update_name = schema_name(table, "UpdateRequest")

    coll_path = f"{base_path}/{table.name}"
    paths: Dict[str, Dict] = {
        coll_path: {
            "get": make_list_op(table, resp_name),
            "post": make_create_op(table, create_name, resp_name),
        }
    }

    single_pk = pick_single_pk(table)
    if single_pk:
        item_path = f"{coll_path}" + "/{id}"
        pk_schema = pk_schema_for(table, enums)
        paths[item_path] = {
            "get": make_get_op(table, resp_name, pk_schema),
            "put": make_put_op(table, update_name, resp_name, pk_schema),
            "delete": make_delete_op(table, pk_schema),
        }
    else:
        # composite PK: add by-key endpoints using query params
        if table.pk_columns:
            key_params = []
            for pk in table.pk_columns:
                col = next((c for c in table.columns if c.name == pk), None)
                sch = pg_type_to_oas(col, enums, table.schema) if col else {"type": "string"}
                key_params.append({"name": pk, "in": "query", "required": True, "schema": sch})
            by_key_path = f"{coll_path}/by-key"
            paths[by_key_path] = {
                "get": make_get_by_key_op(table, resp_name, key_params),
                "delete": make_delete_by_key_op(table, key_params),
            }

    return paths


def build_components(tables: List[Table], enums: Dict[Tuple[str, str], List[str]]) -> Dict:
    schemas: Dict[str, Dict] = {"ErrorResponse": make_api_error_schema()}

    for t in tables:
        schemas.update(build_table_schemas(t, enums))
        resp_name = schema_name(t, "Response")
        schemas[f"{resp_name}Page"] = make_page_result_schema(f"#/components/schemas/{resp_name}")

    return {"schemas": schemas, "responses": make_common_responses()}


# -----------------------------
# Load catalog
# -----------------------------

def load_enums(conn, schemas: List[str]) -> Dict[Tuple[str, str], List[str]]:
    out: Dict[Tuple[str, str], List[str]] = {}
    with conn.cursor() as cur:
        cur.execute(SQL_ENUMS, {"schemas": schemas})
        for enum_schema, enum_name, enum_value, _sort in cur.fetchall():
            out.setdefault((enum_schema, enum_name), []).append(enum_value)
    return out


def build_catalog(conn, schemas: List[str]) -> List[Table]:
    with conn.cursor() as cur:
        cur.execute(SQL_TABLES, {"schemas": schemas})
        table_rows = cur.fetchall()

        cur.execute(SQL_COLUMNS, {"schemas": schemas})
        col_rows = cur.fetchall()

        cur.execute(SQL_PKS, {"schemas": schemas})
        pk_rows = cur.fetchall()

        cur.execute(SQL_FKS, {"schemas": schemas})
        fk_rows = cur.fetchall()

    tables: Dict[Tuple[str, str], Table] = {}
    for schema_name, table_name, table_comment in table_rows:
        tables[(schema_name, table_name)] = Table(
            schema=schema_name,
            name=table_name,
            comment=table_comment,
            columns=[],
            pk_columns=[],
            fks=[],
        )

    for schema_name, table_name, col_name, data_type, udt_name, is_nullable, col_default, col_comment in col_rows:
        t = tables.get((schema_name, table_name))
        if not t:
            continue
        t.columns.append(
            Column(
                name=col_name,
                pg_type=data_type,
                udt_name=udt_name,
                is_nullable=bool(is_nullable),
                default=col_default,
                comment=col_comment,
            )
        )

    pk_map: Dict[Tuple[str, str], List[Tuple[int, str]]] = {}
    for schema_name, table_name, col_name, ord_ in pk_rows:
        pk_map.setdefault((schema_name, table_name), []).append((int(ord_), col_name))
    for k, ord_cols in pk_map.items():
        ord_cols.sort(key=lambda x: x[0])
        if k in tables:
            tables[k].pk_columns = [c for _, c in ord_cols]

    for schema_name, table_name, _conname, cols, ref_schema, ref_table, ref_cols in fk_rows:
        t = tables.get((schema_name, table_name))
        if not t:
            continue
        t.fks.append(
            ForeignKey(
                columns=list(cols),
                ref_schema=ref_schema,
                ref_table=ref_table,
                ref_columns=list(ref_cols),
            )
        )

    out = list(tables.values())
    out.sort(key=lambda x: (x.schema, x.name))
    return out


# -----------------------------
# OpenAPI doc
# -----------------------------

def build_openapi_doc(
        tables: List[Table],
        enums: Dict[Tuple[str, str], List[str]],
        title: str,
        version: str,
        server_url: str,
        base_path: str,
) -> Dict:
    components = build_components(tables, enums)

    paths: Dict[str, Dict] = {}
    for t in tables:
        paths.update(build_paths_for_table(t, base_path, enums))

    tags = []
    for t in tables:
        tg = {"name": tag_name(t)}
        if t.comment:
            tg["description"] = t.comment
        tags.append(tg)

    return {
        "openapi": "3.0.2",
        "info": {"title": title, "version": version},
        "servers": [{"url": server_url}],
        "tags": tags,
        "paths": paths,
        "components": components,
    }


# -----------------------------
# CLI
# -----------------------------

def parse_args():
    p = argparse.ArgumentParser()
    p.add_argument("--dsn", required=True)
    p.add_argument("--schemas", default="public", help="comma-separated")
    p.add_argument("--base-path", default="/v1")
    p.add_argument("--title", default="Generated API")
    p.add_argument("--version", default="1.0.0")
    p.add_argument("--server-url", default="http://localhost:8080/api")
    p.add_argument("--out", default="openapi.yaml")
    return p.parse_args()


class NoAliasDumper(yaml.SafeDumper):
    def ignore_aliases(self, data):
        return True


def main():
    args = parse_args()
    schemas = [s.strip() for s in args.schemas.split(",") if s.strip()]
    base_path = args.base_path.rstrip("/")

    with psycopg.connect(args.dsn) as conn:
        tables = build_catalog(conn, schemas)
        enums = load_enums(conn, schemas)

    doc = build_openapi_doc(
        tables=tables,
        enums=enums,
        title=args.title,
        version=args.version,
        server_url=args.server_url,
        base_path=base_path,
    )

    with open(args.out, "w", encoding="utf-8") as f:
        yaml.dump(doc, f, sort_keys=False, allow_unicode=True, Dumper=NoAliasDumper)

    print(f"Wrote OpenAPI 3.0.2 to: {args.out}")


if __name__ == "__main__":
    main()
