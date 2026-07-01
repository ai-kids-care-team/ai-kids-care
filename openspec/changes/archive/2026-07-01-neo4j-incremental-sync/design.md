## Context

DB-3 后，`db/ne4j_kindergartens/load_graph.py` 是一次性 PG→Neo4j ETL：连接 PG（psycopg2 +
`RealDictCursor`）→ `MATCH (n) DETACH DELETE n` 全清 → 创建 7 个 `*_id`/`role_key` UNIQUE 约束 →
按非 PII 列白名单 `UNWIND … MERGE` 建节点（User/Kindergarten/Teacher/Class/Child/Guardian/Role）
→ 建关系（HAS_ROLE / HAS_TEACHER（由 `Teacher.kindergarten_id` 导出）/ HAS_CLASS / HAS_CHILD /
HAS_GUARDIAN）。`run_all.sh` 先跑 `no000_scrub_sensitive.py`（防御层 REMOVE 残留 PII）再跑
`load_graph.py`。compose `data-loader`（根 `docker-compose.yml`，**非** `ai/docker-compose.yml`）
`restart: no`、`depends_on: db service_healthy + neo4j service_healthy`，跑完退出。

PG 是 system-of-record；Neo4j 是只读派生图（`GraphRepository` 原生 Cypher）。data-platform spec
现有 invariant：**"writes to Neo4j are performed only by the data-loader, not by any application
service"**，且 **INC-003**：loader 绝不投影 S0/PII 列（白名单 SELECT 为主防线 + `no000` scrub 防御
+ `LoaderPiiProjectionGuardTest` 源码扫描守卫）。

P3 已唤醒 `GraphService`（`GET /api/v1/graph/children|teachers/{id}`），图陈旧成为产品可感缺陷。
本设计在**不改图模型、不改读路径、不动 PII 防线、不引重型基础设施**的前提下，让派生图持续与 PG 收敛。

## Goals / Non-Goals

**Goals:**
- 派生图在有界延迟内反映 PG 的业务写入（创建/更新/软删除/硬删除），无需人工重跑 loader。
- 保持 loader 为 Neo4j **唯一写入者**（system-of-record invariant 不破）、psycopg2 不变、节点/关系
  全集逐字段不变、白名单 SELECT 仍是 INC-003 主防线。
- 显式处理删除传播（增量丢了 `DETACH DELETE` 全清的天然清孤儿能力）。
- 保留 one-shot 全量重建作为 bootstrap / 灾难恢复路径。

**Non-Goals:**
- 不改 Neo4j 图模型（标签/关系/属性集），不碰 `GraphService` / `GraphRepository` / 前端 reagraph。
- 不把 Neo4j 写权移进 backend。
- 不改 PG schema / seed；推荐方案**不新增 PG 表**（无 Flyway 迁移）。
- 不做多实例 sync 去重（单写入者假设，跨实例锁是 follow-up，类比 SSE fanout 的 Redis pub/sub defer）。
- 不引入 Debezium / Kafka / 逻辑复制等 CDC 基础设施。

## Decision: 候选方案对比与推荐

### 候选 A — backend 应用事件驱动增量 upsert
backend 在每个 mutating service 提交后发 `GraphEntityChangedEvent`，`@Async @TransactionalEventListener(AFTER_COMMIT)`
监听器用 backend 既有 Neo4j Java Driver 把白名单字段 upsert 进图；删除事件发 `DETACH DELETE`。
- **优点**：近实时（提交即推）；复用 backend 现成 Spring 事件 + `@Async` + Neo4j Driver；backend
  是 PG 唯一写入者，是天然的变更 choke point。
