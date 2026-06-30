# Runbook：生产首次上线与 schema 重置

> **状态说明**：截至本文撰写，本项目**尚无生产环境**。本 runbook 是**首次上线的前置准备**，把当前代码/配置中已落地的事实整合成可照做的操作手册。部署栈演进（Flyway 新增迁移、compose 分层、CD 机制变化）时，**必须同步更新本文档**。
>
> **破坏性操作纪律**：删除 / 迁移 / schema 重置 / 部署上线均为破坏性任务，**须在执行前经维护者逐个批准**（见 CLAUDE.md 工作范式）。本文档**不执行任何命令**，只描述操作流程。
>
> **关联文档**：`docs/runbook-production-reset.md`（既有的"清空旧卷冷启动"reset runbook，本文档是其超集 + 首次上线补充；`docker-compose.cd.yml` / `docker-compose.prod.yml` 注释亦引用之）。

---

## 0. 部署栈拓扑速览

四个业务镜像（`db` / `data-loader` / `backend` / `frontend`）+ Neo4j + Redis，经三层 compose 叠加：

| compose 文件 | 用途 | 关键差异 |
|--------------|------|----------|
| `docker-compose.yml`（base） | 本地 / 演示 / CI smoke | `db` 用 `db/Dockerfile`（**含 `db/initdb` 演示种子**）；端口全部发布到 host；`SESSION_COOKIE_SECURE` 默认 false；`${VAR:-default}` 仅供本地回退 |
| `+ docker-compose.prod.yml`（prod overlay） | **真生产** | `db` 改用 `db/Dockerfile.prod`（vanilla postgres，**无 initdb 种子**，schema 由 Flyway 建）；新增 `caddy` 边缘 TLS 终止（独占 host 80/443）；`db`/`neo4j`/`redis`/`frontend` 端口 `!reset null` 不对外发布；`SESSION_COOKIE_SECURE=true`；暴露 `BOOTSTRAP_ADMIN_*` env |
| `+ docker-compose.cd.yml`（CD overlay） | 部署机自动拉取 | 清除所有 `build:`（`!reset null`），强制从 GHCR 拉 `:prod` 镜像；新增 `watchtower` 轮询 `:prod` digest（默认 `WATCHTOWER_POLL_INTERVAL=300` 即 5 分钟）自动 pull + 重建 |

**prod 与 cd 两条 override 相互独立、不混用**（cd 文件注释明确说明）。真 prod（Caddy TLS + Dockerfile.prod 无种子 + Secure cookie）用：

```sh
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d
```

---

## 1. 前置条件：必填环境变量

所有密钥/凭据从 `.env.example` 复制为 `.env`（`.env` 已被 `.gitignore` 忽略，**绝不入库、绝不提交真值**）。生产**必须覆盖所有默认凭据**——base compose 里的 `${VAR:-default}` 仅供本地/演示回退。

### 1.1 强制 fail-fast 的密钥（缺失则 compose 报错或后端启动失败）

`docker-compose.yml` 用 `${VAR:?... must be set}` 语法对下列变量做硬校验，缺失即 compose 拒绝启动；后端启动时再次以 `@NotBlank` 等做 fail-fast 二次校验：

| 变量 | 用途 | 生成方式 / 约束 |
|------|------|------------------|
| `POSTGRES_PASSWORD` | PostgreSQL 主密码（db / backend / data-loader 共用） | 强随机；无弱默认 |
| `NEO4J_PASSWORD` | Neo4j 认证（neo4j / backend / data-loader） | 强随机 |
| `REDIS_PASSWORD` | Redis `--requirepass` + backend `REDIS_PASSWORD`（同一变量注入两处） | 强随机 |
| `RRN_HASH_PEPPER` | RRN HMAC-SHA256 pepper（ADR-0024，单向哈希、不可逆） | 强随机；**经 secret manager 注入，与 DB 备份/快照分开保管**；切勿用默认值（`test-pepper-not-secret-2026` 仅限 test/CI） |
| `CAMERA_STREAM_AES_KEY_V1` | 摄像头流密码 AES-256-GCM 主密钥（ADR-0026） | **32 字节 Base64**，`openssl rand -base64 32`；**仅注入 Java backend，绝不下发给 AI 容器**；缺失则后端 fail-fast |
| `AI_SERVICE_TOKEN` | AI 服务调 `/api/v1/internal/**` 的共享 Bearer token（ROLE_AI_SERVICE） | `openssl rand -base64 32`；**必须与 AI 栈 `ai/.env.example` 的 `AI_SERVICE_TOKEN` 同值** |

