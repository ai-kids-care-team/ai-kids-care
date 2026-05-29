# 数据库开发指南（Database Guide）

✅ 来源：`db/`、`db/dbml/`、`db/initdb/`、`db/ne4j_kindergartens/`。数据模型总览见 [architecture/data-architecture.md](../architecture/data-architecture.md)。

## Schema 修改工作流（DBML 优先）

✅ 权威 schema 定义是 **`db/dbml/schema.dbml`**，由它生成建表 SQL：

```bash
npm install -g @dbml/cli
dbml2sql db/dbml/schema.dbml -o db/initdb/01_create_schema.sql
```

> 因此**改表结构应从 `schema.dbml` 起**，生成 `01_create_schema.sql`，再同步更新后端 JPA 实体（否则 `ddl-auto=validate` 启动失败）。不要只改 SQL 或只改实体。

## initdb 脚本约定

✅ PostgreSQL 容器**首次创建数据卷时**自动按文件名顺序执行 `db/initdb/*.sql`。顺序与作用见 [data-architecture.md](../architecture/data-architecture.md#3-初始化脚本执行顺序)。

要点：

- 前缀数字决定执行顺序：`00`→`01`(DDL)→`02`菜单→`03`公共代码→`2x/3x/4x`种子→`88`公告→`99`序列同步。
- 种子顺序遵守外键依赖。
- `99_sync_sequences.sql` 在种子插入后重置自增序列，避免后续插入主键冲突。
- ⚠️ initdb 脚本**仅在数据卷为空时执行**。schema 改动后若要重新初始化，需删卷重建（见下，会丢数据）。

## 重置数据库（会丢数据，谨慎）

✅ 来自 `db/README.md` 的模式（按当前容器名）：

```bash
docker compose down -v          # 删除卷（含 postgres_data / neo4j_data）
docker compose up -d --build    # 重新初始化（重新执行 initdb）
```

## 导入中文/韩文种子数据（编码）

✅ `db/README.md` 建议：用 UTF-8 编码的 `.sql` 文件 + `psql -f`/重定向执行，避免内联 `-c`。Windows PowerShell：

```powershell
chcp 65001
$OutputEncoding = [Console]::OutputEncoding = [System.Text.UTF8Encoding]::new()
docker exec -i ai-kids-postgres psql -U kids_user -d kids_postgres_db < db/initdb/89_guardian_seed.sql
```

## Neo4j 图数据加载

✅ `db/ne4j_kindergartens/`（Python）从 PostgreSQL 抽取并构建图：

- `config.py` 读取 `DB_HOST/DB_NAME/...` 与 Neo4j 连接（环境变量）。
- `noXXX_insert_*.py` 按序导入节点（users/kindergartens/teachers/classes/children/guardians/角色/关系）。
- `no1000_create_relationships.py` 建关系边；`run_all.sh` 串联全流程。
- 在根 compose 中由 `data-loader` 服务一次性执行。
- 详细步骤见 `db/ne4j_kindergartens/SETUP_GUIDE.md`。

> ❓ 图为一次性加载，PG 变更后需重跑 loader 才能同步（无增量机制）。

## 时区

✅ 全部时间列为 `timestamptz`，容器统一 `TZ=Asia/Seoul`/`PGTZ=Asia/Seoul`。

## 相关工具目录

- `db/db_sample/` — 样例数据与参考文档（如 `guardian_RefData.md`）
- `db/mockaroo_schemas/` — 🔶 Mockaroo 造数 schema（推断用于生成测试数据）
- `db/db_timezone_setup/` — 时区设置说明
- `db/redis-docker-compose.yml` — 🔶 Redis 的独立 compose（注：根 compose 未含 Redis，见 open-questions）
