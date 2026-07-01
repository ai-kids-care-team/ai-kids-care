# Neo4j data-loader (PG → 派生关系图 ETL)

PostgreSQL 是 system-of-record；Neo4j 是**只读派生关系图**，`data-loader` 是 Neo4j 唯一写入者。
本目录是 compose 中的 `data-loader` 服务：从 one-shot（跑完即退）升级为**长生命周期增量 sync**
（`restart: unless-stopped`），**直接查询 PostgreSQL**，让派生图在轮询间隔内持续与 PG 收敛，
无需人工重跑。不再使用 CSV 快照。

## 文件

| 文件 | 作用 |
|------|------|
| `config.py` | 从环境变量读取 PG / Neo4j 连接配置 + 轮询参数（`POLL_INTERVAL_SEC` / `RECONCILE_EVERY`） |
| `neo4j_connect.py` | 共享 Neo4j 驱动（供 `no000` 使用） |
| `no000_scrub_sensitive.py` | **防御层**：REMOVE 历史节点上残留的 S0/PII 属性（幂等，先跑） |
| `load_graph.py` | **核心 sync**：bootstrap 全量重建 + 稳态增量 upsert + 周期对账清孤儿 |
| `run_all.sh` | 容器入口：`no000` → `load_graph`（常驻循环） |
| `Dockerfile` | python:3.11-slim + neo4j/psycopg2 驱动 |

## 图模型

```
节点:  User · Kindergarten · Teacher · Class · Child · Guardian · Role
关系:  (User)-[:HAS_ROLE]->(Role)
       (Kindergarten)-[:HAS_TEACHER]->(Teacher)
       (Teacher)-[:HAS_CLASS]->(Class)
       (Class)-[:HAS_CHILD]->(Child)
       (Child)-[:HAS_GUARDIAN]->(Guardian)
```

## INC-003：图中绝不含 PII

`load_graph.py` 对每个实体只 `SELECT` **非 PII 列白名单**（见各 `*_COLUMNS` 常量），PII 列
（`rrn_*` / `birth_date` / `address` / `email` / `phone` / `password_hash` /
`emergency_contact_*` / `contact_*`）根本不进 SQL 结果，无从写入图。`no000` 与后端
`LoaderPiiProjectionGuardTest`（源码扫描）为纵深防御。

## 运行

```bash
# 全栈（data-loader 在 db/neo4j healthy 后常驻运行：bootstrap → 增量轮询）
docker compose up -d --build

# 查看 sync 日志（应看到 tick / reconcile 输出，进程不退出）
docker compose logs -f data-loader

# 校验
docker exec -it neo4j cypher-shell -u neo4j -p "$NEO4J_PASSWORD" \
  "MATCH (n) RETURN labels(n)[0] AS label, count(*) ORDER BY label"
```

轮询参数（compose env，`config.py` 读取）：

| env | 默认 | 含义 |
|-----|------|------|
| `POLL_INTERVAL_SEC` (`GRAPH_SYNC_POLL_INTERVAL_SEC`) | `30` | 增量 tick 间 sleep 秒数 = 稳态延迟上界 |
| `RECONCILE_EVERY` (`GRAPH_SYNC_RECONCILE_EVERY`) | `1` | 每多少 tick 做一次全量 id 对账清孤儿 |

## 同步语义

**增量 sync**：loader 常驻循环，每 tick 以每表 high-water mark 拉取 `WHERE updated_at >= :wm`
（`user_role_assignments` 无 `updated_at`，用 `GREATEST(granted_at, COALESCE(revoked_at, granted_at))`）
的增量行，按非 PII 白名单 `UNWIND … MERGE` upsert（幂等），处理后把水位推进到本批 `max`。
PG 的业务写入在轮询间隔内自动反映到图，无需人工重跑。

- **bootstrap**：空图 / 无水位 → `DETACH DELETE` 全量重建 + 初始化各表水位（灾备/冷启动路径）。
- **软删除**（`status` 迁移）→ `updated_at` bump 被增量捕获，节点 status 更新并保留。
- **硬删除 / 关系行移除**（`updated_at` 观测不到）→ 每 `RECONCILE_EVERY` tick 做全量 id 对账
  `MATCH (n:Label) WHERE NOT n.<id> IN $liveIds DETACH DELETE n` 清孤儿（关系同理）。
- **水位存储**：Neo4j meta-node `(:_GraphSyncState {table, watermark})`（仅表名 + 时间戳，非 PII），
  loader 跨重启自恢复、不做无谓全量重建。
- **失败语义**：tick 内某表异常 → 记录、**不推进该表水位**、下 tick 重试（MERGE 幂等，不静默漂移）。
