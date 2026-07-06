---
globs: db/**
disclosure: path-scoped
---

# DB 层约定（`db/`）

## 数据存储三分

- **PostgreSQL** 权威（system-of-record）：JPA `validate` + Flyway 单一 `V1__initial_baseline.sql` 基线〔DB-1 已 squash V1–V12，未来从 **V2** 续起〕，`db/initdb/01_create_schema.sql` 与 baseline 同源自 `db/dbml/schema.dbml`，靠 `baseline-on-migrate` 协调两条装配路径。
- **Neo4j** 是关系图**只读派生副本**（原生 Driver + Cypher，靠 compose `data-loader` 从 PG 装填，**不含 PII**）。
- **Redis** 管 Spring Session（租户上下文载体）+ 登录限流，无业务缓存。

## seed 即集成测试 fixture

**改 `db/initdb/` 任何 seed 后必须 `./gradlew cleanTest test`**——seed 整目录即 testcontainer 集成测试 fixture（`BaseIntegrationTest` 用 `withCopyFileToContainer`），但不在 `test` task 输入，否则被判 UP-TO-DATE 不重跑。