- **缺点**：① **破坏 system-of-record invariant**——把 Neo4j 写权从 loader 移进 application service，
  需 MODIFY "writes to Neo4j performed only by data-loader" 这条核心约束；② 必须逐一插桩
  **每个** mutating 路径（User/Teacher/Class/Child/Guardian + 三张 assignment/relationship 表的
  CRUD），漏一条路径就静默漂移，且 HAS_TEACHER 由 `Teacher.kindergarten_id` 导出、HAS_ROLE 合成
  `role_key`，事件侧重算复杂；③ PII 投影面从「一处 Python 白名单」扩散到「多处 Java 映射」，
  `LoaderPiiProjectionGuardTest`（扫 `.py`）守不住 Java 写路径；④ 进程内事件 + 单实例假设，重启/
  漏发无补偿，需另加对账。**否决**：架构侵入最大、PII 守卫失效、与既有 invariant 冲突。

### 候选 B — 水位（updated_at）增量轮询 + 周期对账（**推荐**）
保持 Python loader 为唯一写入者，把它从 one-shot 改为**长生命周期循环**：每 tick 以每表
high-water mark 拉取 `WHERE updated_at >= :watermark` 的增量行，按现有白名单 `UNWIND … MERGE`
upsert；周期性做一次**全量 id 对账**清孤儿。空图/无水位时回退全量重建（bootstrap）。
- **优点**：① 架构改动最小——同一 loader、同一 psycopg2、同一白名单、同一 MERGE Cypher，
  **system-of-record invariant 与 INC-003 主防线原样保留**；② 无 backend 改动、无新 PG 表（水位
  存 Neo4j meta-node）；③ 对漏发/重启**自愈**（重扫水位之后的行，MERGE 幂等）；④ 删除有明确解法
  （见下）。
- **缺点**：① 延迟 = 轮询间隔（近实时，非即时）；② 同秒边界要 `>=` + 幂等 MERGE 防丢行；
  ③ 硬删除不被 `updated_at` 捕获 → 需对账兜底（见 D3）。
- 综合**最契合本仓库现状**（极小图、3 租户、已有 psycopg2/MERGE/白名单、loader 单写入者、
  无多实例压力），故**推荐 B**。

