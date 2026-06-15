# pg-spring-crud-codegen

PostgreSQL schema → Spring CRUD（Java）代码生成器。

本子工程把"按数据库表结构生成 6 类 Java 文件（CreateDTO / UpdateDTO / Mapper / VO / Controller / Service）"的能力**与主应用解耦**：对主仓**零运行时依赖**，仅在开发期手工触发；规划为日后可独立抽出为单独仓库（[ADR-0011](../docs/decisions/adr/ADR-0011-extract-codegen-subproject.md)）。

> 历史位置：`scripts/codegen/`。2026-05-29 整体迁至本目录，详见上述 ADR。

## 生成的内容

对 PostgreSQL `information_schema` 中的每张表（可用 `ONLY_TABLES` / `EXCLUDE_TABLES` 限定），按 `templates/` 中的 Mustache 模板渲染输出：

```text
<JAVA_PACKAGE_BASE>.dto.<Entity>CreateDTO
<JAVA_PACKAGE_BASE>.dto.<Entity>UpdateDTO
<JAVA_PACKAGE_BASE>.mapper.<Entity>Mapper
<JAVA_PACKAGE_BASE>.vo.<Entity>VO
<JAVA_PACKAGE_BASE>.controller.<Entity>Controller
<JAVA_PACKAGE_BASE>.service.<Entity>Service
```

分层契约见 [ADR-0004](../docs/decisions/adr/ADR-0004-layered-backend-codegen.md)。

## 文件结构

```text
pg-spring-crud-codegen/
├── README.md           # 本文件
├── requirements.txt    # Python 依赖
├── .env.example        # 环境变量样例
├── docker-compose.yml  # 可选：本地 PG（用主仓 schema 引导）——仅在仓内时可用
├── main.py             # 入口
├── introspect_pg.py    # PG schema 内省（表/列/PK/FK/注释）
├── model.py            # EntityModel / FieldModel
├── naming.py           # snake_case ↔ PascalCase/camelCase 转换
├── type_map.py         # PG 类型 → Java 类型映射
└── templates/          # 6 个 Mustache 模板
    ├── Controller.mustache
    ├── CreateDTO.mustache
    ├── UpdateDTO.mustache
    ├── Mapper.mustache
    ├── VO.mustache
    └── Service.mustache
```

## 前置依赖

- Python 3.10+
- 一个可连接的 PostgreSQL（含目标 schema）
- 安装依赖：

```bash
pip install -r requirements.txt
```

## 配置（环境变量）

复制 `.env.example` 为 `.env` 并按实际情况填写：

| 变量 | 说明 | 默认 |
| --- | --- | --- |
| `PG_DSN` | PostgreSQL 连接串 | `postgresql://user:pass@localhost:5432/mydb` |
| `PG_SCHEMA` | 目标 schema | `public` |
| `JAVA_PACKAGE_BASE` | 生成代码的 Java 包前缀 | `com.example.app` |
| `OUT_JAVA` | 输出根目录（相对当前目录） | `../java-generated` |
| `ONLY_TABLES` | 仅生成这些表（逗号分隔，留空表示全部） | （空） |
| `EXCLUDE_TABLES` | 排除这些表（逗号分隔） | （空） |

## 运行

```bash
python main.py
```

控制台将逐表打印 `generated: <EntityName> (table=<table>)`。

## 与主仓的可选联动（仅在仓内时）

`docker-compose.yml` 提供一个一次性 PG 容器，挂载主仓的 `db/initdb/01_create_schema.sql`（相对路径 `../db/initdb/...`）用作内省源——便于不连真实环境时本地试跑：

```bash
docker compose up -d
PG_DSN=postgresql://postgres:postgres@localhost:5432/appdb python main.py
docker compose down
```

> **拆仓提醒**：本子工程未来从主仓抽出后，该 `docker-compose.yml` 的相对路径将失效；届时可改为自带最小 schema 样例或仅依赖外部 PG。本入口（`main.py`）本身**只依赖 PG_DSN**，与主仓零耦合。

## 设计约束

- 一次性脚手架，**与生成后代码无双向绑定**：手工演进的 service/controller 不应被重新生成覆盖（重新生成前请备份或仅生成新增表）。
- 后端 schema 真相位于 `db/dbml/schema.dbml` → `db/initdb/01_create_schema.sql`（见 [data-architecture.md §2](../docs/architecture/data-architecture.md)）。本工具消费的是 PG 实际结构（即 SQL 落库后的形态）。