> 注：`PUSHOVER_API_TOKEN` / `SOLAPI_API_KEY` / `SOLAPI_API_SECRET` / `SOLAPI_SENDER` 在 `application.yml` 中也是 `${ENV}` 注入且对应 config 类 fail-fast（`PushoverConfig` / `SolapiConfig` 空白即启动失败）。若生产启用 PUSH/SMS 通知通道，须一并提供；否则按 provider 启用范围决定。

### 1.2 生产 overlay 专用变量（`docker-compose.prod.yml`）

| 变量 | 用途 | 生产取值 |
|------|------|----------|
| `SESSION_COOKIE_SECURE` | 会话 cookie 标记 Secure（仅 HTTPS 下发） | overlay 已硬编码 `"true"`，无需手动设 |
| `DOMAIN` | Caddy 自动 ACME 证书签发的公网域名 | **必填**，须为真实公网 DNS 名 |
| `ACME_EMAIL` | ACME 注册邮箱（证书到期/吊销通知） | 建议填 |

### 1.3 CD overlay 专用变量（`docker-compose.cd.yml`，仅部署机）

| 变量 | 用途 |
|------|------|
| `GHCR_USER` | GitHub 用户名（watchtower 经 `REPO_USER` 拉 GHCR 私有镜像） |
| `GHCR_PAT` | classic PAT，**`read:packages` scope**（经 `REPO_PASS` 注入；Windows Docker Desktop credsStore 坑的规避路径） |

> **安全 invariant**：secret / PII（RRN、密码、token、session id、raw identifier、请求 body）**绝不入日志/审计/异常**；`.env.example` 只放占位。生产 `LOG_LEVEL_ROOT` 保持 `INFO`（DEBUG 会海量日志并可能泄露敏感数据）。

---

## 2. 首次生产部署（空库）

### 2.1 schema 装配：单一 V1 baseline

- 当前迁移目录只有 **`backend/src/main/resources/db/migration/V1__initial_baseline.sql`**（DB-1 squash 后的单一基线；未来增量从 V2+ 起）。
- `application.yml` Flyway 配置：`baseline-on-migrate: true` + `baseline-version: 1`，`ddl-auto: validate`（Hibernate 不建表，只校验 schema 与 entity 一致）。
- **空库（生产首次部署）路径**：`db/Dockerfile.prod` 是 vanilla postgres、**无 initdb 种子**，所以 PostgreSQL 卷启动时是空的。后端启动时 Flyway 发现空库 → **不触发 baseline** → 正常执行 `V1__initial_baseline.sql` 建全量 schema → 记入 `flyway_schema_history`。
- Spring Boot 保证 Flyway 迁移**先于** `ApplicationRunner` 完成，故 `AdminBootstrapRunner`（见 §3）运行时 schema 已就绪。

### 2.2 发布流水线（`.github/workflows/release.yml`）

由推送到任意分支的 semver tag（`v*`，通常在 `main`）触发，分两个 job：

