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

### 前端能登录但调用 API 仍 401（或反之能无凭证访问）

✅ 后端鉴权当前关闭（`permitAll` + 过滤器停用）。前端的 401/刷新逻辑可能与后端实际行为不符，见 [security-architecture](../architecture/security-architecture.md)。核对部署的后端 `SecurityConfig`。

### AI 推理服务启动报 `Model dir not found`

✅ 模型权重不在仓库。需将训练好的模型放到 `ai/outputs/videomae_baseline/best_model`（或设 `AI_MODEL_DIR`）。

### 中文/韩文种子乱码

✅ 用 UTF-8 文件 + `psql -f`/重定向导入，PowerShell 先 `chcp 65001`（见 [database-guide](../engineering/database-guide.md)）。

### Neo4j 图数据为空或过时

✅ 图由 data-loader 一次性从 PG 加载。PG 改动后需重跑 loader（`db/ne4j_kindergartens/run_all.sh`，或重建 data-loader 服务）。

## 数据重置到干净种子

```bash
docker compose down -v
docker compose up -d --build   # 重新执行 initdb 全部种子
```

> ⚠️ 这与 Jenkins CI 每次部署的行为一致（见 [deployment](deployment.md)）。
