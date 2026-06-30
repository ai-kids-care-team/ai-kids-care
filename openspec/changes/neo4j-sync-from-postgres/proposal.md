## Why

Neo4j 是 PostgreSQL 的只读派生关系图（system-of-record 在 PG）。但当前 `db/ne4j_kindergartens/`
的 data-loader **并不读 PG**：12 个 `noXXX_insert_*.py` / `db100_insert_users.py` 脚本从 `data/*.csv`
**静态快照**建图，`config.py` 里早已注入的 PG 连接变量（`DB_HOST/PORT/NAME/USER/PASSWORD`，compose
已传入）形同虚设。后果：① 图与 PG **天生不一致**——CSV 是某次导出的化石，PG seed 经治理（DB-1/seed
重建）后两侧漂移；② 维护一堆并行 CSV + 注入脚本是纯负担；③ PII 防线靠 `no000_scrub_sensitive.py`
**先把含 PII 的 CSV 灌进去、再 REMOVE** 的事后补救，而非源头不投影。趁当前无生产环境窗口期，把派生
图的数据源从 CSV 化石**切回真正的 system-of-record（PG）**，让图真正"和 PG 数据联动"。

## What Changes

- **BREAKING（loader 内部架构，对外契约不变）**：data-loader 从「读 `data/*.csv` 静态快照」改为
  **一次性查询 PostgreSQL 直接建图**（one-shot PG→Neo4j ETL）。Neo4j 仍是只读派生副本、仍单次运行
  （`restart: no`），SSE/backend/GraphRepository 等下游不受影响。
- **删除** `db/ne4j_kindergartens/data/` 下全部 10 个 CSV（化石快照）。
- **删除** 12 个 CSV 注入脚本（`no100/no200/no300/no400/no500/no600/no700/no800/no900/no950/no1000_*.py`
  + `db100_insert_users.py`），整合为单一 PG-sourced ETL（节点 + 关系一遍建完）。
- **保留并强化 PII 不投影 invariant（INC-003）**：新 ETL 从 PG **只 SELECT 白名单内的非 PII 列**
  （源头不投影，而非事后 scrub）；`no000_scrub_sensitive.py` 作为幂等防御（针对历史残留节点）保留并
  在 ETL 起始先跑。`LoaderPiiProjectionGuardTest` 守卫继续生效（源码扫描 loader `*.py`，与运行架构正交）。
- **新增依赖**：loader `requirements.txt` 增加 PostgreSQL 驱动（`psycopg`）。
- **同步文档/编排**：`docker-compose.yml` 的 `data-loader`（依赖、env）、3 个 README、`run_all.sh`、
  `db/ne4j_kindergartens/SETUP_GUIDE.md`/`readme.md` 随之更新。

## Capabilities

### New Capabilities
<!-- 无新增能力；本变更修改既有 data-platform 能力下的 loader 行为 -->

### Modified Capabilities
- `data-platform`: 修改两条 loader 相关需求的 spec 行为——
  - **「Neo4j loader MUST NOT project S0 or PII fields (INC-003)」**：场景从「逐个 `noXXX` 脚本的
    `MERGE…SET` 省略 PII」改写为「单一 PG-sourced ETL 以非 PII 列白名单 SELECT，源头不投影」；保留
    scrub 防御场景。
  - **「Neo4j sync SHALL be one-shot」**：数据源从 `data/*.csv` 静态快照改为查询 PostgreSQL；一次性
    `restart: no`、`depends_on: db healthy + neo4j started`、运行后退出的语义不变，但明确「图反映的是
    **loader 运行时刻的 PG 状态**」而非某次 CSV 导出。

## Impact

- **代码**：`db/ne4j_kindergartens/`（删 CSV + 12 注入脚本、新增 ETL 模块、改 `requirements.txt`/
  `run_all.sh`/`config.py` 复用、保留 `no000_scrub_sensitive.py`/`neo4j_connect.py`）。
- **编排**：`docker-compose.yml` `data-loader` 服务（已有 PG env，确认 `depends_on db healthy` 顺序）。
- **测试**：`LoaderPiiProjectionGuardTest`（源码扫描，应继续 0 违规）；新增/调整一处 loader 行为说明。
- **文档**：`README.md` / `README.en.md` / `README.zh-CN.md` 中描述 CSV-loader 的段落、
  `db/ne4j_kindergartens/SETUP_GUIDE.md` / `readme.md`、`openspec/specs/data-platform/spec.md`。
- **不影响**：PG schema（DB-1 已收口）、backend Java、frontend、AI 推理；GraphService 仍 `denyAll()` 休眠。
- **Non-goals**：① 不实现增量/实时 sync（仍是 one-shot，重跑才 re-sync）；② 不唤醒 GraphService /
  不改图查询 API；③ 不改 Neo4j 图模型（节点标签/关系类型/属性集不变，仅换数据源）；④ 不动 PG schema 或 seed。
