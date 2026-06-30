## 1. 抽取白名单契约（先做，作为实现依据）

- [ ] 1.1 从当前 12 个 insert/relationship 脚本的 `MERGE … SET` 子句逐字段抄录每个节点标签
  （User/Kindergarten/Teacher/Class/Child/Guardian）的**非 PII 属性白名单**与每条关系
  （HAS_TEACHER/HAS_CLASS/HAS_CHILD/HAS_GUARDIAN）的属性集，记入实现注释/模块顶部常量，确保切 PG 后图形状逐字段不变
- [ ] 1.2 对照 PG schema（`db/dbml/schema.dbml` 终态）确认每个白名单列在对应 PG 表存在、列名一致
  （`users`/`kindergartens`/`teachers`/`classes`/`children`/`guardians` 与三张 assignment/relationship 表）

## 2. PG-sourced ETL 实现

- [ ] 2.1 `requirements.txt` 增加 `psycopg[binary]`（PostgreSQL v3 驱动）
- [ ] 2.2 新增 `load_graph.py`：用 `config.py` 的 PG 变量连接 PostgreSQL，单进程内按"清空→节点→关系"建图
- [ ] 2.3 起始 `MATCH (n) DETACH DELETE n` 全清，再按 id 键 MERGE/CREATE 节点；保留 `*_id IS UNIQUE` 约束创建
- [ ] 2.4 每个节点用**显式列白名单** `SELECT` 非 PII 列（D2），PII 列绝不进 SELECT；temporal 列用驱动原生类型，去掉 CSV 路径的 `fix_datetime/fix_date` 字符串补丁
- [ ] 2.5 关系按现状属性集从 PG `class_teacher_assignments`/`child_class_assignments`/`child_guardian_relationships` + Teacher.kindergarten_id 建立
- [ ] 2.6 任一步异常 → 进程非零退出（不吞错建空图/半图）

## 3. 编排与脚本收口

- [ ] 3.1 改 `run_all.sh` 为两步：`no000_scrub_sensitive.py`（防御）→ `load_graph.py`
- [ ] 3.2 删除 `data/`（10 个 CSV）与 11 个 `noXXX_insert_*.py` + `db100_insert_users.py`（保留 `no000`/`neo4j_connect.py`/`config.py`/`Dockerfile`）
- [ ] 3.3 确认 `docker-compose.yml` `data-loader` 的 PG env 与 `depends_on: db service_healthy` 保留正确；`neo4j_connect.py` 凭据从 env 读取（去掉硬编码默认）

## 4. 文档与 spec 同步

- [ ] 4.1 同步 `openspec/specs/data-platform/spec.md` 两条需求（经本 change 的 delta apply 落地）
- [ ] 4.2 更新 `db/ne4j_kindergartens/readme.md` 与 `SETUP_GUIDE.md`（描述 PG-sourced ETL，删 CSV 说明）
- [ ] 4.3 更新 `README.md` / `README.en.md` / `README.zh-CN.md` 中描述 CSV data-loader 的段落

## 5. 验证

- [ ] 5.1 本机 `docker compose up -d db neo4j` → 跑 data-loader，确认正常退出码 0
- [ ] 5.2 Cypher 核验：各节点 `count` 与 PG `SELECT count(*)` 一致；五段关系路径 `(Kindergarten)-[:HAS_TEACHER]->(Teacher)-[:HAS_CLASS]->(Class)-[:HAS_CHILD]->(Child)-[:HAS_GUARDIAN]->(Guardian)` 连通
- [ ] 5.3 PII 不投影核验：`MATCH (n) UNWIND keys(n) AS k RETURN DISTINCT k` 结果不含任一禁列
- [ ] 5.4 跑 `LoaderPiiProjectionGuardTest`（DooD 单类）应找到 loader `.py` 且 0 违规
- [ ] 5.5 `openspec validate neo4j-sync-from-postgres --strict` 通过
