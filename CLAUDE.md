# AI Kids Care

**多租户幼儿园 AI 安全看护平台**（长期生产系统，monorepo）。核心价值是一条告警闭环：
**CCTV → AI（VideoMAE）实时检测异常行为 → 教职员复核 → 通知家长**。附带教职员/家长沟通工具（公告、感谢信、通知收件箱）、园所运营数据管理、儿童关系图查询。

> **闭环状态**：ADR-0015 V1 链路（AI 检测 → `POST /api/v1/internal/detection-{sessions,events}` → SSE + 通知，backend 为 `detection_events` 唯一写入者）**两侧已实现**；AI 子栈以**长生命周期 supervisor**（`scripts/stream_supervisor.py`，多摄像头 process-per-stream）运行检测→ingest 循环，与 :8001 FastAPI 推理端点**并存**。supervisor 经只读 `GET /api/v1/internal/streams`（`ROLE_AI_SERVICE`）枚举活跃流并 reconcile；实时 `detection_events` 由**后端从 AI 推理写入**（非 seed）。注：worker/supervisor 的 compose service（`ai/docker-compose.yml`）是部署面变更，须经维护者批准上线。

---

## 工作范式

- **「做什么」用 OpenSpec**：能力沉淀在 `openspec/specs/`；变更走 `propose → apply → archive`。项目上下文见 `openspec/config.yaml`。起步：`/opsx:propose "<idea>"`；实现：`/opsx:apply`。
- **「怎么可靠地做」用 superpowers 技能**：brainstorming / writing-plans / executing-plans / test-driven-development / verification-before-completion / requesting-code-review / using-git-worktrees / finishing-a-development-branch 等。
- **优先级**：用户显式指令 > superpowers 技能 > 默认行为。
- **破坏性任务**（删除 / 迁移 / schema / 部署）须在 apply 前经维护者**逐个批准**。提案须写明 Why 与 Non-goals。

---

## 技术栈

| 组件 | 技术 | 关键版本 |
|------|------|----------|
| `backend/` | Spring Boot REST API + Spring Security + Spring Session(Redis) + Data JPA/Hibernate + Flyway + MapStruct + Neo4j Java Driver | Java 21, Spring Boot 3.2.5, Gradle 8.5 |
| `frontend/` | Next.js（`output: 'export'` 纯静态导出，无 SSR）+ React + Redux Toolkit(RTK Query) + Axios + Tailwind | Next 16.1.6, React 19.2.3, Tailwind 4.2.1, TS 5 |
| `ai/` | FastAPI 推理服务（端口 8001）+ VideoMAE（HuggingFace Transformers）+ PyTorch + PyAV；独立 compose 栈 | Python ≥3.12, FastAPI 0.115.12 |
| `db/` | PostgreSQL（system-of-record）+ Neo4j（只读派生关系图）+ Redis（会话/限流） | Neo4j 5.19, Redis 7 |
| `infra/` | Caddy 边缘反代（生产 ACME TLS 终止） | Caddy 2 |
| `e2e/` | Playwright（发版门禁） | — |

包管理：backend=Gradle、frontend=npm、ai=uv、e2e=npm。

---

## 架构与约定（写代码前必读）

**Backend 包结构按层（非按功能）平铺**，根包 `com.ai_kids_care.v1`：
`controller`（仅路由分发，只注入 service）→ `service`（业务逻辑 + `@PreAuthorize` + `@Transactional`）→ `repository`（JPA；`GraphRepository` 是唯一手写 Cypher）+ `mapper`（MapStruct，entity↔VO/DTO）。另有 `entity / dto / vo / event / internal / security / type / bootstrap / config`。所有功能域混在同层，无 feature module。
- **命名**：输入 `XxxCreateDTO`/`XxxUpdateDTO`，持久化 `Xxx`(entity)，响应 `XxxVO`。MapStruct `unmappedTargetPolicy=ERROR`；Update 用 `NullValuePropertyMappingStrategy.IGNORE` 实现 PATCH。
- **JPA**：`ddl-auto: validate`、`open-in-view: false`。
- **方法级授权**：`@PreAuthorize("@authorizationPolicy.isAllowed(...)")` 标在 **service** 方法上（非 controller）。

**多租户隔离（最高权重约束）**：按 `kindergarten_id` 隔离，**靠 ThreadLocal 链**而非 URL 参数。`EffectiveAuthorizationContextFilter` 每请求从 DB 重建 `EffectiveAuthorizationContext`（含 `activeKindergartenId`）存入 ThreadLocal；service 调 `EffectiveAuthorizationContextHolder.requireActiveKindergartenId()` 取值。**前端绝不传 kindergartenId**。租户过滤的 `kindergarten_id` 谓词**必须写进 JPQL/SQL/Cypher**，禁止「加载后过滤」。跨租户/不可见资源**一律 404**（隐藏存在性），不返回 403/200。

