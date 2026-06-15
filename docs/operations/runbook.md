# 运行手册（Runbook）

常见运维任务与故障排查。✅ 命令基于 `docker-compose.yml`、`db/README.md`、各组件文档。

## 启停

```bash
# 整栈启动（不含 AI）
docker compose up -d --build

# 查看状态 / 日志
docker compose ps
docker compose logs -f backend
docker compose logs -f db

# 停止（保留数据卷）
docker compose down

# ⚠️ 停止并删除数据卷（会清空 DB / Neo4j，慎用）
docker compose down -v

# AI 服务（独立）
cd ai && docker compose up -d --build
```

## 连接数据库

```bash
# PostgreSQL（容器名 ai-kids-postgres）
docker exec -it ai-kids-postgres psql -U kids_user -d kids_postgres_db

# Neo4j Browser
# 浏览器打开 http://localhost:7474（Bolt: bolt://localhost:7687）
```

## 健康检查

| 检查 | 方法 |
| --- | --- |
| PostgreSQL | compose 内置 healthcheck（`pg_isready`） |
| 后端 | 访问 `http://localhost:8080/swagger-ui/index.html`（🔶 无专用 health 端点，见 observability） |
| AI 服务 | `GET http://localhost:8001/health` |
| 前端 | 访问 `http://localhost` |

## 常见问题

### 后端启动即失败，报实体/表不匹配

✅ 原因：`ddl-auto=validate`——JPA 实体与实际表结构不一致。
- 核对 `db/dbml/schema.dbml` → `01_create_schema.sql` 与 `entity/` 是否同步。
- 若改过 schema 但 DB 卷已存在，initdb 不会重跑：需 `docker compose down -v` 重新初始化（会丢数据）。

### 检测事件/会话数据为"假数据"

✅ 这是预期：`detection_events` 等由种子数据填充，AI 实时链路当前不写库（见 [ai-architecture](../architecture/ai-architecture.md)）。

### 前端能登录但调用 API 仍 401

✅ 鉴权已启用（默认拒绝 + 服务端会话，PR #89）：会话过期/失效、user 禁用 / 角色撤销 / membership 结束会使**下一请求** `401`；CSRF 缺失/无效 `403`。排查：会话 cookie（`AI_KIDS_CARE_SESSION`）是否随请求带上、`X-XSRF-TOKEN` 头是否回显、生产 `SESSION_COOKIE_SECURE` 是否与 HTTPS 边缘一致（HTTP 下 `Secure` cookie 不发送）。见 [security-architecture](../architecture/security-architecture.md)。

### AI 推理服务启动报 `Model dir not found`

✅ 模型权重不在仓库。需将训练好的模型放到 `ai/outputs/videomae_baseline/best_model`（或设 `AI_MODEL_DIR`）。

### 中文/韩文种子乱码

✅ 用 UTF-8 文件 + `psql -f`/重定向导入，PowerShell 先 `chcp 65001`（见 [database-guide](../engineering/database-guide.md)）。

### Neo4j 图数据为空或过时

⚠️ data-loader 当前主要读取仓库内 CSV 快照；仅 users 另有 PG 导入脚本，且没有可靠增量同步。图为空或过时时，应先确认 CSV/PG 数据来源，再运行 `db/ne4j_kindergartens/run_all.sh` 或重建 data-loader 服务。

## Flyway 迁移管理

### 查看迁移历史

```bash
# 连接到 PostgreSQL 后查询
docker exec -it ai-kids-postgres psql -U kids_user -d kids_postgres_db \
  -c "SELECT version, description, type, success, installed_on FROM flyway_schema_history ORDER BY installed_rank;"
```

### 添加新迁移

在 `backend/src/main/resources/db/migration/` 下新建文件，命名格式：`V{N}__{描述}.sql`（N 为递增整数，如 `V2__relax_notifications_not_null.sql`）。后端重启时 Flyway 自动执行。

> ⚠️ 迁移文件一旦执行**不可修改**（Flyway 校验 checksum）。修订请新建修复迁移文件。

### 后端启动失败：Flyway checksum 不匹配

```
FlywayException: Validate failed: Migration checksum mismatch for migration version 1
```

原因：已执行的迁移文件被修改。处理方式：
1. 恢复原始文件内容（git checkout），或
2. 在 `flyway_schema_history` 中手动更新 checksum（仅限非生产紧急修复，需记录）。

### 后端启动失败：Found non-empty schema(s) without schema history

若出现 `Found non-empty schema(s) ... and not configured to baseline on migrate`，说明目标库有表但 `baseline-on-migrate` 未生效（配置未传入）。确认 `spring.flyway.baseline-on-migrate=true` 生效。

## 数据重置到干净种子（演示/CI 专用）

```bash
docker compose down -v
docker compose up -d --build   # 重新执行 initdb 全部种子
```

> ⚠️ 这与 Jenkins CI 每次部署的行为一致（见 [deployment](deployment.md)）。**生产绝不能用**。
