# 部署（Deployment）

✅ 来源：`docker-compose.yml`、`Jenkinsfile`、各 `Dockerfile`、`ai/docker-compose.yml`、`jenkins/`。

## 部署形态

整栈通过 **Docker Compose** 部署。根 `docker-compose.yml` 编排：`db`(PostgreSQL)、`neo4j`、`data-loader`、`backend`、`frontend`。**AI 服务不在根 compose**，由 `ai/docker-compose.yml` 独立部署。

```bash
docker compose up -d --build
```

拓扑、端口、依赖见 [architecture/system-overview.md](../architecture/system-overview.md#3-运行时拓扑整栈-docker-compose)。

## 镜像构建（多阶段）

| 组件 | 构建要点 | 证据 |
| --- | --- | --- |
| backend | `gradle:8.5-jdk21-alpine` 构建 → `eclipse-temurin:21-jre-alpine` 运行；**非 root 用户** `spring`；Asia/Seoul 时区 | `backend/Dockerfile` |
| frontend | `node:20-slim` 构建（`npm install --legacy-peer-deps` + `next build` 静态导出）→ `nginx:alpine` 托管 `/out` + 反代 `/api/` | `frontend/Dockerfile`、`nginx.conf` |
| ai | `python:3.12-slim` + ffmpeg/libgl；安装 PyTorch(cu130)；`outputs` 只读挂载 | `ai/Dockerfile`、`ai/docker-compose.yml` |
| db | 基于 PostgreSQL，启动执行 `initdb/*.sql` | `db/Dockerfile` |

## CI 流水线（Jenkins）

✅ `Jenkinsfile`（声明式）：

```text
Checkout Code  →  List Files  →  Docker Compose Up
                                   ├─ docker compose down --remove-orphans --volumes --rmi local || true
                                   └─ docker compose up -d --build
```

> ⚠️ **重要风险（已确认）**：CI 的部署步骤执行 `docker compose down --volumes`，即**每次部署都删除数据卷**（`postgres_data`、`neo4j_data`）。这会在每次 CI 部署时**清空数据库并重新从 initdb 种子初始化**。
> - 这对**演示环境**（每次重置到干净种子）说得通；
> - 对**保留数据的生产环境**会造成数据丢失。
> - 意图待确认，见 [open-questions](../modernization/open-questions.md)（OQ-OPS-1）。

✅ `jenkins/`（`Dockerfile` + `docker-compose.yml`）用于自建 Jenkins 环境（🔶 推断为本地/自托管 CI）。

## 生产前必做（基于已确认事实，仅清单非方案）

> 以下为**事实驱动的核对项**，处置方式由团队决定：

- [ ] 用 `.env` 覆盖所有默认凭据（`POSTGRES_*`、`NEO4J_*`、`JWT_SECRET`）——默认值已硬编码在 compose/yml（OQ-SEC-3/5）。
- [ ] 确认 CI 删卷行为是否符合目标环境预期（OQ-OPS-1）。
- [ ] 确认后端鉴权是否需开启（`SecurityConfig` 当前 permitAll + 过滤器停用，OQ-SEC-1）。
- [ ] 确认日志级别（`root: DEBUG`，OQ-SEC-6）。
- [ ] 确认 TLS/HTTPS 终结位置（仓库内无 TLS 配置）。
- [ ] 确认 AI 服务是否需部署、模型权重如何分发到 `outputs/`。

## 回滚

🔶 未见显式回滚机制。当前 CI 为"全量重建"，回滚 = 部署上一版镜像/代码并重跑 compose。❓ 正式回滚策略未记录。