1. **`build-smoke`（自动，无审批）**——构建四镜像（`db` 用**含种子的 `db/Dockerfile`** 供 smoke 验证，非 Dockerfile.prod）→ 本地 tag 为 `:prod` 别名 → `docker compose up` 起全栈 smoke：
   - 等 `db` healthcheck（最多 3 分钟）；
   - 校验 `backend` 容器 running（未崩溃）；
   - smoke 前端 HTTP；
   - 轮询 **`GET /api/v1/auth/csrf`**（permitAll 的 CSRF-bootstrap 端点，匿名永远 200）直到就绪（最多 120s）作为后端 readiness 闸；
   - **Playwright E2E 硬门禁**（Tier-2 release acceptance，原生跑在 runner 上）——任一断言失败即 job 失败，**`:version` 不推送**；
   - smoke 用的 fail-fast env 是**故意非 secret 的占位值**（如 `test-pepper-not-secret-2026`、全 A 的 Base64 AES key），仅为让 backend 启动校验通过。
   - 全绿后推送不可变的 **`:<version>`** 镜像到 GHCR（`:prod` 在此 job **不推**）。
2. **`deploy-prod`（人工审批门）**——`environment: production`，受 **GitHub Environment `production` 的 required reviewer（维护者）** 闸控，job 暂停等批准；批准后 pull 通过 smoke 的 `:<version>` → 重 tag 为 `:prod` → push（**不重建**，保证 `:prod` == smoke 过的镜像）。
3. 部署机上的 **watchtower** 轮询发现 `:prod` digest 变化 → 自动 pull + 重建容器（持久卷 `postgres_data` 跨重建保留）。

### 2.3 首次上线手动步骤（部署机，每步须维护者批准）

> 前提：部署机已用 GHCR PAT（`read:packages`）`docker login ghcr.io`；prod overlay 与 cd overlay 不混用——真 prod TLS 栈用 prod overlay。

1. 填好 `.env`（§1 全部必填变量 + `DOMAIN` / `ACME_EMAIL` + 首次冷启动的 `BOOTSTRAP_ADMIN_*`，见 §3）。
2. 确认 `db` 由 `db/Dockerfile.prod` 构建（无 `COPY initdb/`）——`docker compose ... config` 可看有效构建上下文。
3. 启动 prod 栈（首次空库会跑 Flyway V1 建 schema，再由 `AdminBootstrapRunner` 建首个 SUPERADMIN）：
   ```sh
   DOMAIN=app.example.com ACME_EMAIL=ops@example.com \
     docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d
   ```
4. **部署时验证（CI 不覆盖）**：真实 ACME 证书签发需公网 DNS 指向 `$DOMAIN` 且 host 的 80/443 空闲可达；`docker compose config` 只验结构，不验证书签发/端口绑定。
5. 健康检查见 §5。

---

## 3. 冷启动管理员（`AdminBootstrapRunner`）

生产 DB 是 Flyway schema-only、**无任何 seed 账号**，需引导首个管理员。位置：`backend/src/main/java/com/ai_kids_care/v1/bootstrap/AdminBootstrapRunner.java`。

### 3.1 开启流程

仅首次冷启动设这两个 env（`docker-compose.prod.yml` 已声明透传）：

```sh
export BOOTSTRAP_ADMIN_LOGIN_ID=<非 admin 的 login id>
export BOOTSTRAP_ADMIN_PASSWORD=<强密码，绝非 admin123，绝不入 git>
# 可选：BOOTSTRAP_ADMIN_NAME / BOOTSTRAP_ADMIN_DEPARTMENT（默认 "Bootstrap Admin" / "Platform Operations"）
```

对应 `application.yml` 的 `bootstrap.admin.{login-id,password,name,department}`，无弱默认（未设即空 → runner 无动作）。

### 3.2 安全与幂等约束（代码已强制）

- **env-gate**：`BOOTSTRAP_ADMIN_LOGIN_ID` 与 `BOOTSTRAP_ADMIN_PASSWORD` 二者皆非空白才动作；否则**静默跳过**，绝不创建默认/可猜账号。
- **拒绝 `admin`**：login id 等于 `admin`（大小写无关）一律拒绝（避免与演示 seed anchor 冲突、防 robot 扫 `admin`/`admin123`），仅 WARN 不创建。
- **空表条件**：仅当 `users` 表 `count()==0` 时创建**恰好 1 个 `ACTIVE` `SUPERADMIN`**（`PLATFORM` scope）。
- **幂等**：已有任何用户即 no-op；后续带 env 重启也是 no-op。
- **不打密码**：仅记录 `loginId`，密码 bcrypt 后绝不入日志。

