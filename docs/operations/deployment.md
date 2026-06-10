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

## 两条部署生命周期（ADR-0012）

✅ **演示重置（Demo CI）** 与 **生产部署（Production）** 是两条独立路径，见 [ADR-0012](../decisions/adr/ADR-0012-production-data-lifecycle.md)。

### 演示 / CI 重置

`Jenkinsfile` 执行（每次 CI 部署均清空数据卷并从 initdb 种子重建）：

```text
Checkout Code → Test (./gradlew test, Testcontainers 门禁) → Demo Deploy (CI Reset)
                                                               ├─ docker compose down --remove-orphans --volumes --rmi local
                                                               └─ docker compose up -d --build
```

这对**演示环境**（每次重置到干净种子状态）是刻意设计，不适用于生产。

### 生产部署

**⚠️ 生产环境绝对不能使用 `--volumes`。** 使用 `docker-compose.prod.yml` override：

```bash
# 首次或后续生产部署（不删卷，Flyway 迁移管理 schema 变更）
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --build
```

- `docker-compose.prod.yml` 覆盖 `db` 服务：使用 `db/Dockerfile.prod`（不含 initdb 脚本的纯 postgres 镜像）。
- 后端启动时 Flyway 自动运行迁移：空库 → 执行 `V1__initial_baseline.sql` 建 schema；有库 → 执行 `>=V2` 的增量迁移。
- 迁移历史在 `flyway_schema_history` 表中可审计。

> ⚠️ **2026-06-10 复核：此路径尚未达到生产就绪。** 合并后的 compose 仍会启动 `data-loader`；它只依赖 PostgreSQL healthcheck，不依赖后端/Flyway 完成，因此空库首启存在 loader 与 V1 迁移的竞态。loader 还主要导入仓库内 CSV 快照并复制敏感字段。解决这些问题前，应把本节视为“生产方向的骨架”，而不是已验证的生产部署方案。

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

**演示/CI**：重跑 `docker compose down --volumes && docker compose up -d --build` 即可（数据卷本来就会清空）。

**生产**：Flyway 不支持自动回滚已执行的迁移。回滚选项：
1. 回退到上一版镜像（`docker compose up` 指定旧 tag）——若 schema 变更前向兼容则可行。
2. 编写修复迁移（`VN__revert_*.sql`）而非反向执行。
3. 从备份恢复——备份/恢复策略待定（OQ-OPS-4，未决，见 [ADR-0012](../decisions/adr/ADR-0012-production-data-lifecycle.md)）。
