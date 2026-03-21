import os

import psycopg
import pystache
from dotenv import load_dotenv

from introspect_pg import list_tables, list_columns, primary_key_columns, foreign_keys
from model import EntityModel, FieldModel
from naming import snake_to_pascal, snake_to_camel, table_to_entity_name, resource_path_v1
from type_map import pg_to_java

load_dotenv()


def render(template_path: str, ctx: dict) -> str:
    with open(template_path, "r", encoding="utf-8") as f:
        return pystache.render(f.read(), ctx)


def write_file(base_dir: str, package: str, filename: str, content: str) -> None:
    out_path = os.path.join(base_dir, *package.split("."), filename)
    os.makedirs(os.path.dirname(out_path), exist_ok=True)
    with open(out_path, "w", encoding="utf-8") as f:
        f.write(content)


def to_entity_model(conn, *, package_base: str, schema: str, table: str) -> EntityModel:
    cols = list_columns(conn, schema, table)
    pk_set = primary_key_columns(conn, schema, table)
    fk_map = foreign_keys(conn, schema, table)

    fields = []
    id_type = "Long"  # fallback
    for c in cols:
        java_type = pg_to_java(c.data_type, c.udt_name)
        field = FieldModel(
            name=snake_to_camel(c.column_name),
            column=c.column_name,
            java_type=java_type,
            required=(not c.is_nullable),
            max_len=c.char_max_len,
            comment=c.comment,
            pk=(c.column_name in pk_set),
            fk=fk_map.get(c.column_name),
        )
        fields.append(field)
        if field.pk:
            id_type = java_type

    for i, f in enumerate(fields):
        f.last = (i == len(fields) - 1)

    return EntityModel(
        package_base=package_base,
        schema=schema,
        table=table,
        entity_name=table_to_entity_name(table),
        resource=resource_path_v1(table),
        id_type=id_type,
        fields=fields,
    )


def main():
    # 1) 配置区（你改这里）
    dsn = os.environ.get("PG_DSN", "postgresql://user:pass@localhost:5432/mydb")
    schema = os.environ.get("PG_SCHEMA", "public")
    package_base = os.environ.get("JAVA_PACKAGE_BASE", "com.example.app")
    out_java = os.environ.get("OUT_JAVA", "./out/src/main/java")

    # 可选：只生成部分表
    only_tables = os.environ.get("ONLY_TABLES")  # 例如 "users,orders"
    only = set(t.strip() for t in only_tables.split(",")) if only_tables else None

    templates_dir = os.path.join(os.path.dirname(__file__), "templates")

    with psycopg.connect(dsn) as conn:
        tables = list_tables(conn, schema)
        if only:
            tables = [t for t in tables if t in only]

        for table in tables:
            model = to_entity_model(conn, package_base=package_base, schema=schema, table=table)

            # 2) 生成文件（示例：CreateDTO + VO）
            dto_pkg = f"{package_base}.dto"
            vo_pkg = f"{package_base}.vo"
            mapper_pkg = f"{package_base}.mapper"
            controller_pkg = f"{package_base}.controller"
            service_pkg = f"{package_base}.service"

            create_dto = render(os.path.join(templates_dir, "CreateDTO.mustache"), model.__dict__ | {
                "fields": [f.__dict__ for f in model.fields]
            })
            update_dto = render(os.path.join(templates_dir, "UpdateDTO.mustache"), model.__dict__ | {
                "fields": [f.__dict__ for f in model.fields]
            })
            mapper = render(os.path.join(templates_dir, "Mapper.mustache"), model.__dict__ | {
                "fields": [f.__dict__ for f in model.fields]
            })
            vo = render(os.path.join(templates_dir, "VO.mustache"), model.__dict__ | {
                "fields": [f.__dict__ for f in model.fields]
            })
            controller = render(os.path.join(templates_dir, "Controller.mustache"), model.__dict__ | {
                "fields": [f.__dict__ for f in model.fields]
            })
            service = render(os.path.join(templates_dir, "Service.mustache"), model.__dict__ | {
                "fields": [f.__dict__ for f in model.fields]
            })

            write_file(out_java, dto_pkg, f"{model.entity_name}CreateDTO.java", create_dto)
            write_file(out_java, dto_pkg, f"{model.entity_name}UpdateDTO.java", update_dto)
            write_file(out_java, mapper_pkg, f"{model.entity_name}Mapper.java", mapper)
            write_file(out_java, vo_pkg, f"{model.entity_name}VO.java", vo)
            write_file(out_java, controller_pkg, f"{model.entity_name}Controller.java", controller)
            write_file(out_java, service_pkg, f"{model.entity_name}Service.java", service)

            print(f"generated: {model.entity_name} (table={table})")


if __name__ == "__main__":
    main()