**两类 Spring 事件，用法不同（新增 event 须遵循分类）**：
- `DetectionEventIngestedEvent` → `@Async @EventListener`：ingest 是 autocommit 无事务，**不能**用 TransactionalEventListener。
- `EventReviewedEvent`（record，payload 在 review 事务内 eager 预载关联）→ `@Async @TransactionalEventListener(AFTER_COMMIT)`：防异步线程无 persistence session 时懒加载失败。

**异步与性能约束**：所有 `@Async` 共用 Boot `applicationTaskExecutor`（core=8/max=16/queue=200 有界/CallerRunsPolicy，`application.yml`）；独立吞吐场景须声明命名 Executor + `@Async("bean")`。外部 HTTP（Pushover/SMS）**必须在事务边界外并设超时**（5s）；通知投递用 `REQUIRES_NEW` 把 DB 写拆成短事务、provider 调用夹在中间不占连接。AFTER_COMMIT 异步监听器里**禁用懒代理**，用 `findAllById` 批量预载。

**SSE 实时看板**：`GET /api/v1/detection-events/stream`（text/event-stream，会话认证 + 租户域），前端原生 `EventSource`。事件名 `detection-event`，**心跳 25s / 流寿命 30min / Last-Event-ID replay 上限 200**。注册表是**进程内** `ConcurrentHashMap<kindergartenId, Set<SseEmitter>>` → **单实例假设**，多实例 fanout（Redis pub/sub）是 follow-up。

**前端**：App Router（`src/app/`），API 调用层全在 `src/services/apis/`，**双 HTTP 客户端并存**——RTK Query(`baseApi`) 与 Axios(`apiClient`)，两者均在拦截器注入 CSRF 头。Redux store 两个 reducer：`api`(RTK Query 缓存) + `user`(认证态)。会话恢复靠 `SessionBootstrap` 调 `GET /api/v1/auth/session`。

---

## 跨组件契约

- **REST 基线**：所有业务 API 在 `/api/v1/**`，Cookie 会话 + CSRF 头（非 Bearer）。分页 Spring `Page` ↔ 前端 `PageResponse`。
- **AI → backend ingest**：`POST /api/v1/internal/detection-{sessions,events}`，**Bearer `AI_SERVICE_TOKEN`（ROLE_AI_SERVICE）**；payload camelCase 两侧对齐；幂等键 `dedupKey="{streamId}-{epochSec}"` 按 `(kindergarten_id, dedup_key)` 去重；AI 侧 bounded retry 3 次/指数退避/10s 超时。**backend 是 detection_events 唯一写入者**（ADR-0026）。
- **数据存储三分**：**PostgreSQL** 权威（JPA `validate` + Flyway 单一 `V1__initial_baseline.sql` 基线〔DB-1 已 squash V1–V12，未来从 **V2** 续起〕，`db/initdb/01_create_schema.sql` 与 baseline 同源自 `db/dbml/schema.dbml`，靠 `baseline-on-migrate` 协调两条装配路径）；**Neo4j** 是关系图**只读派生副本**（原生 Driver + Cypher，靠 compose `data-loader` 从 PG 装填，**不含 PII**）；**Redis** 管 Spring Session（租户上下文载体）+ 登录限流，无业务缓存。
- **enum 单一真源**：`GET /api/v1/enums/{name}`（ADR-0013 退役 `common_codes`/`menus`，label 归前端 i18n）；改 enum 须同步 DB / 后端 `type.*` / 前端三处。
- **内部事件链**：ingest → `DetectionEventIngestedEvent` → SSE 推送；review → `EventReviewedEvent`(AFTER_COMMIT) → `GuardianNotificationService` 家长通知。
- **通知渠道**：PUSH(Pushover via `push_subscriptions`) + SMS(Solapi via `users.phone`)；EMAIL 已注册未实现。教职员告警 ingest 后即发；家长通知须复核后（ESCALATED 穿透 / RESOLVED 受 quiet_hours 约束，DEFERRED 由 `@Scheduled` 60s 扫描器补发）。

---

## 安全 invariants（不可违背；也用于避免误报）

1. **会话式认证（Spring Session + Redis），无 JWT**；不要为前端用户引入无状态 token。授权决策不信任会话快照——每请求重解析、角色/状态撤销下一请求即生效。
2. **CSRF 对所有写请求强制**（`CookieCsrfTokenRepository.withHttpOnlyFalse`，前端回填 `X-XSRF-TOKEN`）；**唯一豁免** = `/api/v1/internal/**`（用 Bearer）。不要把会话端点塞进 internal 前缀或 CSRF 豁免。
3. **default-deny 是设计**：`anyRequest().authenticated()` 兜底，公开白名单极小且按方法精确限定。新端点自动受保护——「端点未配置」是预期，不是 bug。
4. **RRN 单向哈希**（HMAC-SHA256 + pepper，不可逆；列名 `rrn_hash`，历史误名 `rrn_encrypted`）；**摄像头流凭据 AES-256-GCM 可逆 + 版本化**。两种机制**不可混用**。RRN 不落明文/不打日志。
5. **密钥全部 `${ENV}` 注入 + fail-fast**（`@NotBlank`/`@NotEmpty`）；`.env.example` 只放占位，绝不提交真值。secret/PII（RRN、密码、token、session id、raw identifier、请求 body）绝不入日志/审计/异常。
6. **测试占位 / demo ≠ 生产漏洞**：`test-pepper-not-secret-2026`、demo 密码 `admin123` 仅存在于 test/seed；生产 DB 是 Flyway schema-only **无 seed**。报这些为「硬编码 secret / 弱口令」是假阳性。
7. **冷启动管理员** `AdminBootstrapRunner`（env-gated + 空表 + 幂等 + 拒绝 `admin` + 不打密码）与**登录限流** `LoginThrottleService`（Redis 计数 + TTL 锁 → 429，key 用 SHA-256 哈希不存 raw identifier）是受控机制，勿削弱、勿误报。