### 3.3 首次登录后应做的事

1. 用 `BOOTSTRAP_ADMIN_LOGIN_ID` / `BOOTSTRAP_ADMIN_PASSWORD` 登录，确认可用。
2. 确认 `admin`/`admin123` 及所有演示账号均 `401`（生产无 seed）。
3. 确认登录限流：同一标识符连续失败返回 `429`（`LoginThrottleService`，默认 5 次/300s 窗口/900s 锁）。
4. **轮换 bootstrap 凭据并清空 env**：无 schema 强制改密（零 schema 变更），手动经正常改密流程或 DBA 改密、存入 secret manager，然后从部署 env **移除** `BOOTSTRAP_ADMIN_*` 并重启，使密钥不再驻留进程 env。runner 此时已 no-op，但清 env 是为消除残留 secret。

---

## 4. schema 重置 / 历史遗留 DROP 的受控执行

### 4.1 两条装配路径（由 `baseline-on-migrate` 协调）

| 路径 | 触发场景 | Flyway 行为 |
|------|----------|-------------|
| **fresh-V1** | 空库（生产 Dockerfile.prod，或 `down -v` 后冷启动） | 无 `flyway_schema_history` 且无 schema → 不 baseline，**正常执行 V1** 建全量 schema |
| **initdb+baseline** | demo/CI（`db/Dockerfile` 已经 `db/initdb` 装好 schema+种子） | schema 已存在但无 `flyway_schema_history` → Flyway **建 history 表并把 V1 记为 baseline，不重跑 V1**；后续 V2+ 正常增量 |

这是 base 与 prod 两条镜像路径并存的协调机制，`baseline-version: 1` 是关键。

### 4.2 dev vs 生产

- **dev / demo / CI**：删库重拉即生效。演示重置（含重新装种子）：
  ```sh
  docker compose down --remove-orphans --volumes --rmi local
  docker compose up -d --build
  ```
  此路径**故意清空并从 initdb 重新装种子**——演示可接受，**生产绝不可用**。
  > 改 `db/initdb/` 任何 seed 后必须 `cd backend && ./gradlew cleanTest test`（seed 整目录是 testcontainer 集成测试 fixture，不在 `test` 输入会被判 UP-TO-DATE 不重跑）。
- **生产 schema 重置**：受控、维护者执行，详见既有 `docs/runbook-production-reset.md`。核心是**清空残留旧种子卷 → Flyway 重建 schema-only → bootstrap 单一 SUPERADMIN**：
  ```sh
  # prod 栈不可在常规部署前 down --volumes（保留数据卷是该栈要点）
  # 仅"彻底重置"时（每步须维护者批准）：
  docker compose -f docker-compose.yml -f docker-compose.prod.yml down
  docker volume rm <project>_postgres_data   # docker volume ls 查确切名
  # 设 BOOTSTRAP_ADMIN_* + 必填 secret 后重启（见 §2.3 / §3）
  ```

### 4.3 破坏性约束

- **删除 / 迁移 / schema / 部署须维护者逐个批准**；生产 schema 操作前先 `pg_dump` 备份（见 §5）。
- 未来引入新迁移（V2+）须遵循 Flyway 前向迁移；任何历史遗留 DROP / 数据迁移**写成 V2+ 迁移**而非手改 V1 baseline（baseline 已 squash，重写会破坏 initdb+baseline 路径的 checksum 一致性）。

---

## 5. 回滚 / 校验

### 5.1 部署后健康检查

- **后端 readiness**：`GET /api/v1/auth/csrf` 返回 `200`（permitAll，匿名可访问，release.yml 即以此为 readiness 闸）。
- **前端**：HTTP 根 `/` 返回 200（生产经 Caddy → 443）。
- **容器状态**：`docker inspect --format='{{.State.Status}}' backend` 为 `running`；`db` healthcheck `healthy`。

