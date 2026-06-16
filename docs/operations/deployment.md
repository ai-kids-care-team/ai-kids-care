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

### 演示机首次配置（维护者操作一次，Windows PowerShell）

```powershell
# 1. 让 Docker Desktop 登录 GHCR（供初次 docker compose pull）。
#    Windows 上避免 --password-stdin（PowerShell 管道喂 stdin 易卡），改用交互式：
#    提示 Password 时粘贴 read:packages PAT。
docker login ghcr.io -u <github-username>

# 2. 在演示机仓库目录建 .env（已 gitignore），填 watchtower 的 GHCR 拉取凭据（见 .env.example）：
#    GHCR_USER=<github-username>
#    GHCR_PAT=<read:packages PAT>

# 3. 启动含 watchtower 的演示栈
docker compose -f docker-compose.yml -f docker-compose.cd.yml up -d
```

> **watchtower 认证（路 A，Windows 友好）**：watchtower 经 `REPO_USER`/`REPO_PASS` 从 `.env` 的
> `GHCR_USER`/`GHCR_PAT` 读凭据拉私有镜像——避开 Windows Docker Desktop 把 `docker login` 凭据
> 存进 credsStore（而非明文 `~/.docker/config.json`）导致挂载 config.json 失效的坑。第 1 步的
> `docker login` 仅供初次 `docker compose pull`；watchtower 的**自动**拉取靠 `.env`。
> **OQ-2（未决）**：GHCR PAT 的轮换方式。

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

### data-loader × Flyway 首启竞态的缓解（OQ-OPS-1，已决定方向，落地待部署验证）

**事实**：`run_all.sh` 串行执行 12 个脚本，其中**仅 `db100_insert_users.py` 读取 live PostgreSQL**（`SELECT … FROM users`），其余 `no*` 脚本读仓库内 CSV 快照、与 PG schema 无关。`data-loader` 仅 `depends_on db: service_healthy`（`pg_isready`），**不**依赖 Flyway 完成（Flyway 在 `backend` 启动时运行）。

- **演示 / CD（持久卷）路径竞态良性**：`db: service_healthy` 时 initdb 的 V1（含 `users` 表与种子）首启已建好，`db100` 的 `SELECT FROM users` 必然成功；后续启动数据持久。残留**潜在**风险——未来某迁移若改名/删除 `db100` 所 SELECT 的 `users` 列，会与 loader 竞态。
- **生产（`Dockerfile.prod` 空库无种子）路径竞态会破坏**：空库 `pg_isready` 即 healthy，但 `users` 表要等 Flyway（在 `backend` 内）建好；`data-loader` 不等 `backend`，`db100` 可能 `relation "users" does not exist` → `exit(1)`。且 loader 在生产仍会把仓库 CSV 演示快照灌入 Neo4j（生产不应有），并复制敏感字段（见下）。

**已决定方向**（本轮仅文档化；compose/loader 改动属部署行为、本机无部署环境、CI 仅 `docker compose config` 结构校验，须部署时验证后再落地）：

1. **生产不跑 data-loader**（同时消除竞态与向生产 Neo4j 注入演示/敏感数据）。建议在 `docker-compose.prod.yml` 用 prod-only 覆盖把 loader 置为 no-op（**提案，未落地**）：
   ```yaml
   services:
     data-loader:
       entrypoint: ["sh", "-c", "echo '[prod] data-loader disabled (loader×Flyway race + sensitive projection); skipping' && exit 0"]
   ```
2. **若演示路径将来需要严格排序**（迁移开始改 `users` 列时）：给 `backend` 加 healthcheck，并令 `data-loader.depends_on.backend: service_healthy`（**提案，未落地**），使 Flyway 完成后再跑 loader。

> 🔒 **关联安全问题（§365，单独跟踪）**：`db100_insert_users.py` 把 `password_hash`(S0) 与 `email`/`phone`(PII) 写入 Neo4j `User` 节点，违反 SPEC-0001 §365「loader/projection 不写入 S0/PII」。该项属安全域、单独切片修复（已开后台任务跟踪），不在本 ops 文档轮次内。上面的「生产不跑 loader」可消除其在**生产** Neo4j 的暴露，但 demo Neo4j 仍存在该投影，待安全切片处置。

## 生产前必做（基于已确认事实，仅清单非方案）

> 以下为**事实驱动的核对项**，处置方式由团队决定：