---

## 构建 / 测试 / 运行

```bash
# 全栈本地（含 initdb 种子）
docker compose up -d --build

# Backend：测试需 Docker（testcontainers 自起 PG+Redis）
cd backend && ./gradlew test --no-daemon --stacktrace
cd backend && ./gradlew bootJar          # 构建 fat jar
# 本机无 Java → testcontainers 走 DooD：挂仓库根(非 backend) + TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal

# Frontend：本机有 node
cd frontend && npm run lint && npm run build    # next build → /out（静态导出）

# AI：CI 仅装精简依赖（无 torch）
cd ai && PYTHONPATH=src python -m pytest tests/ -v

# E2E：仅 release.yml 中运行
cd e2e && npx playwright test
```

- **改 `db/initdb/` 任何 seed 后必须 `./gradlew cleanTest test`**——seed 整目录即 testcontainer 集成测试 fixture（`BaseIntegrationTest` 用 `withCopyFileToContainer`），但不在 `test` task 输入，否则被判 UP-TO-DATE 不重跑。
- **本地 pre-push lint**：`git config core.hooksPath .githooks`（每次新克隆执行一次）；hook 仅在 push 含 `frontend/` 改动时跑 ESLint（本地 node 优先，回退 `node:20` 容器）。
- **必填环境变量**（无弱默认，缺失则 compose 报错，从 `.env.example` 复制）：`POSTGRES_PASSWORD`、`NEO4J_PASSWORD`、`REDIS_PASSWORD`、`RRN_HASH_PEPPER`、`CAMERA_STREAM_AES_KEY_V1`（32 字节 Base64）、`AI_SERVICE_TOKEN`。
- **演示登录**：所有 demo 账号统一密码 `admin123`（来源 `db/initdb/` seed，见 `docs/demo-accounts.md`）。

**CI（`.github/workflows/`）**：`backend-java-tests` / `ai-tests` / `frontend-lint-build` / `compose-config`（develop/main 触发）；`release.yml` 构建 4 镜像 → `docker compose` smoke → 等 `/api/v1/auth/csrf` 200 → **Playwright E2E 硬门禁** → 推 `:version`（`:prod` 需 GitHub Environment 人工审批）。**后端 Gradle test 不在 release 链路**。

---

## 分支 / 发布 / CD

- `develop` = 集成 trunk（直接提交）；`main` = 受保护发布线，经 `develop → main` PR。
- 发布门：GitHub Actions（Compose Config）+ code-owner 批准 + 全新独立评审 + 维护者 merge。
- 发布后 `release.yml` 构建 + 冒烟 + E2E +（production 环境批准后）推 `:prod`，远程 **watchtower** 轮询部署。
- Compose 分层：`docker-compose.yml`（基础/含种子）+ `.prod.yml`（Caddy TLS、Dockerfile.prod 无种子、Secure cookie）+ `.cd.yml`（GHCR 镜像 + watchtower）。

---

## 本机环境与已知陷阱

- **本机**：Windows，仓库在 `C:\ai-kids-care`（无 D 盘）；**node 已在 PATH**（前端 lint/build 可本地跑）；**无 Java** → 后端 testcontainers 走 DooD 容器。Claude hook 解释器仅 git/powershell（node 不在 hook PATH、bash=WSL）。
- **前端整树在 `.gitignore` 中** → `Grep`/`Glob` 会静默跳过前端文件；需用裸 `rg --no-ignore`（Bash）核实前端代码。
- **REST 路径命名不统一**：`detection-events`（连字符）vs `detection_sessions` / `cctv_cameras`（下划线）——以实际 controller 为准。
- **Caddy 全局 `encode gzip` 会缓冲 SSE 帧**，最坏延迟退化到心跳间隔（25s）；SSE 路径需在 Caddy 排除 gzip。
- **时区**：全部服务 `TZ=Asia/Seoul`，JVM 加 `-Duser.timezone=Asia/Seoul`。

---

## 语言策略

- 用请求所用语言与维护者沟通；持久工程文档**简体中文为主、英文术语为辅**。
- 保留代码标识符、API 路径、enum 值、数据库名、韩语产品文案不变。

---

> 组件多角度分析、代码审查等能力由对应 skill 按需自触发，不在本文件登记。harness 自身的设计/变更历史见 OpenSpec change 与各 skill 文件。
