## Context

`db/ne4j_kindergartens/` 的 data-loader 当前是一组按实体拆分的脚本：`run_all.sh` 依次跑
`no000_scrub_sensitive.py` → 11 个 `noXXX_insert_*.py`（+ `db100_insert_users.py`）→
`no1000_create_relationships.py`。这些 insert 脚本**全部从 `./data/*.csv` 读取静态快照**
（`csv.DictReader`），用 `MERGE … SET` 建节点/关系。`config.py` 已声明 PG 连接变量、`docker-compose.yml`
的 `data-loader` 服务也已注入 `DB_HOST/PORT/NAME/USER/PASSWORD` 并 `depends_on: db service_healthy`
——**但没有任何脚本实际连接 PG**，PG 配置是历史遗留的空摆设。

PG 是 system-of-record，Neo4j 是只读派生关系图（`GraphRepository` 原生 Cypher 查询，GraphService 当前
`@PreAuthorize("denyAll()")` 休眠）。当前架构下，图反映的是某次 CSV 导出的化石，与经治理后的 PG seed
（DB-1 schema squash + seed 重建）**结构性漂移**。本设计把派生图的数据源从 CSV 切回 PG，使其名副其实地
"和 PG 联动"。

INC-003 安全约束：loader 绝不能把 S0/PII 字段（`password_hash`/`rrn_*`/`birth_date`/`address`/`email`/
`phone`/`emergency_contact_*`/`contact_*`/`stream_password_*`）投影进图节点。当前靠 insert 脚本的 SET
子句"恰好不写 PII" + `no000_scrub_sensitive.py` 事后 REMOVE 双保险，并由 `LoaderPiiProjectionGuardTest`
（纯源码扫描）守卫。

## Goals / Non-Goals

**Goals:**
- data-loader 一次性查询 PostgreSQL 直接建图，退役 `data/*.csv` 化石与 CSV 注入脚本群。
- 保持 Neo4j 图模型不变（节点标签、关系类型、属性集与现状逐字段对齐），下游 `GraphRepository` 零感知。
- INC-003 从"事后 scrub 补救"升级为"源头白名单 SELECT 不投影"，scrub 退为防御层；守卫测试继续 0 违规。
- one-shot 语义不变（`restart: no`、跑完退出），但数据源是"运行时刻的 PG 状态"。

**Non-Goals:**
- 不做增量/CDC/实时 sync（仍是 one-shot；PG 后续写入需重跑 loader 才反映）。
- 不唤醒 GraphService、不改图查询 API、不改前端 reagraph。
- 不改 PG schema / seed（DB-1 已收口）、不改 backend Java。

## Decisions

### D1：把 11+1 个 CSV insert 脚本整合为单一 PG-sourced ETL（`load_graph.py`）
`run_all.sh` 改为两步：`python no000_scrub_sensitive.py`（防御）→ `python load_graph.py`（建图）。
`load_graph.py` 在一个进程内：连接 PG → 按节点（User/Kindergarten/Teacher/Class/Child/Guardian）→
关系（HAS_TEACHER/HAS_CLASS/HAS_CHILD/HAS_GUARDIAN）顺序，逐实体从 PG 查询白名单列、写 Neo4j。
- **为何整合而非逐个改写**：用户明确要"不再需要这一堆脚本"；单进程单 PG 连接、统一事务/批处理、
  一处白名单更易守。逐个改写会留下 12 个近乎重复的文件。
- **替代方案**：保留 per-entity 脚本仅把 CSV 源换成 PG —— 否决（仍是"一堆脚本"，违背诉求）。
- 删除：`data/`（10 CSV）、`no100/no200/no300/no400/no500/no600/no700/no800/no900/no950/no1000_*.py`、
  `db100_insert_users.py`。保留：`no000_scrub_sensitive.py`、`neo4j_connect.py`、`config.py`、`run_all.sh`、
  `Dockerfile`、`requirements.txt`。

### D2：节点/关系属性集 = 当前图的逐字段镜像，PII 列永不进 SELECT
新 ETL 的每个实体用**显式列白名单**从 PG 查询，白名单逐字段抄录自当前 insert 脚本的 `SET` 子句，
确保图形状不变：
- `User`: user_id, login_id, status, last_login_at, created_at, updated_at（PG `users`）
- `Kindergarten`: kindergarten_id, name, region_code, code, business_registration_no, contact_name,
  status, created_at, updated_at（PG `kindergartens`；**不取** address/contact_phone/contact_email）
- `Teacher`: teacher_id, kindergarten_id, user_id, staff_no, name, gender, level, start_date, end_date,
  status, created_at, updated_at（**不取** rrn_*/emergency_contact_*）
