## Why

迭代修数据库累积出 Flyway `V1..V12` 迁移链：`db/initdb/01_create_schema.sql` 停在 **V1 旧基线形态**（仍含 `rrn_encrypted` 列、`device_tokens` 表、旧式 `quiet_hours_json`），真实最终 schema 由 `V2..V12` 在其上叠加而成。两条装配路径割裂、新人难懂、initdb 已是陈旧快照。**当前无生产环境**，是把起点收敛为单一最终基线的最佳窗口——生产存在后 squash 成本剧增。

## What Changes

- **BREAKING（迁移历史，非业务语义）**：删除 `backend/src/main/resources/db/migration/V2__*.sql … V12__*.sql`，将 `V1..V12` 的累积净效果合并为单一 `V1__initial_baseline.sql`（= 最终 schema）。未来 schema 变更从 `V2` 续起。
- 以 `db/dbml/schema.dbml`（已基本为最终态）为**单一真源**：核对 DBML == 最终迁移态后，用 `dbml2sql` 重生成 `db/initdb/01_create_schema.sql`，并据同一 DBML 产出新的 `V1__initial_baseline.sql`，两侧同源。
- 对齐 `db/initdb/` 的 `*_seed.sql`（`21..46`、`88`）到最终 schema：去除对已删列/表（`rrn_encrypted`/`device_tokens`）的引用，补齐最终态表（如 `push_subscriptions`）所需种子，使种子在最终 schema 上直接可载。
- 更新 `FlywayMigrationTest` / `FlywayMigrationSmokeTest`：`flyway_schema_history` 现仅含单一 V1（BASELINE 或 SQL，按路径），删除对 `V2..V12` 的断言。
- 保留 `baseline-on-migrate=true` 双路径语义（生产空库跑 V1；demo/CI initdb 既有表则 V1 被 baseline 标记）。
- **不变**：业务 schema 语义、列/表/约束的最终形态、`ddl-auto=validate`、RRN 单向哈希等安全不变量。

## Capabilities

### New Capabilities
<!-- 无新能力 -->

### Modified Capabilities
- `data-platform`: 修改「Flyway manages production schema evolution; initdb is for demo/CI only」及相关 schema-source-alignment 需求——把「V1 baseline + V2..Vn 叠加演进」收敛为「单一 consolidated `V1__initial_baseline.sql` 为最终基线；`V2+` 仅供未来变更续起」。对应场景（fresh-prod 跑 V1..Vn、demo baseline 后叠 V2+、测试套件跑 V1..Vn、DBML 随迁移 reconcile）更新为单一 V1 终态。schema 的最终形态与 DBML-as-truth、initdb=demo/CI、生产 schema-only 经 Flyway 等核心约束**不变**。

## Impact

- **代码/文件**：`backend/src/main/resources/db/migration/V1..V12`（删 V2..V12、重写 V1）、`db/dbml/schema.dbml`（核对/补齐最终态）、`db/initdb/01_create_schema.sql`（重生成）、`db/initdb/*_seed.sql`（对齐最终 schema）、`backend/src/test/.../FlywayMigrationTest.java` + `.../smoke/FlywayMigrationSmokeTest.java`。
- **验证连锁**：`db/initdb` 是集成测试 fixture（`BaseIntegrationTest` 整目录挂进 Testcontainer）；改动后须 `./gradlew cleanTest test` 全套件重跑验证绿（`test` 输入不含 `db/initdb`，否则判 UP-TO-DATE）。JPA `ddl-auto=validate` 会即时暴露 schema 与实体不符。
- **环境**：仅影响 dev/demo/CI 的从零初始化与测试；无生产数据迁移风险（当前无生产；本机 `down -v` 重灌）。
- **不影响**：业务 API、前端、AI、Neo4j 联动（Neo4j 改造是独立 change DB-3）。
