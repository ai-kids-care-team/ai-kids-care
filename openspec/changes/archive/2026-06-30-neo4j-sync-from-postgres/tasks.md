## 1. 抽取白名单契约（先做，作为实现依据）

- [x] 1.1 从当前 12 个 insert/relationship 脚本的 `MERGE … SET` 子句逐字段抄录每个节点标签
  （User/Kindergarten/Teacher/Class/Child/Guardian/**Role**）的**非 PII 属性白名单**与每条关系
  （**HAS_ROLE**/HAS_TEACHER/HAS_CLASS/HAS_CHILD/HAS_GUARDIAN）的属性集，记入 `load_graph.py` 顶部常量；
  实现期核实图含 User/Role/HAS_ROLE（初稿遗漏，已纳入）
- [x] 1.2 对照 PG schema（`db/dbml/schema.dbml` 终态）确认每个白名单列在对应 PG 表存在、列名一致
  （`users`/`kindergartens`/`teachers`/`classes`/`children`/`guardians` 与 `user_role_assignments` + 三张 assignment/relationship 表）

## 2. PG-sourced ETL 实现

- [x] 2.1 复用既有 `psycopg2-binary==2.9.9`（已在 requirements + db100 已用此模式）；顺带删去未引用的 `pandas`
- [x] 2.2 新增 `load_graph.py`：用 `config.py` 的 PG 变量连接 PostgreSQL，单进程内按"清空→约束→节点→关系"建图
- [x] 2.3 起始 `MATCH (n) DETACH DELETE n` 全清，再按 id 键 MERGE 节点；保留 7 个 `*_id`/`role_key` UNIQUE 约束创建
- [x] 2.4 每个节点用**显式列白名单** `SELECT` 非 PII 列（D2），PII 列绝不进 SELECT；temporal 列 `isoformat()` 归一，去掉 CSV 路径的 `fix_datetime/fix_date` 补丁
- [x] 2.5 关系按现状属性集从 PG `user_role_assignments`/`class_teacher_assignments`/`child_class_assignments`/`child_guardian_relationships` + Teacher.kindergarten_id 建立
- [x] 2.6 任一步异常 → `sys.exit(1)`（psycopg2/Neo4jError/通用兜底，不吞错建空图/半图）

## 3. 编排与脚本收口

- [x] 3.1 改 `run_all.sh` 为两步（`set -euo pipefail`）：`no000_scrub_sensitive.py`（防御）→ `load_graph.py`
- [x] 3.2 删除 `data/`（10 个 CSV）与 11 个 `noXXX_insert_*.py` + `db100_insert_users.py`；`Dockerfile` 去掉 `COPY data`（保留 `no000`/`neo4j_connect.py`/`config.py`）
- [x] 3.3 确认 `docker-compose.yml` `data-loader` 的 PG env 与 `depends_on: db service_healthy` 正确（无需改）；`neo4j_connect.py` 改为从 `config.py`(env) 读凭据、去掉硬编码默认与 import 时连接副作用

## 4. 文档与 spec 同步

- [x] 4.1 同步 `openspec/specs/data-platform/spec.md` 两条需求（经本 change 的 delta apply 落地）
- [x] 4.2 重写 `db/ne4j_kindergartens/readme.md` 与 `SETUP_GUIDE.md`（描述 PG-sourced ETL，删 CSV 说明）
- [x] 4.3 更新 `README.md` / `README.en.md` / `README.zh-CN.md` 的 Neo4j 数据加载器条目（标注 PG-sourced、不使用 CSV）

## 5. 验证

- [x] 5.1 本机 `docker compose up -d db neo4j` → build+run data-loader，**退出码 0**（no000 scrub + load_graph 全程成功）
- [x] 5.2 Cypher 核验：节点 count 全等 PG（User10/KG3/Teacher6/Class6/Child6/Guardian3/Role10）；关系 HAS_ROLE10/HAS_TEACHER6/HAS_CLASS6/HAS_CHILD6/HAS_GUARDIAN6 等于源表；五段路径连通=6
- [x] 5.3 PII 不投影核验：`MATCH (n) UNWIND keys(n) AS k ...` PII 子集为 `[]`；全图 distinct keys 人工核对无任一禁列
- [x] 5.4 跑 `LoaderPiiProjectionGuardTest`（DooD 单类）：BUILD SUCCESSFUL，`:test` 执行，找到 loader `.py` 且 0 违规
- [x] 5.5 `openspec validate neo4j-sync-from-postgres --strict` 通过
