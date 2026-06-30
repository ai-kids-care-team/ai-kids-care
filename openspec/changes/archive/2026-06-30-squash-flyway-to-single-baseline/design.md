## Context

数据库 schema 当前由两条**同源但已分叉**的装配路径维护：

- `backend/src/main/resources/db/migration/V1__initial_baseline.sql`（941 行）—— ADR-0012(2026-06-08) 时期的 V1 快照，仍含 `rrn_encrypted` 列、`device_tokens` 表等旧形态。
- `db/initdb/01_create_schema.sql`（1255 行）—— 同一时期的 dbml 生成快照，同样陈旧。
- `V2__*.sql … V12__*.sql` —— 把上述 V1 旧基线演进到**最终态**（rrn_hash 强制 + 删 rrn_encrypted、device_tokens→push_subscriptions、quiet_hours、detection_events.dedup_key、notification_delivery_attempts 等）。

最终真实 schema = `V1 + V2..V12`。`db/dbml/schema.dbml`（746 行）已基本是最终态（含 push_subscriptions/dedup_key、无 device_tokens/rrn_encrypted），是离"最终真相"最近的单一设计源。

生产经 Flyway 在空库跑 V1..Vn；dev/CI 经 initdb 建表后 `baseline-on-migrate=true` 标记 V1、再叠 V2..Vn。`db/initdb` 整目录是集成测试 fixture（`BaseIntegrationTest` 挂进 Testcontainer）。**当前无生产环境**，无历史 `flyway_schema_history` 需兼容。

## Goals / Non-Goals

**Goals:**
- 把 `V1..V12` 折叠为单一 `V1__initial_baseline.sql`（最终态），删除 `V2..V12`，未来从 `V2` 续起。
- 以 `db/dbml/schema.dbml` 为单一真源，`dbml2sql` 重生成 `db/initdb/01_create_schema.sql` 与 `V1__initial_baseline.sql`，两侧同源收敛到同一最终 schema。
- 全套后端集成测试在新基线上 `cleanTest` 重跑绿；`ddl-auto=validate` 与实体对齐。

**Non-Goals:**
- 不改任何业务 schema 语义/最终形态（只 squash，不重设计列/约束）。
- 不动 Neo4j 联动（独立 change DB-3）。
- 不引入生产数据迁移（当前无生产；不处理既有 `flyway_schema_history`）。

## Decisions

**D1：以 DBML 为真源重生成，而非手工合并 V1..V12。**
理由：仓库既有 `data-platform` spec 明确 DBML-first（`dbml2sql` 生成 initdb），工具链（`db/scripts/`、`@dbml/cli`）已在；手工合并 12 个迁移的净效果易漏改、与 DBML 漂移。代价：须先确证 DBML == V1..V12 终态（见 D2）。
备选：手工把 V2..V12 净效果并进 V1 —— 否决（高漏改风险、丢失 DBML 单源）。

**D2：先"实证对齐"再生成——用 V1..V12 跑出的终态 schema 作为校验基准。**
先在 PostgreSQL Testcontainer 上跑现有 `V1..V12` 得到**权威终态**，dump 其结构；再 `dbml2sql(schema.dbml)` 生成候选 schema，结构 diff，对齐 `schema.dbml` 直到两者等价。这样 DBML 的正确性被现有迁移链背书，避免凭空相信 DBML 已是终态。

**D3：复用 `V1__initial_baseline.sql` 文件名，重写其内容为最终态；删除 `V2..V12`。**
理由：保持"单一 V1 基线"语义与 `baseline-version: 1`、`baseline-on-migrate` 配置不变。无生产历史，故 V1 checksum 变化无碍（dev/CI 从零重灌）。

**D4：seed 对齐以 `cleanTest` 全套件为硬门，按失败驱动最小改动。**
现状利好：`*_seed.sql` 不引用 `rrn_encrypted`/`device_tokens`。残余风险点是 V2..V12 新增的 NOT NULL 列（如 `detection_events.dedup_key`）在终态 initdb 下需种子提供值。不预先猜测，跑 `cleanTest` 让 `validate`/插入失败精确定位，逐个补齐。

## Risks / Trade-offs

- **[seed 与终态 NOT NULL 列不符 → initdb 载入/`validate` 失败]** → `./gradlew cleanTest test` 全套件为硬门；`db/initdb` 不在 `test` task 输入，必须 `cleanTest` 否则判 UP-TO-DATE（见 [[seed-is-integration-test-fixture]]、[[backend-test-dood-invocation]]）。
- **[DBML 并非真正终态，漏掉某 V 的细节]** → D2 的实证 diff 兜底：以现有 V1..V12 跑出的 schema 为基准，不信任 DBML 自述。
- **[dbml2sql 输出与手写 V1 风格/排序差异引入无意义 diff]** → 只关心结构等价（表/列/类型/约束/索引/enum），不追求文本逐字一致；用结构断言而非 textual diff 验收。
- **[本机无 Java]** → 后端测试走 DooD（挂仓库根 + `TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal`，见 [[backend-test-dood-invocation]]）；`dbml2sql` 用本机 node 的 `npx @dbml/cli` 或容器。
- **[FlywayMigrationTest/SmokeTest 仍断言 V2..V12 痕迹]** → 同步更新为单一 V1 断言，否则测试红。

## Migration Plan

1. 在 Testcontainer 跑现有 `V1..V12` → dump 权威终态结构（基准）。
2. 核对/补齐 `db/dbml/schema.dbml` 使 `dbml2sql` 输出与基准结构等价。
3. `dbml2sql` 重生成 `db/initdb/01_create_schema.sql`；据同一 DBML 产出新的 `V1__initial_baseline.sql`（最终态），`git rm` `V2..V12`。
4. `./gradlew cleanTest test`（DooD）→ 按失败补齐 seed / 修测试断言，迭代到全绿。
5. 本地 `docker compose down -v && up -d --build` 冒烟：dev 路径 initdb+baseline 起得来、登录通（demo `admin123`）。
- **回滚**：本 change 未归档前，`V2..V12` 仍在 git 历史；`git revert` 整个提交集即可恢复旧链。

## Open Questions

- 是否需要新增 `push_subscriptions` 的 seed？取决于现有通知/推送集成测试是否依赖既有订阅行——由步骤 4 的 `cleanTest` 结果裁定，不预设。
- `dbml2sql` 的具体执行载体（本机 `npx @dbml/cli` vs 容器）在 apply 时按本机可用性选定，不影响设计。
