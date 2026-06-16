# 部署（Deployment）

✅ 来源：`docker-compose.yml`、`docker-compose.cd.yml`、`docker-compose.prod.yml`、各 `Dockerfile`、`ai/docker-compose.yml`、`.github/workflows/release.yml`（ADR-0022 落地）。

## 部署形态

整栈通过 **Docker Compose** 部署。根 `docker-compose.yml` 编排：`db`(PostgreSQL)、`neo4j`、`data-loader`、`backend`、`frontend`。**AI 服务不在根 compose**，由 `ai/docker-compose.yml` 独立部署。

拓扑、端口、依赖见 [architecture/system-overview.md](../architecture/system-overview.md#3-运行时拓扑整栈-docker-compose)。

## 镜像构建（多阶段）

| 组件 | 构建要点 | 证据 |
| --- | --- | --- |
| backend | `gradle:8.5-jdk21-alpine` 构建 → `eclipse-temurin:21-jre-alpine` 运行；**非 root 用户** `spring`；Asia/Seoul 时区 | `backend/Dockerfile` |
| frontend | `node:20-slim` 构建（`npm install --legacy-peer-deps` + `next build` 静态导出）→ `nginx:alpine` 托管 `/out` + 反代 `/api/` | `frontend/Dockerfile`、`nginx.conf` |
| ai | `python:3.12-slim` + ffmpeg/libgl；安装 PyTorch(cu130)；`outputs` 只读挂载 | `ai/Dockerfile`、`ai/docker-compose.yml` |
| db | 基于 PostgreSQL，启动执行 `initdb/*.sql` 灌演示种子（**演示用**；生产用 `Dockerfile.prod`） | `db/Dockerfile` |

## CD 管线（ADR-0022）

**触发规则**：在 `main` 打 `v*` tag → GitHub Actions `release.yml` 自动构建、冒烟、推送到 GHCR；演示机上 watchtower 感知 `:prod` digest 变化后自动拉取并重建容器。**Jenkins 已退役。**

```
main 打 v* tag
  └─ release.yml（GitHub Actions 托管 runner）
       ├─ buildx 构建四镜像（db/data-loader/backend/frontend）
       ├─ 冒烟门：整栈 up → db healthcheck → backend running → frontend HTTP
       └─ 冒烟绿 → push :<version> + :prod 到 GHCR 私有包
                                      ↓
                         演示机 watchtower（每 5 分钟轮询）
                           └─ 检测到 :prod digest 变化 → pull + 重建容器
```

### GHCR 镜像命名

| 服务 | 镜像 |
| --- | --- |
| db（含演示种子） | `ghcr.io/ai-kids-care-team/ai-kids-care/db:<version>` / `:prod` |
| data-loader | `ghcr.io/ai-kids-care-team/ai-kids-care/data-loader:<version>` / `:prod` |
| backend | `ghcr.io/ai-kids-care-team/ai-kids-care/backend:<version>` / `:prod` |
| frontend | `ghcr.io/ai-kids-care-team/ai-kids-care/frontend:<version>` / `:prod` |

`:<version>` 不可变，用于回滚；`:prod` 是 watchtower 轮询的可变 tag。

### 演示机首次配置（维护者操作一次）

```bash
# 1. 用 read:packages scope 的 PAT 登录 GHCR
docker login ghcr.io -u <github-username> --password-stdin
# 2. 启动含 watchtower 的演示栈
docker compose -f docker-compose.yml -f docker-compose.cd.yml up -d
```

> ⚠️ **OQ-2（未决）**：GHCR PAT 的发放与轮换方式（host 上如何安全存放）。
> 当前：维护者手动 `docker login`，凭据写入 `~/.docker/config.json`，watchtower 通过挂载复用。

### 演示数据策略（OQ-1 已定：持久）

`db` 使用含 initdb 种子的镜像（`ghcr.io/.../db:prod`）；**首次启动一次灌种子 + 持久卷（`postgres_data`）**；schema 由 Flyway 增量管理。watchtower 重建容器时不清卷，演示数据持续存在。

若需强制重灌种子（例如新建演示环境）：

```bash
docker compose -f docker-compose.yml -f docker-compose.cd.yml down -v
docker compose -f docker-compose.yml -f docker-compose.cd.yml up -d
```

### 回滚

回滚 = 将 GHCR 中 `:prod` tag 重新指向旧版本镜像，再手动 `docker compose pull` 或等待 watchtower 下一个轮询周期自动拉取。

```bash
# 在有推送权限的机器（或通过 GitHub Actions）：
docker pull ghcr.io/ai-kids-care-team/ai-kids-care/backend:v1.0.0
docker tag  ghcr.io/ai-kids-care-team/ai-kids-care/backend:v1.0.0 \
            ghcr.io/ai-kids-care-team/ai-kids-care/backend:prod
docker push ghcr.io/ai-kids-care-team/ai-kids-care/backend:prod
# watchtower 轮询后自动重建（或手动：docker compose pull && docker compose up -d）
```

## 两条部署生命周期（ADR-0012 + ADR-0022）

### 演示 / CD 自动部署

演示机运行 `docker-compose.cd.yml` overlay（base + watchtower；不构建、拉 GHCR 镜像）。release tag 触发 `release.yml`，冒烟门通过后推 `:prod`，watchtower 自动部署。

本地开发/手动演示启动（仍可用）：

```bash
docker compose up -d --build
```

### 生产部署

**⚠️ 生产环境绝对不能使用 `--volumes`。** 使用 `docker-compose.prod.yml` override：

```bash
# 首次或后续生产部署（不删卷，Flyway 迁移管理 schema 变更）
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d
```

- `docker-compose.prod.yml` 覆盖 `db` 服务：使用 `db/Dockerfile.prod`（不含 initdb 脚本的纯 postgres 镜像）。
- 后端启动时 Flyway 自动运行迁移：空库 → 执行 `V1__initial_baseline.sql` 建 schema；有库 → 执行 `>=V2` 的增量迁移。
- 迁移历史在 `flyway_schema_history` 表中可审计。
- 生产 overlay 包含 Caddy 边缘 TLS 和 `SESSION_COOKIE_SECURE=true`（ADR-0017）。

> ⚠️ **2026-06-10 复核：此路径尚未达到生产就绪。** 合并后的 compose 仍会启动 `data-loader`；它只依赖 PostgreSQL healthcheck，不依赖后端/Flyway 完成，因此空库首启存在 loader 与 V1 迁移的竞态。loader 还主要导入仓库内 CSV 快照并复制敏感字段。解决这些问题前，应把本节视为"生产方向的骨架"，而不是已验证的生产部署方案。

## 生产前必做（基于已确认事实，仅清单非方案）

> 以下为**事实驱动的核对项**，处置方式由团队决定：

- [ ] 用 `.env` 覆盖所有默认凭据（`POSTGRES_*`、`NEO4J_*`）；设生产 `SESSION_COOKIE_SECURE=true` 与 Caddy `DOMAIN`/`ACME_EMAIL`（`JWT_SECRET` 已废，JWT→服务端会话）。
- [ ] 解决 data-loader 与 Flyway V1 迁移的首启竞态（OQ-OPS-1）。
- [x] 后端鉴权已开启（默认拒绝 + 服务端会话 + 每请求授权，PR #89）。
- [ ] 确认日志级别（`root: DEBUG`，OQ-SEC-6）。
- [ ] 部署 Caddy 边缘 TLS（`infra/caddy/Caddyfile`）：设公网 `DOMAIN`，验证 ACME 证书签发与 HTTP→HTTPS/HSTS（PR #89 草案，端到端待部署验证）。
- [ ] 确认 AI 服务是否需部署、模型权重如何分发到 `outputs/`。
- [ ] 生产切换 `db` 镜像为 `Dockerfile.prod`（无 initdb 种子），通过 CD 管线打 release tag 发布。
- [ ] 在 GHCR `release.yml` 中为"推 :prod"步添加 GitHub Environments 人工审批门（ADR-0022 OQ-3）。
- [ ] 配置生产机上的 GHCR PAT（OQ-2）及 watchtower 部署，或改用 `docker-compose.prod.yml` 手动部署路径。
- [ ] 建立 PostgreSQL/Neo4j 备份与恢复策略（OQ-OPS-4，见 ADR-0012）。

## 本地开发回滚

**演示/CD**：`docker compose -f docker-compose.yml -f docker-compose.cd.yml down -v && ... up -d`（清卷重新灌种子）。

**生产**：Flyway 不支持自动回滚已执行的迁移。回滚选项：
1. 回退到上一版镜像（重推旧版到 `:prod` tag，watchtower 自动拉取）——若 schema 变更前向兼容则可行。
2. 编写修复迁移（`VN__revert_*.sql`）而非反向执行。
3. 从备份恢复——备份/恢复策略待定（OQ-OPS-4，未决，见 [ADR-0012](../decisions/adr/ADR-0012-production-data-lifecycle.md)）。
