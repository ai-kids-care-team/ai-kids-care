# 数据库开发指南（Database Guide）

✅ 来源：`db/`、`db/dbml/`、`db/initdb/`、`db/ne4j_kindergartens/`。数据模型总览见 [architecture/data-architecture.md](../architecture/data-architecture.md)。

---

## Schema 变更工作流（完整，ADR-0012）

> **核心原则**：Schema 唯一权威 = `db/dbml/schema.dbml`（DB-first，见 [ADR-0004](../decisions/adr/ADR-0004-layered-backend-codegen.md)）。生产 schema 演进路径 = Flyway 迁移文件（见 [ADR-0012](../decisions/adr/ADR-0012-production-data-lifecycle.md)）。ERD 由 schema 重新派生，**不手工编辑**。

### 端到端流程

```
db/dbml/schema.dbml          ← 唯一权威，在此修改表结构
       │  dbml2sql (自动)
       ▼
db/initdb/01_create_schema.sql   ← 演示/initdb 路径（保持与 DBML 同步）
       │
       │  generate_migration.py  ← 工具：diff 当前迁移状态 vs 新 DBML
       ▼
backend/src/main/resources/db/migration/V{N}__description.sql  ← 草稿
       │  人工评审
       ▼
Flyway 迁移（生产部署时自动执行）
       │  ddl-auto=validate
       ▼
JPA 实体同步（否则启动失败）
       │
       ▼
ERD 重新派生（db/ERD/*.mmd 由 schema 生成，不手工编辑）
```

### 第一步：修改 DBML

在 `db/dbml/schema.dbml` 中做结构变更（新增表/列/索引/枚举值等）。

### 第二步：更新 initdb 快照

```bash
# 重新生成 01_create_schema.sql（需全局安装 @dbml/cli）
npm install -g @dbml/cli       # 仅首次
dbml2sql db/dbml/schema.dbml -o db/initdb/01_create_schema.sql
```

这保持演示/initdb 路径与 DBML 同步。

### 第三步：一次性安装 migra（仅首次）

```bash
pip install -r db/scripts/requirements-migra.txt
```

migra（[github.com/djrobstep/migra](https://github.com/djrobstep/migra)）是 PostgreSQL 专用 schema diff 工具，比对两个 PG 库的结构差异并生成 SQL。

### 第四步：生成迁移草稿

```bash
# 方法 A：直接运行脚本
python3 db/scripts/generate_migration.py <description>

# 方法 B：Gradle 任务（在 backend/ 目录执行）
./gradlew generateMigration -Pdesc=<description>

# 示例
python3 db/scripts/generate_migration.py add_rrn_hash_to_children
./gradlew generateMigration -Pdesc=relax_notifications_not_null
```

脚本做的事：
1. `dbml2sql` 导出当前 `schema.dbml` → 临时 SQL
2. 启动一个临时 `postgres:16-alpine` 容器
3. 在容器里建两个库：
   - `schema_from`：依次应用 `V1__.sql`, `V2__.sql`, ... 得到当前迁移后状态
   - `schema_to`：应用新 DBML 导出的 SQL 得到目标状态
4. 运行 `migra --unsafe schema_from schema_to` 生成 diff
5. 将带有警告注释的草稿写到 `backend/src/main/resources/db/migration/V{N}__description.sql`
6. 删除临时容器

### 第五步：人工评审草稿

草稿顶部有 `⚠️ HUMAN REVIEW REQUIRED` 注释。务必检查：

| 检查项 | 说明 |
| --- | --- |
| `DROP` 语句 | 确认是否真的要删除该对象，还是 migra 误判 |
| `NOT NULL` 新增 | 是否需要先 `UPDATE ... SET col = default` 回填现有行 |
| FK 约束 | 是否需要加 `DEFERRABLE INITIALLY IMMEDIATE` |
| ENUM 新增值 | 确认顺序与 Java enum 一致（`ddl-auto=validate` 校验） |
| 命名规范 | 索引/约束命名保持与 `01_create_schema.sql` 一致 |

> ⚠️ 迁移文件一旦被 Flyway 执行就**不可修改**（checksum 校验）。修订请新建 `VN+1__revert_...` 或 `VN+1__fix_...`。

### 第六步：同步 JPA 实体

`ddl-auto=validate` 在启动时对比 JPA 实体与实际表结构。Schema 变更后必须同步 `backend/src/main/java/com/ai_kids_care/v1/entity/` 中对应的 Entity 类（列名、类型、枚举等），否则应用无法启动。

### 第七步：运行测试（Testcontainers 验证）

```bash
cd backend
./gradlew test
```

测试覆盖两个场景（[ADR-0014](../decisions/adr/ADR-0014-test-baseline.md)）：

| 测试类 | 场景 | 验证内容 |
| --- | --- | --- |
| `BaseIntegrationTest` + 子类 | initdb 初始化后 Flyway baseline | schema 与 JPA 实体对齐（`ddl-auto=validate`） |
| `FlywayMigrationTest` | 空库（无 initdb） | V1..VN 迁移依次执行 + validate 通过 |

若测试通过，说明迁移文件正确且 JPA 实体已同步。

### 第八步：更新 ERD

ERD 由 schema 派生，不要手工编辑 `.mmd` 文件（见 [db/ERD/README.md](../../db/ERD/README.md)）。

### 第九步：提交

将以下文件一起提交到同一个 commit：
- `db/dbml/schema.dbml`（修改后）
- `db/initdb/01_create_schema.sql`（重新生成）
- `backend/src/main/resources/db/migration/V{N}__description.sql`（草稿评审后）
- 对应的 JPA 实体变更
- 相关 ERD 更新

---

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
