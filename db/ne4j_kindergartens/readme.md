# Neo4j data-loader (PG → 派生关系图 ETL)

PostgreSQL 是 system-of-record；Neo4j 是**只读派生关系图**。本目录是 compose 中的
`data-loader` 服务：容器启动时一次性运行，**直接查询 PostgreSQL** 重建图，跑完即退出
（`restart: no`）。不再使用 CSV 快照。

## 文件

| 文件 | 作用 |
|------|------|
| `config.py` | 从环境变量读取 PG / Neo4j 连接配置（单一出处） |
| `neo4j_connect.py` | 共享 Neo4j 驱动（供 `no000` 使用） |
| `no000_scrub_sensitive.py` | **防御层**：REMOVE 历史节点上残留的 S0/PII 属性（幂等，先跑） |
| `load_graph.py` | **核心 ETL**：清空旧图 → 按非 PII 列白名单从 PG 重建节点与关系 |
| `run_all.sh` | 容器入口：`no000` → `load_graph` |
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
# 全栈（data-loader 在 db/neo4j healthy 后自动跑一次）
docker compose up -d --build

# 仅重跑 loader（PG 数据变化后重新同步）
docker compose run --rm data-loader

# 校验
docker exec -it neo4j cypher-shell -u neo4j -p "$NEO4J_PASSWORD" \
  "MATCH (n) RETURN labels(n)[0] AS label, count(*) ORDER BY label"
```

## 同步语义

One-shot：图反映 **loader 运行时刻**的 PG 状态；PG 后续写入需重跑 loader 才反映（无增量/实时 sync）。
每次运行先 `DETACH DELETE` 全清再重建，保证图严格镜像当前 PG（不残留已删实体的孤儿节点）。