### 候选 C — PostgreSQL CDC（逻辑复制 / Debezium / 触发器 outbox）
捕获 WAL 级 INSERT/UPDATE/**DELETE**（含硬删除）流式推 Neo4j。
- **优点**：原生捕获删除、最低延迟、最完整。
- **缺点**：① 运维面过重——Debezium+Kafka 或复制槽管理，对一个微型只读派生副本是杀鸡用牛刀；
  ② 复制槽消费滞后会撑爆 WAL；③ **PII 风险更高**——原始 WAL 携带全部列，白名单要在下游过滤，
  远不如「源头 SELECT 白名单」可证；④ 引入新基础设施违背 Non-goals。**否决**：成本/收益严重失衡。

### 推荐：候选 B —— 水位增量轮询 + 周期对账，loader 长生命周期化

## Decisions（推荐方案细节）

### D1：loader 长生命周期循环，one-shot 全量重建退为 bootstrap
新增循环 entrypoint（或扩展 `load_graph.py` 的 `main`）：启动先判断「图是否为空 / 是否有水位」——
无 → 跑现有 `DETACH DELETE` 全量重建并把各表水位置为 `max(updated_at)`（bootstrap）；有 → 进入
`while True: incremental_tick(); sleep(POLL_INTERVAL)` 稳态循环。compose `data-loader`
`restart: no` → `unless-stopped`。`run_all.sh` 仍先跑 `no000_scrub_sensitive.py`（防御）。
- **替代**：保留 `restart: no` + 外部 cron/scheduler 周期重invoke 全量重建——否决（仍是全量、延迟取决
  于 cron、且每次全清期间图短暂为空，P3 在线读会闪空）。增量 upsert 不全清，读侧无空窗。

### D2：每表 high-water mark 增量拉取，MERGE 幂等 upsert
每 tick：对每个源表 `SELECT <白名单列> FROM t WHERE updated_at >= :wm ORDER BY updated_at`，
用**现有**白名单 + `UNWIND … MERGE` Cypher upsert（节点按 id 键、关系按现状属性集）；处理完把该表
水位推进到本批 `max(updated_at)`。边界用 `>=`（而非 `>`）+ MERGE 幂等，避免同秒多行丢失（重叠重放
安全）。HAS_TEACHER（由 `Teacher.kindergarten_id` 导出）在 Teacher 行变更时按现有 Cypher 重建该
KG 子集。所有表均有 `created_at/updated_at`（assignment 表有 `updated_at`/`granted_at`，已在 DB-3
白名单中确认）。
- **水位存储**：Neo4j meta-node `(:_GraphSyncState {table, watermark})`（只存表名+时间戳，非 PII；
  与派生数据同库，loader 跨重启无状态自恢复）。**替代**：新增 PG 表 `graph_sync_state`——否决（要
  Flyway V2 schema 变更，approval 更重，违 Non-goal「不改 PG schema」）。meta-node 须在
  `LoaderPiiProjectionGuardTest` / PII 核验中确认其属性键不含禁列（只有 `table`/`watermark`）。

### D3：删除传播 = 软删 upsert + 硬删周期对账（增量的关键缺口）
全量 `DETACH DELETE` 天然清掉 PG 已删实体；增量必须显式处理：
- **软删除**（`status` 迁移为 INACTIVE/WITHDRAWN/… ）：行 `updated_at` 被 bump → 被 D2 增量捕获 →
  节点 `status` 更新、**保留在图中**（与现状一致，节点本就携带 status）。
- **硬删除 / 关系行物理移除**：`updated_at` 观测不到（行已没了）→ 用**全量 id 对账**兜底：每隔
  `RECONCILE_EVERY` 个 tick，`SELECT <id> FROM t`（仅 id，极轻）取 live id 集合，
  `MATCH (n:Label) WHERE NOT n.<idKey> IN $liveIds DETACH DELETE n` 清孤儿；关系按其源表
  存在性同理对账。图极小（节点/关系各个位数十位数），对账可每 tick 跑亦无压力。
- **替代**：依赖 PG 触发器写 deletion outbox 表——否决（要 schema 变更 + 触发器，approval 重）。

### D4：复用 psycopg2，不引 psycopg3
沿用 DB-3 已验证的 `psycopg2-binary==2.9.9` + `RealDictCursor` + `UNWIND` 批量 MERGE 模式；
temporal 列 `value.isoformat()` 归一字符串（与既有图存储一致）。`requirements.txt` 不新增驱动。

### D5：INC-003 白名单是两条路径共用的单一真源
全量重建与增量拉取**共用同一份节点/关系列白名单常量**（`load_graph.py` 顶部已定义的
`USER_COLUMNS`/`KINDERGARTEN_COLUMNS`/… ）。增量 `SELECT` 也只取这些列 + `updated_at`（`updated_at`
本就在白名单内，非 PII），PII 列在两条路径都不进 SQL 结果。`no000` scrub（启动前防御）与
`LoaderPiiProjectionGuardTest`（源码扫描）继续守卫。

### D6：失败语义
增量 tick 内 PG/Neo4j 异常 → 记录并**不推进该表水位**（下个 tick 重试，MERGE 幂等），不吞错前进、
不静默漂移；连接彻底失败按既有 `sys.exit(1)` 思路决定是否退出由 restart 策略接管（长生命周期下倾向
退避重试而非立即退出，具体策略实现期定，见 Open Questions）。

## Risks / Trade-offs

- **[轮询延迟]** 稳态延迟 = `POLL_INTERVAL`（近实时非即时）→ 间隔可配；图小、对账廉价，可取较短间隔
  （如 30s，对齐 AI supervisor `STREAM_POLL_INTERVAL_SEC`）。
- **[同秒边界丢行]** → `>=` 水位 + 幂等 MERGE，重叠重放安全。
- **[硬删除漏清]** → D3 全量 id 对账兜底；需确认应用实际是否用硬删除（见 Open Questions），决定对账是
  load-bearing 还是安全网。
- **[长生命周期 loader 新增常驻进程]** → 部署面变更（compose `restart` 策略 + watchtower 监控面），
  须维护者批准；资源开销极小（周期轻量 SQL + 小批 MERGE）。
- **[多实例下重复 sync]** → 单写入者假设；多实例需锁（ShedLock 类）→ follow-up，本提案 Non-goal。
- **[白名单/meta-node 误纳 PII]** → `LoaderPiiProjectionGuardTest` 源码扫描 + `no000` scrub + 加载后
  `MATCH (n) UNWIND keys(n)` 核验（含 `_GraphSyncState`）三层兜底。
- **[本机无 Java，守卫测试需 DooD]** → guard test 纯源码扫描，按既有 DooD 配方单类跑；增量/对账行为用
  本机 docker 起 PG+Neo4j 实跑 + 改 PG 行后观测图收敛 + Cypher 核验。

## Migration Plan

1. 抽水位/对账逻辑：复用 `load_graph.py` 白名单常量与 MERGE Cypher，新增 incremental_tick / reconcile /
   水位读写（Neo4j meta-node）。
2. loader 入口改长生命周期循环（bootstrap 判定 → 稳态轮询）；`config.py` 加 `POLL_INTERVAL` /
   `RECONCILE_EVERY` env（带合理默认）。
3. 改根 `docker-compose.yml` `data-loader`：`restart: no` → `unless-stopped`，注入轮询 env；确认
   `docker-compose.cd.yml` watchtower 监控面。
4. 改 `openspec/specs/data-platform/spec.md`（本 change 的 delta apply）。
5. 同步 `db/ne4j_kindergartens/readme.md` / `SETUP_GUIDE.md` 与根 README 三语 loader 条目。
6. 验证：本机 `docker compose up -d db neo4j` → 起 data-loader（bootstrap）→ 改 PG（INSERT 新 child /
   UPDATE status / DELETE 一行）→ 观测下一 tick 内图收敛；Cypher 核验 ① 节点/关系 count 与 PG 一致
   ② 全图（含 meta-node）无 PII 属性键 ③ 硬删行被对账清除。再跑 `LoaderPiiProjectionGuardTest`（DooD）应 0 违规。
7. **回滚**：本变更只动 loader 子目录 + compose `restart`/env + 文档 + spec；回滚 = `git revert`
   恢复 one-shot；无 schema/数据风险（Neo4j 派生可重建）。

## Open Questions（留给维护者）

1. **运行拓扑**：loader 内常驻轮询循环（compose `restart: unless-stopped`，新增常驻服务）vs 保留
   `restart: no` + 外部 cron/scheduler 周期重invoke？影响部署面与 watchtower。推荐前者。
2. **可接受陈旧度 / 轮询间隔 SLA**：30s（对齐 AI supervisor）？还是更长？决定 `POLL_INTERVAL` 默认。
3. **硬删除是否真实存在**：应用是软删（`status`）为主，还是有物理 `DELETE`？决定 D3 对账是 load-bearing
   还是纯安全网（需与 backend 团队确认各实体删除语义）。
4. **水位存储位置**：Neo4j meta-node（推荐，无 PG 改动）vs 新 PG `graph_sync_state` 表（Flyway V2，
   approval 更重）？
5. **多实例**：若未来 loader/backend 多实例，谁拥有 sync？是否引 ShedLock 类锁？本提案倾向 defer（类比
   SSE fanout）。
6. **共存 vs 替换**：one-shot 全量重建保留为 bootstrap/灾备路径（推荐），还是彻底由增量取代？
7. **失败退避策略**：长生命周期下 PG/Neo4j 持续不可达时，退避重试 vs `sys.exit` 交由 restart 接管的
   阈值。
