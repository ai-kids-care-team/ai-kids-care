# scripts/codegen → 已迁移

本目录的全部代码已于 **2026-05-29** 整体迁至仓库根目录的 **[`pg-spring-crud-codegen/`](../../pg-spring-crud-codegen/README.md)**，作为自洽的代码生成子工程（[ADR-0011](../../docs/decisions/adr/ADR-0011-extract-codegen-subproject.md)）。

旧路径：`scripts/codegen/{main.py, introspect_pg.py, model.py, naming.py, type_map.py, templates/*.mustache, .env.example, docker-compose.yml, requirements.txt}`
新路径：`pg-spring-crud-codegen/`（同名文件，结构不变）

> 本 README 仅作为"软指针"——避免内部链接 / 旧引用 404。新增工作请直接在 `pg-spring-crud-codegen/` 进行；本 README 可在所有内部引用完成切换、且经过若干个发布周期后移除。