### 5.2 Flyway history 审计

```sh
docker compose -f docker-compose.yml -f docker-compose.prod.yml exec db \
  psql -U kids_user -d kids_postgres_db \
  -c "SELECT version, description, type, success, installed_on FROM flyway_schema_history ORDER BY installed_rank;"
```
- 空库首发应见 `version=1`、`type=SQL`、`success=t`；
- initdb+baseline 路径应见 `version=1`、`type=BASELINE`（V1 未重跑）。

### 5.3 出问题时的回退要点

- **schema 重置失败**：从 §5 step-1 的 `pg_dump` 备份恢复进新卷再重启（备份保留到验证通过为止）。
  ```sh
  docker compose -f docker-compose.yml -f docker-compose.prod.yml exec db \
    pg_dump -U kids_user kids_postgres_db > backup-$(date +%F).sql
  ```
- **镜像回退**：`:version` 是不可变 tag；要回退把目标 `:<version>` 重 tag 为 `:prod` 重推，watchtower 会拉回。bootstrap / 限流行为是加法式：清 `BOOTSTRAP_ADMIN_*` env 与软化 `LOGIN_THROTTLE_*` 阈值即可软关闭。
- **Flyway validate 失败**（entity 与 schema 不一致，`ddl-auto: validate` 会拒启）→ 检查迁移与 entity 是否对齐，不要手改 DB 绕过。

---

## 6. 安全 invariants 提醒（与部署相关）

1. **会话式认证**（Spring Session + Redis），**无 JWT**；不要为前端引入无状态 token。授权每请求重解析，角色/状态撤销下一请求即生效。
2. **CSRF 对所有写请求强制**（`CookieCsrfTokenRepository.withHttpOnlyFalse`，前端回填 `X-XSRF-TOKEN`）；**唯一豁免 = `/api/v1/internal/**`（用 Bearer `AI_SERVICE_TOKEN`）**。不要把会话端点塞进 internal 前缀或 CSRF 豁免。
3. **default-deny**：`anyRequest().authenticated()` 兜底，公开白名单极小（如 `/api/v1/auth/csrf`）。
4. **RRN 单向哈希**（HMAC-SHA256 + pepper，列名 `rrn_hash`）与**摄像头流凭据 AES-256-GCM 可逆 + 版本化**（`CAMERA_STREAM_AES_KEY_V1`）**不可混用**；RRN 不落明文、不打日志。
5. **AES key 只进 Java backend，绝不下发 AI 容器**；`AI_SERVICE_TOKEN` 两栈同值。
6. **密钥全部 `${ENV}` 注入 + fail-fast**；`.env.example` 只放占位，**绝不提交真值**；secret/PII 绝不入日志/审计/异常；生产 `LOG_LEVEL_ROOT=INFO`。
7. **TLS 在 Caddy 边缘终止**（prod overlay）；`SESSION_COOKIE_SECURE=true` 依赖此 HTTPS 边缘。Caddy 全局 `encode gzip` 会缓冲 SSE 帧，SSE 路径需在 Caddyfile 排除 gzip。

---

### 附：与 CLAUDE.md 描述不符之处（核实发现）

- **Flyway 迁移数**：CLAUDE.md「跨组件契约」段称 *"Flyway 到 **V12** + db/initdb baseline"*，但实际迁移目录**只有单一 `V1__initial_baseline.sql`**（DB-1 squash 已落地，`baseline-version: 1`）。本 runbook 以**实际单一 V1 baseline**为准；建议同步修订 CLAUDE.md。
- **既有 reset runbook 路径**：`docker-compose.cd.yml` / `.prod.yml` 注释引用的是 `docs/runbook-production-reset.md`（已存在）。本新文档 `docs/runbooks/production-deploy-and-schema-reset.md` 是其超集；若以本文档为正本，建议后续把 compose 注释指向本文件以免双源漂移。
