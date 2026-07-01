## 0. 维护者批准门（apply 前置，逐项批准 — 破坏性/部署变更）

> 本变更含**部署面 + loader 运行拓扑**破坏性改动。以下批准门已由维护者授权（apply 授权决策
> 0.1–0.4，按推荐默认执行）。

- [x] 0.1 批准方案 B（水位增量轮询 + 周期对账，loader 长生命周期化），否决 A（backend 事件驱动，破坏
  single-writer invariant）与 C（CDC，基础设施过重）
- [x] 0.2 批准 compose `data-loader` 运行拓扑变更：`restart: no` → `unless-stopped`（新增常驻服务，
  影响部署面与 watchtower 监控）
- [x] 0.3 批准水位存储位置 = Neo4j meta-node（不新增 PG 表、无 Flyway 迁移）；若维护者选 PG 表方案，
  本变更范围需扩入一条 `V2` 迁移并另行 approval
- [x] 0.4 确认删除语义（Open Question 3）：**硬删除真实存在** → 对账兜底是 **load-bearing**（非纯安全网）。
  调查结论：图相关实体中 `ClassService.deleteClass` 执行物理 `repository.delete(entity)`（硬删 `classes` 行，
  且 FK 级联很可能连带物理删除其 assignment 行）；`child_guardian_relationships` 等关系行也由重指派物理移除。
  其余（User/Teacher/Guardian/UserRoleAssignment/Membership）为软删（`status` 条件 UPDATE）。故 `reconcile_deletes()`
  是传播硬删除的必需路径。

## 1. 水位 / 对账契约（先做，作为实现依据）

- [x] 1.1 复用 `load_graph.py` 顶部既有非 PII 列白名单常量（节点 6 + Role + 4 关系源表）；增量
  SELECT 列集与全量**完全一致**（仅在 WHERE 上加水位谓词，不新增任何列）
- [x] 1.2 定义每个源表的 high-water mark 字段。**发现**：`user_role_assignments` **无 `updated_at` 列**，
  改用 `GREATEST(granted_at, COALESCE(revoked_at, granted_at))`（两列均已在白名单、非 PII）；其余表 = `updated_at`。
  HAS_TEACHER 由 `Teacher.kindergarten_id` 导出，随 Teacher 行变更重建该 KG 子集。
  已知限制：`user_role_assignments` 的 PENDING→ACTIVE 纯状态转移不 bump 任何时间列 → 该状态变更在下次
  该行 granted/revoked 变化或全量重建前不被增量捕获（详见报告；不影响本 change 核心增删改语义）。
- [x] 1.3 定义水位 meta-node `(:_GraphSyncState {table, watermark})` schema；属性键仅 `table`/`watermark`
  （非 PII），已纳入加载后 PII 核验范围并实测 0 违规

## 2. 增量 sync 实现

- [x] 2.1 新增 `incremental_tick()` + `_sync_one()`：每表 `SELECT <白名单列> FROM t WHERE <wm_expr> >= :wm` →
  现有 `UNWIND … MERGE` upsert → 推进水位到本批 `max`（边界 `>=` + 幂等 MERGE 防同秒丢行）
- [x] 2.2 新增 `reconcile_deletes()`：取各表 live id 集合，节点 `MATCH (n:Label) WHERE NOT n.<idKey> IN $liveIds
  DETACH DELETE n` 清孤儿；关系按 role_assignment_id / assignment_id /(child_id,guardian_id) 对账 +
  孤儿 Role 节点清理 + HAS_TEACHER 换园过时边清理
- [x] 2.3 软删除经 `updated_at` 增量被 upsert 捕获、节点 `status` 更新并保留（实测 child status→DISABLED 保留）
- [x] 2.4 bootstrap 分支：空图 / 无水位 → 现有 `DETACH DELETE` 全量重建 + 初始化各表水位为 `max(watermark_expr)`；
  非空 + 有水位 → 进入稳态轮询（实测重启跳过 bootstrap）
- [x] 2.5 loader 入口改长生命周期 `run_sync_loop()`：bootstrap 判定 → `while True: incremental_tick(); 周期 reconcile;
  sleep(POLL_INTERVAL_SEC)`；每 tick 用短生命周期 psycopg2 连接（避免 stale），Neo4j driver 长驻复用
- [x] 2.6 失败语义：tick 内 PG/Neo4j 异常 → 每表 try/except 隔离、记录、**不推进该表水位**、下 tick 重试
  （MERGE 幂等）；tick 级异常 → 退避 sleep 后下 tick 重试（不 exit，交由 restart 策略兜底致命故障）

## 3. 编排与脚本收口

- [x] 3.1 `config.py` 增 `POLL_INTERVAL_SEC`（默认 30）/ `RECONCILE_EVERY`（默认 1）env
- [x] 3.2 根 `docker-compose.yml` `data-loader`：`restart: no` → `unless-stopped`，注入
  `POLL_INTERVAL_SEC`/`RECONCILE_EVERY`（可经 `GRAPH_SYNC_*` 覆盖）；保留 `depends_on` healthy
- [x] 3.3 `run_all.sh`：仍先跑 `no000_scrub_sensitive.py`（防御）→ `exec python load_graph.py`（常驻循环）
- [x] 3.4 确认 `docker-compose.cd.yml` watchtower `WATCHTOWER_LABEL_ENABLE=false` = 监控全部容器，
  已覆盖常驻 `data-loader`，无需改动
- [x] 3.5 （追加）`Dockerfile` 加 `ENV PYTHONUNBUFFERED=1`，保证常驻服务的 tick/reconcile 日志实时刷出

## 4. 文档与 spec 同步

- [x] 4.1 同步 `openspec/specs/data-platform/spec.md`（RENAME + MODIFY 两条 + ADD 一条）
- [x] 4.2 重写 `db/ne4j_kindergartens/readme.md` 与 `SETUP_GUIDE.md`（增量 sync + bootstrap + 对账，删 one-shot 措辞）
- [x] 4.3 更新根 `README.md` / `README.en.md` / `README.zh-CN.md` 的 Neo4j 数据加载器条目（标注增量 sync）

## 5. 验证

- [x] 5.1 本机 `docker compose up -d db neo4j` → 起 data-loader → bootstrap 全量重建成功、各表水位初始化
  （实测：7 标签节点 + 10 个 `_GraphSyncState` 水位）
- [x] 5.2 增量验证：PG `INSERT` 新 child(999) / `UPDATE` status / 物理 `DELETE` cgr 一行 → 下一 tick 内图收敛
  （Child 6→7、HAS_GUARDIAN 6→5，与 PG count 一致）
- [x] 5.3 删除验证：软删（child2 status→DISABLED）节点保留且 status 更新；硬删 cgr 行经对账被清除、无孤儿
- [x] 5.4 PII 不投影核验：`MATCH (n) UNWIND keys(n)`（含 `_GraphSyncState`）无任一禁列；新 child 999（PG 侧带
  rrn_first6/rrn_hash/birth_date/address）在图中仅非 PII 白名单键；全局 forbidden-key 扫描为空
- [x] 5.5 跑 `LoaderPiiProjectionGuardTest`（DooD 单类）：BUILD SUCCESSFUL、0 违规（另本机等价 Python 扫描 0 违规）
- [x] 5.6 `openspec validate neo4j-incremental-sync --strict` 通过
