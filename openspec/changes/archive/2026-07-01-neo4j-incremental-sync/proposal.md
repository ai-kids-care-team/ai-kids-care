## Why

DB-3（archive `2026-06-30-neo4j-sync-from-postgres`）已把派生图的数据源从 CSV 化石切回
PostgreSQL，但仍是**一次性 ETL**（`run_all.sh` = `no000_scrub_sensitive.py` → `load_graph.py`，
`MATCH (n) DETACH DELETE n` 全清重建，compose `data-loader` `restart: no` 跑完即退）。其后 PG
的任何业务写入，图都**不会自动反映**——必须人工重跑 loader 才 re-sync（现有 spec 明文
"Graph data becomes stale after a later PostgreSQL write"）。

这在 DB-3 时是可接受的：`GraphService` 当时 `@PreAuthorize("denyAll()")` 休眠，图陈旧无人可感知。
但 **P3 已唤醒图查询**——`GET /api/v1/graph/children/{childId}` 与 `/api/v1/graph/teachers/{teacherId}`
已对教职员上线（data-platform spec "Graph query API is reachable" 等需求已 apply）。于是「图陈旧」
从一个隐性技术债**变成了可感知的产品缺陷**：新入园儿童、班级调整、监护关系变更在重跑 loader 前，
教职员在关系图上看到的是旧状态。本提案把派生图从「重跑才更新」升级为「持续与 PG 收敛的**增量
sync**」，让 P3 的图读 API 反映接近实时的 PG 状态。

## What Changes

- **BREAKING（部署面 + loader 运行拓扑，对外读契约不变）**：data-loader 从「one-shot 跑完退出」
  改为**长生命周期增量 sync**——以水位（high-water mark）轮询 PG `updated_at` 增量、`MERGE` upsert
  进 Neo4j，并周期性 reconcile 删除/孤儿。Neo4j 仍是**只读派生副本**，**loader 仍是 Neo4j 唯一写入者**
  （不把图写权移进 backend），`GraphRepository` / `GraphService` / 前端读路径**零感知**。
- **保留 one-shot 全量重建作为 bootstrap**：空图 / 无水位时回退到现有 `DETACH DELETE` 全量重建
  并初始化水位；增量是稳态路径。两者共用同一套白名单 SELECT 与 Cypher MERGE。
- **显式处理删除传播**（增量无法像 `DETACH DELETE` 全清那样天然清理）：① 软删除（`status`
  迁移）经 `updated_at` 扫描被 upsert 捕获、节点 status 更新；② 硬删除 / 关系行移除靠周期性
  **全量 id 对账（anti-join）** `MATCH (n:Label) WHERE NOT n.<id> IN $liveIds DETACH DELETE n`
  清理孤儿；关系同理。
- **保留并强化 INC-003 白名单不投影**：增量 SELECT 与全量 SELECT 用**同一份非 PII 列白名单**；
  PII 列不进 SQL 结果、不进 Python 行、无从绑定 Cypher。`no000_scrub_sensitive.py` 防御层与
  `LoaderPiiProjectionGuardTest` 源码扫描守卫继续生效。
- **复用 psycopg2**（不引 psycopg3）、复用既有 `config.py` env 与 `neo4j_connect.py`。
- **同步编排/文档**：根 `docker-compose.yml` 的 `data-loader` 服务（`restart` 策略、轮询间隔 env）、
  `docker-compose.cd.yml`（watchtower 监控面）、`db/ne4j_kindergartens/` README/SETUP_GUIDE、
  `openspec/specs/data-platform/spec.md`。

## Capabilities

### New Capabilities
<!-- 无新增 capability；修改既有 data-platform 能力下 loader 的 sync 语义。 -->

### Modified Capabilities
- `data-platform`:
  - **MODIFIED「Neo4j sync …（原 one-shot, no incremental）」** → 改为「增量 sync 使派生图持续
    与 PG 收敛；one-shot 全量重建退为 bootstrap」；删除「写后图陈旧、须重跑」场景，替换为「写后
    图在轮询间隔内收敛」。
  - **MODIFIED「Neo4j loader MUST NOT project S0 or PII fields (INC-003)」** → 明确白名单 SELECT
    同时约束**全量重建与增量水位拉取两条路径**，新增一条增量 SELECT 白名单场景。
  - **ADDED「Incremental sync propagates deletes and tolerates missed updates」** → 规定水位推进/
    重启恢复、软删除 upsert、硬删除对账清孤儿、bootstrap 全量重建语义。

## Impact

- **代码**：`db/ne4j_kindergartens/load_graph.py`（增量水位 + 对账 + bootstrap 分支）、`run_all.sh`
  / 新增 entrypoint（长生命周期循环）、`config.py`（轮询间隔 / 对账周期 env）、`no000`/`neo4j_connect.py`
  保留。
- **编排**：根 `docker-compose.yml` `data-loader`（`restart: no` → `unless-stopped`，注入轮询间隔
  env）；`docker-compose.cd.yml`（watchtower 监控范围已含全栈，确认即可）。
- **测试**：`LoaderPiiProjectionGuardTest`（源码扫描，应继续 0 违规）；建议补 loader 单元/集成验证
  增量 + 对账行为。
- **文档**：`db/ne4j_kindergartens/readme.md` / `SETUP_GUIDE.md`、根 README 三语 loader 条目、
  `openspec/specs/data-platform/spec.md`。
- **不影响**：PG schema（推荐方案不加 PG 表 → 无 Flyway 迁移）、backend Java、frontend、AI 推理；
  `GraphService` / `GraphRepository` 读路径与 VO 映射不变；Neo4j 图模型（节点标签/关系类型/属性集）不变。
- **Non-goals**：① 不改图语义/节点集/关系集（仅换"何时写"，不换"写什么"）；② 不碰 `GraphService`
  读路径、图查询 API、前端 reagraph；③ 不把 Neo4j 写权移进 backend（loader 仍是唯一写入者，
  "writes to Neo4j performed only by data-loader" invariant 保持）；④ 不改 PG schema / seed；
  ⑤ 不做多实例 sync 去重（单写入者假设，多实例锁是 follow-up，类比 SSE fanout）；⑥ 不引入
  CDC / 逻辑复制 / Kafka 等重型基础设施。