- `Class`: 当前 `no400` 的非 PII 列（class_id, kindergarten_id, name, … 实现时逐字段对齐）
- `Child`: child_id, kindergarten_id, name, child_no, gender, enroll_date, leave_date, status,
  created_at, updated_at（**不取** rrn_*/birth_date/address）
- `Guardian`: 当前 `no600` 的非 PII 列（guardian_id, kindergarten_id, name, … ；**不取** rrn_*/address）
- 关系属性沿用现状：HAS_CLASS{assignment_id,role,start/end_date,reason,note,status,created_by_user_id,…}、
  HAS_CHILD{assignment_id,…}、HAS_GUARDIAN{relationship,is_primary,priority,start/end_date,…}，分别源自
  PG `class_teacher_assignments` / `child_class_assignments` / `child_guardian_relationships`。
- **白名单是 INC-003 主防线**：列在 SQL `SELECT` 处即被允许集合限定，PII 列根本不进 Python 行字典，
  Cypher 参数也就无从绑定。`LoaderPiiProjectionGuardTest` 源码扫描 + `no000` scrub 为二、三层防御。

### D3：清空后重建（DETACH DELETE all → load），保证图严格反映当前 PG
ETL 起始 `MATCH (n) DETACH DELETE n` 全清，再建。Neo4j 是纯派生副本、无任何权威数据，全清安全；
相比当前"MERGE-only + 仅删 tree 关系"，全清能消除 PG 已删实体在图中残留为孤儿节点的问题，使一次 sync
后图与 PG 完全一致。节点仍按 id 键 MERGE/CREATE，唯一约束（`*_id IS UNIQUE`）保留。

### D4：PG 驱动用 psycopg(v3)
`requirements.txt` 增 `psycopg[binary]`。原生 datetime/date 类型由驱动直接返回，**省去** CSV 路径里
`fix_datetime`/`fix_date` 字符串补丁逻辑，Neo4j Python driver 也原生接受 temporal 类型。
- **替代**：psycopg2 —— 否决（v3 是当前主线，binary wheel 装载更省事）。

### D5：失败即非零退出，依赖顺序确保 PG 就绪
`load_graph.py` 任一步异常 → 进程非零退出（compose 里 `data-loader` 显红，便于发现），不吞错继续。
`data-loader` 已 `depends_on: db service_healthy`（确认保留）；连接失败/空库直接报错而非静默建空图。

## Risks / Trade-offs

- **[一次性快照仍会过期]** → 非本变更目标（增量 sync 是 follow-up）；保留 spec "one-shot/stale after write"
  语义，文档明确"重跑才 re-sync"。
- **[全清重建期间图短暂为空]** → loader 是 one-shot 启动任务、Neo4j 派生只读、GraphService 休眠，无在线读
  受影响；可接受。后续若上线图查询，需评估在事务/单独 db 内重建或加载完原子切换。
- **[白名单漏列某非 PII 列 → 图少属性]** → 实现时逐字段对照当前 SET 子句；验证步用 Cypher 抽样比对节点
  属性键集合。**[白名单误纳 PII]** → `LoaderPiiProjectionGuardTest` 源码扫描 + `no000` scrub 双兜底。
- **[本机无 Java，守卫测试需 DooD]** → guard test 是纯源码扫描，按既有 DooD 配方（容器 `gradle`、挂仓库根、
  `TESTCONTAINERS_HOST_OVERRIDE`、Ryuk 关）可单类跑；ETL 端到端用本机 docker 起 PG+Neo4j 实跑 + Cypher 核验。

## Migration Plan

1. 写 `load_graph.py`（PG→Neo4j ETL，含 D2 白名单 + D3 全清重建）、改 `run_all.sh`（scrub→load_graph）、
   `requirements.txt`（+psycopg）。
2. 删 `data/*.csv` 与 12 个 CSV 注入脚本。
3. 同步 `docker-compose.yml` `data-loader`（确认 env/depends_on）、3 README、`SETUP_GUIDE.md`/`readme.md`。
4. 改 `openspec/specs/data-platform/spec.md` 两条需求（经 delta spec apply）。
5. 验证：`docker compose up -d db neo4j` → 跑 data-loader → Cypher 核验：① 各节点 count 与 PG `SELECT count`
   一致；② 全图无任一 PII 属性键（`MATCH (n) UNWIND keys(n) AS k RETURN DISTINCT k` 不含禁列）；③ 五段
   关系路径连通。再跑 `LoaderPiiProjectionGuardTest`（DooD）应 0 违规。
6. **回滚**：本变更只动 loader 子目录 + 文档 + spec；回滚 = `git revert`，CSV 路径整体复原，无 schema/数据风险。

## Open Questions

无（方向已由维护者 2026-06-30 拍板：one-shot PG→Neo4j ETL、删 CSV+脚本、保 PII scrub 守卫）。
