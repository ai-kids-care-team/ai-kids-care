## 0. 维护者批准门（apply 前置，逐项批准 — 破坏性/部署变更，勿勾选）

> 本变更含**部署面 + loader 运行拓扑**破坏性改动，须维护者在 apply 前**逐项**批准；以下为前置门，
> 实现开始前不得勾选。

- [ ] 0.1 批准方案 B（水位增量轮询 + 周期对账，loader 长生命周期化），否决 A（backend 事件驱动，破坏
  single-writer invariant）与 C（CDC，基础设施过重）
- [ ] 0.2 批准 compose `data-loader` 运行拓扑变更：`restart: no` → `unless-stopped`（新增常驻服务，
  影响部署面与 watchtower 监控）
- [ ] 0.3 批准水位存储位置 = Neo4j meta-node（不新增 PG 表、无 Flyway 迁移）；若维护者选 PG 表方案，
  本变更范围需扩入一条 `V2` 迁移并另行 approval
- [ ] 0.4 确认删除语义（Open Question 3）：硬删除是否真实存在 → 决定对账兜底是 load-bearing 还是安全网

## 1. 水位 / 对账契约（先做，作为实现依据）

- [ ] 1.1 复用 `load_graph.py` 顶部既有非 PII 列白名单常量（节点 6 + Role + 4 关系源表）；确认增量
  `SELECT` 只比全量多取 `updated_at`（已在白名单内，非 PII），不引入任何新列
- [ ] 1.2 定义每个源表的 high-water mark 字段 = `updated_at`（assignment/relationship 表确认有
  `updated_at`）；HAS_TEACHER 由 `Teacher.kindergarten_id` 导出，随 Teacher 行变更重建该 KG 子集
- [ ] 1.3 定义水位 meta-node `(:_GraphSyncState {table, watermark})` schema；确认其属性键仅
  `table`/`watermark`（非 PII），纳入加载后 PII 核验范围

## 2. 增量 sync 实现

- [ ] 2.1 新增 `incremental_tick()`：每表 `SELECT <白名单列> FROM t WHERE updated_at >= :wm` →
  现有 `UNWIND … MERGE` upsert（节点按 id 键、关系按现状属性集）→ 推进水位到本批 `max(updated_at)`
  （边界 `>=` + 幂等 MERGE 防同秒丢行）
- [ ] 2.2 新增 `reconcile_deletes()`：每 `RECONCILE_EVERY` tick 取各表 live id 集合，
  `MATCH (n:Label) WHERE NOT n.<idKey> IN $liveIds DETACH DELETE n` 清孤儿；关系按源表存在性对账
- [ ] 2.3 软删除经 `updated_at` 增量被 upsert 捕获、节点 `status` 更新并保留（与现状一致），无需特殊分支
- [ ] 2.4 bootstrap 分支：空图 / 无水位 → 跑现有 `DETACH DELETE` 全量重建 + 初始化各表水位为
  `max(updated_at)`；非空 + 有水位 → 进入稳态轮询
- [ ] 2.5 loader 入口改长生命周期 `while True: incremental_tick(); periodic reconcile; sleep(POLL_INTERVAL)`；
  复用 psycopg2 连接（合理生命周期管理，勿引 psycopg3）、复用 `neo4j_connect.py`
- [ ] 2.6 失败语义：tick 内 PG/Neo4j 异常 → 记录、**不推进该表水位**、下 tick 重试（MERGE 幂等），不
  吞错前进；持续不可达的退避/退出阈值按 Open Question 7 实现

## 3. 编排与脚本收口

- [ ] 3.1 `config.py` 增 `POLL_INTERVAL_SEC` / `RECONCILE_EVERY` env（带合理默认，如 30s / 每 tick）
- [ ] 3.2 根 `docker-compose.yml` `data-loader`：`restart: no` → `unless-stopped`，注入轮询 env；保留
  `depends_on: db service_healthy + neo4j service_healthy`（**部署面变更，须 0.2 批准**）
- [ ] 3.3 `run_all.sh`：仍先跑 `no000_scrub_sensitive.py`（防御）→ 启动长生命周期 sync（替换原跑完即退）
- [ ] 3.4 确认 `docker-compose.cd.yml` watchtower 监控面覆盖常驻 `data-loader`（已监控全栈，确认即可）

## 4. 文档与 spec 同步

- [ ] 4.1 同步 `openspec/specs/data-platform/spec.md`（经本 change 的 delta apply：MODIFY 两条 + ADD 一条）
- [ ] 4.2 重写 `db/ne4j_kindergartens/readme.md` 与 `SETUP_GUIDE.md`（描述增量 sync + bootstrap + 对账，
  删 one-shot「重跑才更新」措辞）
- [ ] 4.3 更新根 `README.md` / `README.en.md` / `README.zh-CN.md` 的 Neo4j 数据加载器条目（标注增量 sync）

## 5. 验证

- [ ] 5.1 本机 `docker compose up -d db neo4j` → 起 data-loader → bootstrap 全量重建成功、各表水位初始化
- [ ] 5.2 增量验证：PG `INSERT` 新 child / `UPDATE` status / 物理 `DELETE` 一行 → 下一 tick 内图收敛；
  节点/关系 count 与 PG `SELECT count(*)` 一致
- [ ] 5.3 删除验证：软删（status 迁移）节点保留且 status 更新；硬删行经对账被 `DETACH DELETE` 清除、无孤儿
- [ ] 5.4 PII 不投影核验：`MATCH (n) UNWIND keys(n) AS k RETURN DISTINCT k`（含 `_GraphSyncState`）无任一
  禁列；增量 `SELECT` 列集 = 白名单（人工核对）
- [ ] 5.5 跑 `LoaderPiiProjectionGuardTest`（DooD 单类）：找到 loader `.py`、0 违规
- [ ] 5.6 `openspec validate neo4j-incremental-sync --strict` 通过
