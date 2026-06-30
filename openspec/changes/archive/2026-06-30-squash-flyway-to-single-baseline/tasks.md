## 1. 建立权威终态基准（实证 DBML 正确性）

- [x] 1.1 在 PostgreSQL Testcontainer（或本机 docker pg）上按序跑现有 `V1__initial_baseline.sql … V12__*.sql`，dump 终态结构（表/列/类型/nullability/约束/索引/enum）为基准文件
- [x] 1.2 用 `dbml2sql db/dbml/schema.dbml` 生成候选 schema，与 1.1 基准做**结构 diff**
- [x] 1.3 据 diff 核对/补齐 `db/dbml/schema.dbml`，直到 `dbml2sql` 输出与 1.1 终态结构等价（只求结构等价，不求文本逐字一致）

## 2. 重生成单一基线

- [x] 2.1 `dbml2sql db/dbml/schema.dbml -o db/initdb/01_create_schema.sql` 重生成 initdb schema
- [x] 2.2 据同一 DBML 产出新的 `V1__initial_baseline.sql`（最终态，替换旧 941 行快照），更新文件头注释为"consolidated baseline（squash V1..V12）"
- [x] 2.3 `git rm` `backend/src/main/resources/db/migration/V2__*.sql … V12__*.sql`
- [x] 2.4 确认 `application.yml` 的 `flyway.baseline-on-migrate=true` / `baseline-version=1` 与单一 V1 语义一致（无需改值，复核注释）

## 3. 对齐 seed 与测试断言

- [x] 3.1 `./gradlew cleanTest test`（DooD：挂仓库根 + `TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal` + Ryuk 关），收集首轮失败
- [x] 3.2 按失败逐个补齐 `db/initdb/*_seed.sql`（`21..46`、`88`）到终态 schema：满足新增 NOT NULL 列（如 `detection_events.dedup_key`）、保持 spec「test-anchor invariants」（admin/user_id=1、kg {1,2,3}、负样本 room 等）
- [x] 3.3 更新 `FlywayMigrationTest` 与 `smoke/FlywayMigrationSmokeTest`：`flyway_schema_history` 仅断言单一 V1（BASELINE/SQL），删除对 `V2..V12` 的断言
- [x] 3.4 复核 data-platform 能力测试（schema 结构不变量）在 V1 终态下仍断言：`push_subscriptions` 存在且无 `device_tokens`/`device_platform_enum`、`rrn_hash` NOT NULL 无 `rrn_encrypted`、`detection_events.dedup_key` + `uq_detection_events_dedup` 存在、`notifications.sent_at`/`fail_reason` 可空
- [x] 3.5 迭代 3.1–3.4 直到 `./gradlew cleanTest test` 全套件绿

## 4. 端到端冒烟与收口

- [x] 4.1 本地 `docker compose down -v && docker compose up -d --build`：验证 dev 路径（initdb 建表 → backend 启动 Flyway baseline V1 → `ddl-auto=validate` 通过）起得来
- [x] 4.2 冒烟登录（demo `admin123`，CSRF 流程）确认 seed 可用、关键页面可达
- [x] 4.3 复核 `data-platform` spec delta 与实现一致；运行 `openspec validate`（如可用）
- [x] 4.4 提交（develop trunk）；变更说明列明删除 V2..V12、重生成 V1/initdb、seed/测试对齐