- [ ] 用 `.env` 覆盖所有默认凭据（`POSTGRES_*`、`NEO4J_*`）；设生产 `SESSION_COOKIE_SECURE=true` 与 Caddy `DOMAIN`/`ACME_EMAIL`（`JWT_SECRET` 已废，JWT→服务端会话）。
- [ ] 解决 data-loader 与 Flyway V1 迁移的首启竞态（OQ-OPS-1，方向已定见上「缓解」小节；部署时按提案落地并验证）。
- [x] 后端鉴权已开启（默认拒绝 + 服务端会话 + 每请求授权，PR #89）。
- [ ] 确认日志级别（`root: DEBUG`，OQ-SEC-6）。
- [ ] 部署 Caddy 边缘 TLS（`infra/caddy/Caddyfile`）：设公网 `DOMAIN`，验证 ACME 证书签发与 HTTP→HTTPS/HSTS（PR #89 草案，端到端待部署验证）。
- [ ] 确认 AI 服务是否需部署、模型权重如何分发到 `outputs/`。
- [ ] 生产切换 `db` 镜像为 `Dockerfile.prod`（无 initdb 种子），通过 CD 管线打 release tag 发布。
- [ ] 在 GHCR `release.yml` 中为"推 :prod"步添加 GitHub Environments 人工审批门（ADR-0022 OQ-3）。
- [ ] 配置生产机上的 GHCR PAT（OQ-2）及 watchtower 部署，或改用 `docker-compose.prod.yml` 手动部署路径。
- [ ] 建立 PostgreSQL/Neo4j 备份与恢复策略（OQ-OPS-4，策略见下「备份与恢复策略」节；自动化与异地存储待部署时落地，见 [ADR-0012](../decisions/adr/ADR-0012-production-data-lifecycle.md)）。

## 备份与恢复策略（OQ-OPS-4）

> 本节为**推荐策略 + 可执行命令**，与现有拓扑一致；自动化调度、异地/加密存储与恢复演练由维护者在真实环境落地与验证，归 [ADR-0012](../decisions/adr/ADR-0012-production-data-lifecycle.md)。命令见 [runbook](runbook.md#备份与恢复)。

**分层（按是否源真相决定备份必要性）**：

| 存储 | 角色 | 备份必要性 |
| --- | --- | --- |
| PostgreSQL（`postgres_data`） | **源真相**（全部业务数据 + `flyway_schema_history`） | **必须**——逻辑 `pg_dump` 为主，可选卷快照做 PITR |
| Neo4j（`neo4j_data`） | **派生投影**（由 CSV 快照 + `users` 的 PG 导入重建） | **非主备份目标**——PG 恢复后重跑 data-loader 即可重建 |
| Redis（`redis_data`） | 易失会话存储（Spring Session） | **无需备份**——丢失=用户重新登录 |

**策略要点**：

- **以 PostgreSQL 逻辑备份为基线**：`pg_dump -Fc`（custom 格式，支持选择性恢复）。一份完整 dump 同时含 schema、数据与 `flyway_schema_history`，恢复后 Flyway 看到一致的迁移历史、只增量执行更新的迁移，无需特殊处理。
- **迁移前必做**：每次应用新 Flyway 迁移（尤其破坏性 schema 变更）前先取一份 PG dump——这是回滚选项 3（从备份恢复）的前提，与 Flyway「只前向、不反向」（见下「本地开发回滚」）配套。
- **节奏与留存**：建议每日逻辑备份 + 保留 N 天（具体由维护者按 RPO 定）；存放到容器与宿主之外的位置（异地/对象存储），避免与数据卷同损。
- **Neo4j 恢复 = 重建**：PG 恢复后执行 data-loader（`run_all.sh`）重投影即可；如需独立时点快照，可用 `neo4j-admin database dump`（停库或在线备份），但非主路径。
- **恢复演练**：恢复流程须定期在非生产环境演练验证（备份未经恢复验证不算备份）。

## 本地开发回滚

**演示/CD**：`docker compose -f docker-compose.yml -f docker-compose.cd.yml down -v && ... up -d`（清卷重新灌种子）。

**生产**：Flyway 不支持自动回滚已执行的迁移。回滚选项：
1. 回退到上一版镜像（重推旧版到 `:prod` tag，watchtower 自动拉取）——若 schema 变更前向兼容则可行。
2. 编写修复迁移（`VN__revert_*.sql`）而非反向执行。
3. 从备份恢复——见上「[备份与恢复策略（OQ-OPS-4）](#备份与恢复策略oq-ops-4)」（迁移前的 PG dump 即此选项的前提）。
