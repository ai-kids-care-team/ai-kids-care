# 演进路线（Roadmap）

> 本文档跟踪**已 Accept 的 ADR 的实施进度**，并显式列出会阻断后续实施的 Proposed 决策门；已接受 ADR 的「落地范围」展开为可勾选清单。每篇 ADR 落地遵循 [`CLAUDE.md`](../../CLAUDE.md) 的会话规则——**单一目标、改动小、可独立评审、仓库始终可工作**。
>
> 基线：2026-05-29（5 篇前瞻 ADR Accept 当日）。本文从 placeholder 升级为实际路线图。
>
> **修订（2026-06-14）**：[ADR-0019 服务端有效授权上下文与租户强制边界](../decisions/adr/ADR-0019-effective-authorization-context-tenant-enforcement.md) 已由维护者 Accept，SPEC-0001 Phase 2/3 决策门解除。后续 session principal、Effective Authorization Context、平台 tenant context 与 tenant enforcement 必须按该 ADR 分阶段落地。
>
> **修订（2026-06-07，接手复核后）**：新增 [ADR-0014 测试基线](../decisions/adr/ADR-0014-test-baseline.md) 与 [ADR-0015 AI 检测闭环](../decisions/adr/ADR-0015-ai-detection-closed-loop.md)，**均于 2026-06-07 Accept、实现委派独立 session**（0015 = V1：AI 直写 PG + 后端 LISTEN/NOTIFY，通知复核后发家长，并勘误 ADR-0002/0006 中"后端唯一写入者/AI 不连库"的误记）；并把"全局异常处理（OQ-ARCH-2）""keyword 空操作（OQ-ARCH-4）"两项低成本高杠杆改动排入次序。另：**ADR-0016 服务端会话鉴权**（Spring Session + Redis，取代 ADR-0007）亦于 2026-06-07 Accept、**排在 ADR-0009 之前**、实现委派独立 session。**ADR-0017（TLS/HTTPS，由 0016 硬触发）与 ADR-0018（通知子系统）** 亦于 2026-06-07 Accept、实现委派独立 session。状态列已标明。

## 状态总览

| 顺序 | ADR | 决策摘要 | 状态 | 复杂度 |
| --- | --- | --- | --- | --- |
| 1 | [ADR-0011](../decisions/adr/ADR-0011-extract-codegen-subproject.md) | codegen → `pg-spring-crud-codegen/` 仓内迁址 | ✅ **Implemented (2026-05-29)** → **Superseded by ADR-0027 (2026-06-18)** | 小 |
| 2 | [ADR-0014](../decisions/adr/ADR-0014-test-baseline.md) | 测试基线（Testcontainers PG + characterization） | ✅ **Implemented (2026-06-08)** | 中 |
| 3 | [ADR-0012](../decisions/adr/ADR-0012-production-data-lifecycle.md) | 演示重置 vs 生产数据生命周期 + Flyway | ⚠️ **Partial**：迁移已落地，生产 loader 仍有竞态/快照问题 | 中 |
| 4 | [ADR-0013](../decisions/adr/ADR-0013-dictionary-tables-governance.md) | `menu` → C 静态；`common_codes` → β 后端 enum 端点 + 前端 i18n | 📋 Backlog | 中 |
| 5 | [ADR-0010](../decisions/adr/ADR-0010-rrn-one-way-hash.md) | RRN HMAC-SHA-256 + pepper（替代 BCrypt+候选集） | 📋 Backlog | 中-高 |
| 6 | [ADR-0019](../decisions/adr/ADR-0019-effective-authorization-context-tenant-enforcement.md) | Effective Authorization Context、集中 tenant enforcement 与平台 tenant context | 🔄 **In Progress (PR #89, 2026-06-15)**：phase 2-5 部分已合；Guardian 关系策略 / 审计 / 全量主动吊销 deferred | 高 |
| 7 | [ADR-0016](../decisions/adr/ADR-0016-server-side-session-auth.md) | 服务端会话鉴权（Spring Session + Redis，**取代 ADR-0007**） | ✅ **Implemented (PR #89, 2026-06-15)** | 中 |
| 8 | [ADR-0017](../decisions/adr/ADR-0017-tls-https-termination.md) | TLS/HTTPS 终结与强制（ADR-0016 `Secure` cookie 硬前置） | 🔄 **In Progress (PR #89)**：Caddy 边缘 TLS + 生产 Secure cookie 草案，端到端待部署验证 | 中 |
| 9 | [ADR-0009](../decisions/adr/ADR-0009-restore-auth-enforcement.md) | 恢复鉴权强制（机制改为 **session**，见 ADR-0016） | ✅ **Implemented (PR #89, 2026-06-15)**：默认拒绝 + 每请求授权强制 | 高 |
| 10 | [ADR-0018](../decisions/adr/ADR-0018-notification-subsystem.md) | 通知子系统（后端发、**家长复核后**通知） | ✅ **Accepted (2026-06-07)（实现委派独立 session）** | 中 |
| 11 | [ADR-0015](../decisions/adr/ADR-0015-ai-detection-closed-loop.md) | AI 检测闭环集成契约（终态；V1 AI 直写 PG + 后端 LISTEN/NOTIFY） | ✅ **Accepted (2026-06-07)，终态（实现委派独立 session）** | 高 |

## 实施次序（2026-06-14 修订）

**0011 ✅ → 0014 ✅ → 0012 ⚠️ Partial → 0013 → 0010 → 0019 🔄 → 0016 ✅ → 0017 🔄 → 0009 ✅ → 0018 → 0015**（按风险递增 + 依赖关系排序）

> **进度更新（2026-06-15，PR #89 合入 develop）**：0016（服务端会话）与 0009（默认拒绝 + 每请求授权强制）已 **Implemented**；0019（Effective Authorization Context + 租户强制 + 平台 tenant-context + Teacher assignment 策略 + 会话吊销）与 0017（Caddy 边缘 TLS + 生产 Secure cookie 草案）为 **In Progress**。仍 deferred：Guardian 关系策略（待开放 guardian 资源）、状态变更主动吊销（待 admin 管理端点）、安全审计（待 ADR-0012 schema 迁移）、TLS 端到端（部署时）。

> 原次序（2026-05-29 已确认）：`0011 → 0012 → 0013 → 0010 → 0009`。本次修订**前插 0014（测试基线）；在 0009 前插入 0016（会话机制）+ 0017（TLS）；在 0015 前插入 0018（通知子系统）；后接 0015（AI 闭环）**，未改动中间四篇的相对顺序。

| 依赖说明 | 影响顺序 |
| --- | --- |
| 0011 是纯结构调整（零行为风险） | 排第一，作为 Implementation 工作流的安全起点 |
| **0014 测试基线是横切前置** | 改为**第二**：0012/0013/0010/0009 均为高 blast-radius 变更，须在有回归保护下进行（[ADR-0014](../decisions/adr/ADR-0014-test-baseline.md) 背景） |
| 0013 / 0010 涉及 schema 变更 | 必须排在 0012（迁移基础设施）之后 |
| 0010 引入 pepper 密钥管理范式 | 0009 复用同一路径，故 0010 在前 |
| **0019 是 0016/0009 的授权上下文决策门** | 已于 2026-06-14 Accept；session principal、role/scope 解析、平台 tenant context、tenant-aware repository 与 401/403/404 按该 ADR 实施 |
| **0016 会话机制（session）先于 0009** | 0009 直接按 session 落地，避免"先恢复 JWT 再返工"；0016 引入 Redis（已有 compose）、不改 schema、不依赖 0012 |
| **0017 TLS（由 0016 触发）** | `Secure` cookie 需 HTTPS → 生产部署前置；与 0012 生产部署、0016 耦合，排 0016 后 |
| 0009 翻转鉴权（最高风险） | 紧随 0016/0017；其测试前置已由 0014 提前满足 |
| **0018 通知子系统（后端发、复核后）** | 闭环"通知"步依赖它，排 0015 前/同期；放宽 `notifications` NOT NULL 走 0012 迁移 |
| **0015 AI 闭环（V1：AI 直写 PG）** | 排**最末**为终态；V1 用 DB 凭据直连、**不依赖 0009 后端鉴权**；通知由 0018 子系统发；实现待加固轨完成（OQ-AI 前置已解） |

---

## 待办清单（按 ADR 展开）

### ✅ ADR-0011 codegen 迁址 — 已完成（2026-05-29）

- [x] 14 个文件 `scripts/codegen/*` → `pg-spring-crud-codegen/*`
- [x] `pg-spring-crud-codegen/docker-compose.yml` 相对路径 `../../db` → `../db`
- [x] 新增 `pg-spring-crud-codegen/README.md`（自洽子工程说明）
- [x] 新增 `scripts/codegen/README.md`（软指针）
- [x] ~~`CODEOWNERS` 增补 `/pg-spring-crud-codegen/` 条目~~ **（2026-06-18）** `CODEOWNERS` 已整体删除（所引用 GitHub 团队不存在）
- [x] 13 处内部引用切换（root READMEs、docs/architecture/*、ADR-0004 等）

**未做**（按 ADR 范围）：未拆出独立 git 仓库——保持仓内迁址形态；后续 `git filter-repo` 带史拆出留待单独任务。

**退役（2026-06-18）**：[ADR-0027](../decisions/adr/ADR-0027-retire-pg-spring-crud-codegen.md) 退役并删除 `pg-spring-crud-codegen/`；ADR-0011 被 ADR-0027 取代。

---

### ✅ ADR-0014 测试基线 — Implemented（2026-06-08）

> 详见 [ADR-0014](../decisions/adr/ADR-0014-test-baseline.md)。范围刻意收窄为"薄而可工作的基线"。

- [x] backend 加 Testcontainers（PostgreSQL）依赖；确认 CI 节点可用 Docker（Jenkins agent socket/DinD）
- [x] 建集成测试基类：一次性 Postgres 容器 + 真实 `db/initdb/*.sql` 建库 → 顺带守护 `ddl-auto=validate`
- [x] 首批 characterization 测试：4 个真实认证端点（`/auth/login` `/refresh` `/register`、`GET /auth/register/availability`）+ 3 个代表性 CRUD 读路径（`GET /detection_events` × 3）
- [x] `Jenkinsfile` 在部署 stage **之前**加 `./gradlew test` 门禁
- [x] 文档同步：`engineering/testing.md` 全量重写；ADR-0014 状态更新为 Implemented
- [ ] ADR-0009 测试前置依赖解除（随 ADR-0009 落地时确认）
- [x] **显式不做**：全量覆盖、前端/AI 测试栈、E2E（后续）

---

### ⚠️ ADR-0012 演示重置 vs 生产数据生命周期 — Partial

> 选型：Flyway（Spring Boot 3.2.5 / Flyway 9.22.3）。详见 [ADR-0012](../decisions/adr/ADR-0012-production-data-lifecycle.md)。

- [x] backend `build.gradle` 加 `org.flywaydb:flyway-core` 依赖（版本由 Spring Boot BOM 管理）
- [x] 创建 `backend/src/main/resources/db/migration/V1__initial_baseline.sql`（内容 = 当前 `01_create_schema.sql`，作为基线）
- [x] `application.yml` 配置 `spring.flyway.enabled=true`、`baseline-on-migrate=true`、`baseline-version=1`
- [x] `docker-compose.prod.yml`（新建）+ `db/Dockerfile.prod`（新建）：生产 profile 无 initdb seed；演示路径（`docker-compose.yml`）保持不变
- [x] `Jenkinsfile` 区分 demo CI（保留 `down --volumes` + 重建）vs 生产部署（注释说明 `docker-compose.prod.yml` 命令）
- [x] 文档同步：`operations/{deployment,configuration,runbook}.md`、`data-architecture.md §3`、`ADR-0004` 链入迁移流程
- [x] 测试：`FlywayMigrationTest`（新建，独立新鲜容器验证 V1 在空库执行正常 + `ddl-auto=validate` 通过）
- [x] **后续 Design 议题已记录**：备份/恢复 + 回滚策略（OQ-OPS-4，未决）见 `deployment.md §回滚`；Neo4j 生产再同步策略（OQ-DATA-1，未决）关联 ADR-0012
- [ ] production compose 中 `data-loader` 必须等待 Flyway 完成，或从生产 profile 移除
- [ ] Neo4j loader 改为可验证的 PG 最小投影，移除密码/RRN/地址等非必要字段

**迁移撰写工作流工具（2026-06-08 补完）**：
- [x] 选型 **migra**（PostgreSQL 专用 Python diff 工具）；`db/scripts/requirements-migra.txt` 记录依赖
- [x] `db/scripts/generate_migration.py`：DBML 导出 SQL → 临时 PG 容器 → migra diff → `V{N}__desc.sql` 草稿（带人工评审提示）
- [x] `backend/build.gradle` 新增 `generateMigration` Gradle 任务（`./gradlew generateMigration -Pdesc=...`）
- [x] `docs/engineering/database-guide.md` 补全端到端工作流（DBML → initdb 快照 → 生成草稿 → 人工评审 → JPA 同步 → Testcontainers 验证 → 提交）

**未做（有意）**：
- 未添加 Flyway Gradle 插件（无 `./gradlew flywayMigrate` 需求；Spring Boot 自动运行迁移）
- `docker-compose.yml` 本体未改动（演示/CI 路径零破坏）

---

### 📋 ADR-0013 字典表治理

#### `menu` → C 静态

- [ ] `frontend/src/config/menu.ts` 新建（TypeScript 类型化菜单树，单条声明可见角色集合，消除 `(menu × role)` 笛卡尔展开）
- [ ] `frontend/locales/<lang>/menu.json` 新增菜单 i18n 键（`ko` 必出货，对齐 [ADR-0008](../decisions/adr/ADR-0008-language-governance.md)）
- [ ] 删除 `frontend/src/services/apis/menu.api.ts`
- [ ] 改造调用方（角色化导航组件）从本地配置 + i18n 读取
- [ ] 删除后端 `MenuController` / `MenuService` / `MenuVO` 及相关 mapper/repository（如有）
- [ ] 从 `db/initdb/` 移除 `02_menu.sql`
- [ ] 文档同步：`features.md §6`、`api/rest-endpoints.md`、`architecture/frontend-architecture.md §5`、`architecture/data-architecture.md`

#### `common_codes` → β 后端 enum 元数据端点 + 前端 i18n

- [ ] 新增 `EnumMetadataController`：`GET /api/v1/enums/{name}?context=<table>`，从 `com.ai_kids_care.v1.type.*` 包反射返回 `[{value, labelKey}]`
- [ ] 删除 `CommonCodeController` / `Service` / `Mapper` / `Repository` / `CommonCode` 实体 / `CommonCodeCreateDTO` / `CommonCodeUpdateDTO` / `CommonCodeVO`（共 **8 个后端文件**）
- [ ] `frontend/locales/<lang>/enum.json` 新建（labelKey → 文案）
- [ ] 重写 `frontend/src/services/apis/commonCodes.api.ts` 为 `enums.api.ts`（取 enum 元数据，不再用 24h 缓存等运行时配置策略）
- [ ] 改造调用方（`getParentCommonCodeList`、`getCommonCodes`）为 `useEnum(name, { context, filterBy })` 形态
- [ ] 从 `db/initdb/` 移除 `03_CommonCode.sql`
- [ ] **CI 新护栏**：增加"PG enum 取值 ⊆ Java enum 取值"对照测试（防止收回单一真相后悄悄漂移；同时覆盖 [OQ-DATA-2](open-questions.md) `relationship_enum` 待扩展时的同步问题）
- [ ] 文档同步：`features.md §6`、`api/rest-endpoints.md`、`architecture/data-architecture.md`、`product/glossary.md`

---

### 📋 ADR-0010 RRN HMAC-SHA-256 + pepper

> **前置依赖**：ADR-0012 的 schema 迁移基础设施。

- [ ] 引入 pepper 配置：`application.yml` 新增 `rrn.hash.pepper` 项，env 注入（dev 默认带显眼警告，prod 强制覆盖；与 [ADR-0009](../decisions/adr/ADR-0009-restore-auth-enforcement.md) 的 JWT secret 走同一管理路径）
- [ ] 新建 `RrnHashUtil`：`HMAC_SHA256(pepper, rrn_first6 + back7)` → base64
- [ ] Schema 变更（走 ADR-0012 迁移）：`rrn_encrypted` 列改名为 `rrn_hash`；添加 `UNIQUE(rrn_hash)` 约束
- [ ] DBML 同步更新；ERD `.mmd` 由 schema 重新派生（**不手工编辑**）
- [ ] `AuthService.registerGuardian` / `registerTeacher`：用 `RrnHashUtil` 替换 `passwordEncoder.encode()` 写入 `rrn_hash`
- [ ] `ChildrenService.getChildEntityByRRN`：改为 `WHERE rrn_hash = ?` 单次等值查询（取代 candidate set + BCrypt 逐条匹配）
- [ ] **数据迁移**（生产）：既有 BCrypt 数据无法直接转 HMAC——过渡期**双读单写**：先按 HMAC 命中，未命中回退候选集 + BCrypt 匹配；用户/儿童下次提供完整 RRN 时把 HMAC 列回填。具体步骤建议另立**子 ADR（编号顺延，如 ADR-0019；注：0014/0015/0016/0017/0018 已分别用于测试基线 / AI 闭环 / 会话鉴权 / TLS / 通知子系统）**固化
- [ ] 文档同步：`security-architecture.md §4`、`data-architecture.md §6`、`product/glossary.md`

---

### ✅ ADR-0016 会话机制（服务端 session）— 先于 0009（Accepted 2026-06-07，实现委派独立 session）

> 详见 [ADR-0016](../decisions/adr/ADR-0016-server-side-session-auth.md)。取代 ADR-0007（JWT）；产品未上线，greenfield 零迁移。
>
> **✅ 已实现（PR #89，2026-06-15）**：下列各项均已落地——Redis Spring Session、`SecurityConfig` 会话鉴权（去 JWT）、cookie 安全属性 + CSRF token、前端去 token 化、characterization 测试、文档同步。

- [ ] backend 加 `spring-session-data-redis`；Redis 并入主 `docker-compose.yml`（复用 `db/redis-docker-compose.yml`）
- [ ] `SecurityConfig` 改 session 鉴权（弃用 `JwtAuthenticationFilter`/`JwtUtil`）；`AuthService` 登录建会话、登出 invalidate
- [ ] cookie 安全属性 `httpOnly`+`Secure`+`SameSite`（生产同源 Lax）；启用 Spring Security **CSRF token**（写操作）
- [ ] 前端 `apiClient.ts` 去 token 化（删 token 存储/refresh/401 重放），改 `withCredentials: true` + CSRF 头
- [ ] 落地补 characterization 测试（[ADR-0014](../decisions/adr/ADR-0014-test-baseline.md)）
- [ ] 文档同步：`security-architecture.md`、`operations/*`、ADR-0007 状态注（已加 Superseded 指针）

---

### ✅ ADR-0017 TLS/HTTPS 终结 — 由 0016 触发（Accepted 2026-06-07，实现委派独立 session）

> 详见 [ADR-0017](../decisions/adr/ADR-0017-tls-https-termination.md)。`Secure` 会话 cookie 的硬前置 + 儿童 PII 传输加密；生产部署前置。
>
> **🔄 In Progress（PR #89）**：已选 **Caddy** + 起草边缘 TLS（ACME）+ HTTP→HTTPS + HSTS + 生产 `Secure` cookie + `compose-config` CI + 文档同步；真实证书签发与端到端 HTTPS 待部署时验证。

- [ ] 选定边缘 TLS 终结：自动证书反代（Caddy/Traefik，ACME 自动签发）或扩展现有 frontend Nginx（443 + 证书）
- [ ] HTTP→HTTPS 强制重定向 + HSTS
- [ ] 生产 cookie `Secure`、同源 `SameSite=Lax`（配合 [ADR-0016](../decisions/adr/ADR-0016-server-side-session-auth.md)）；dev 放宽
- [ ] `docker-compose.yml` / 反代配置 + 证书续期
- [ ] 文档同步：`security-architecture.md §5`、`operations/{deployment,configuration}.md`、关闭 OQ-OPS-3

---

### 📋 ADR-0009 鉴权恢复 — After 0016（机制 = session）

> ⚠️ **机制更新（2026-06-07）**：鉴权"恢复"决策不变，但**机制由 JWT 改为 session**（[ADR-0016](../decisions/adr/ADR-0016-server-side-session-auth.md)）。下列 **JWT 专属项作废**（access/refresh 区分、role claim、JWT secret 外部化、`expireSecond` 改名），由 ADR-0016 session 等价项替代；本节保留的是"翻转 `permitAll`→`authenticated` + 公开端点白名单 + 授权集成测试"。

> **必要前置（已部分满足）**：ADR-0014 已建立 Testcontainers 测试基础设施与认证 characterization；翻转鉴权前仍需补齐 session、CSRF、角色和租户边界测试。
>
> **✅ 已实现（PR #89，2026-06-15）**：核心目标"翻转 `permitAll`→`authenticated` + 公开端点白名单 + 授权集成测试"已落地（机制 = **会话**，见 ADR-0016）。下列 **JWT 专属项**（access/refresh、role claim、JWT secret 外部化、`expireSecond` 改名）随 JWT 移除而**作废**，不再适用；`/auth/refresh` 已移除、不在白名单。

- [x] **配套**：搭建 Spring Boot Test + Testcontainers PostgreSQL + `spring-security-test`
- [x] **配套**：为已实现认证端点补首批 characterization 测试
- [ ] `SecurityConfig.java:48,51`：取消 `JwtAuthenticationFilter` 注释；`/api/v1/**` 由 `permitAll()` 改 `authenticated()`
- [ ] 公开端点白名单：`/auth/login`、`/auth/refresh`、`/auth/register`、`/auth/register/availability`、`/swagger-ui/**`、`/v3/api-docs/**`、`OPTIONS /**`
- [ ] access / refresh 区分（OQ-SEC-2）：加 `TokenTypeEnum` claim、独立过期时间；`/auth/refresh` 仅接受 `refresh` 类型 token
- [ ] 令牌加入 `role` / `authorities` claim（支撑授权）
- [ ] JWT secret 外部化（OQ-SEC-3）：生产强制 env 注入；移除 `application.yml:27` 的默认 fallback
- [ ] 字段命名修正：`expireSecond` → `expirationMs`（消除"名 vs 实"单位混淆）
- [ ] 写授权集成测试（带/无 token、过期 token、错误角色 → 401/403）
- [ ] 文档同步：`security-architecture.md §2-3`、ADR-0007 状态注更新（"⚠️ 当前停用" → 已恢复，本 ADR 落地后）

**显式排除（留待后续 ADR）**：运行时多租户隔离（[OQ-SEC-8](open-questions.md)）—— ADR-0009 落地后这个问题变为"代码已能识别用户但 service 不做租户过滤"，建议作为下个 Design 议题。

---

### ✅ ADR-0018 通知子系统 — 先于/同期 0015（Accepted 2026-06-07，实现委派独立 session）

> 详见 [ADR-0018](../decisions/adr/ADR-0018-notification-subsystem.md)。后端拥有；家长复核后通知；移除 AI 演示告警。

- [ ] 后端通知子系统：`LISTEN` 检测 → `notification_rules` → `device_tokens`（Pushover）解析 → 投递 + 失败重试
- [ ] 门控：家长通知须 `event_reviews` 复核确认后发；园方高置信即时预警（阈值细化）
- [ ] **移除** AI 侧 `utils/{pushover,sms}.py` 演示告警（收件人写死、不查 DB）
- [ ] schema：放宽 `notifications` 的 `sent_at`/`fail_reason`/`retry_count` NOT NULL（走 [ADR-0012](../decisions/adr/ADR-0012-production-data-lifecycle.md) 迁移，关闭 OQ-DATA-3）
- [ ] 幂等：复用 [ADR-0015](../decisions/adr/ADR-0015-ai-detection-closed-loop.md) 的 `dedup_key` 防重复通知
- [ ] 文档同步：`features.md`、`api/rest-endpoints.md`、`data-architecture.md`

---

### ✅ ADR-0015 AI 检测闭环 — 终态（Accepted 2026-06-07；V1；实现委派独立 session）

> 详见 [ADR-0015](../decisions/adr/ADR-0015-ai-detection-closed-loop.md)。**集成媒介 = PostgreSQL 直连；concretization = V1（AI 直写核心表）**（维护者定 2026-06-07）；实现排加固轨之后、委派独立 session。

**前置澄清状态**：

- [x] ✅ OQ-AI-2 模型/数据集来源（base=`videomae-base-finetuned-kinetics`；数据=AI Hub 이상행동 CCTV, dataSetSn=171；见 [ai-architecture §1.1](../architecture/ai-architecture.md)）
- [x] ✅ OQ-AI-3 标签→`event_type_enum` 映射（12 类近 1:1 + `OTHER`；仅剩确认模型 `id2label` 字符串）
- [x] ✅ concretization 已定 = **V1（AI 直写核心表）**；V2 列为 Phase 2 运维后升级
- [x] ✅ 证据视频存储：**已定向 = 本地→对象存储、可升级抽象**（`event_evidence_files` 存 `evidence_uri`+hash，URI scheme 可升级；视频不入 PG）
- [ ] AI 获 DB 写入凭据的管理（最小权限账号，与 [ADR-0009](../decisions/adr/ADR-0009-restore-auth-enforcement.md) 密钥范式同路径；V1 不依赖后端鉴权）
- [x] ✅ 通知**已定**：**归属 = 后端**从 DB 发（推理端不发，AI 现有 Pushover/SMS 系临时演示码、落地移除）；**时机 = 面向家长的通知须人工复核确认后发出**；园方高置信即时预警（落地细化）

**实现清单（V1）**：

- [ ] AI 侧新增 DB 写入（首次"AI→DB"）：封装在**单一 detection-sink 模块**后；带 `dedup_key` 幂等；enum 映射/租户解析**各集中一处**（为 Phase 2 受控搬迁预留）
- [ ] 直写 `detection_sessions`/`detection_events`/`event_evidence_files`；如需增列走 [ADR-0012](../decisions/adr/ADR-0012-production-data-lifecycle.md) 迁移
- [ ] 后端 `LISTEN/NOTIFY` 监听核心表 + SSE/WebSocket 推前端；启动扫"未读"行兜底
- [x] ✅ **勘误 [ADR-0002](../decisions/adr/ADR-0002-dual-datastore-postgres-neo4j.md) + [ADR-0006](../decisions/adr/ADR-0006-decoupled-ai-videomae.md)**（2026-06-07：澄清"唯一写入者/AI 不连库"为误记的临时现状，非决策）；落地时再终态更新
- [ ] 落地补 characterization 测试（[ADR-0014](../decisions/adr/ADR-0014-test-baseline.md)）
- [ ] 前端接通 `frontend/src/app/detectionEvents/` 到真实数据
- [ ] 文档同步：`api/ai-service-api.md`、`architecture/{ai-architecture,data-architecture,integration-and-dataflow}.md`
- [ ] **Phase 2（运维后）**：升级到 V2（落地表 + 后端晋升）→ 再演进到 broker（方案 C；Redis Stream 备选，OQ-OPS-2）

---

### 📋 顺带项（低成本高杠杆，2026-06-07 新增）

> 非独立 ADR 级别，但应在加固轨中择机插入。

- [ ] **全局异常处理（OQ-ARCH-2）**：加 `@RestControllerAdvice` + 统一错误信封，收敛当前 service 直抛 `IllegalArgumentException`/`EntityNotFoundException` 导致的 500 泄栈。**建议排在 [ADR-0009](../decisions/adr/ADR-0009-restore-auth-enforcement.md) 之前**——鉴权强制后需统一 401/403 响应格式；若达 API 契约变更门槛，另立轻量 ADR。
- [ ] **keyword 空操作（OQ-ARCH-4）**：15 个公开列表端点暴露 `keyword`，12 个静默忽略。团队先决"实现 vs 移除参数"，**决策应落在 codegen 模板层**（避免 3 已实现 vs 12 未实现的持续漂移）。建议作为 [ADR-0014](../decisions/adr/ADR-0014-test-baseline.md) 落地后**首个带测试的真实改动**。

---

## 关联但未决的开放问题（参考）

下列问题与上述实施有交集但**未被任一已 Accept 的 ADR 直接覆盖**，落地过程中会触达，建议在相应时机立单独 ADR 或在落地 ADR 内记录后续 Design 议题：

| 开放问题 | 关联实施点 |
| --- | --- |
| [OQ-SEC-8](open-questions.md) 运行时多租户隔离强制 | ADR-0009 落地后浮现 |
| [OQ-AI-1](open-questions.md) AI 检测闭环落库 | ✅ 已立 [ADR-0015](../decisions/adr/ADR-0015-ai-detection-closed-loop.md)（终态，待 Accept） |
| [OQ-AI-2](open-questions.md) 训练配置/数据集来源未文档化 | ADR-0015 **阻断性前置** |
| [OQ-AI-3](open-questions.md) 模型标签集 vs `event_type_enum` 映射 | ADR-0015 **阻断性前置** |
| [OQ-DATA-1](open-questions.md) PG → Neo4j 增量同步 | ADR-0012 落地时配套设计 |
| [OQ-OPS-3](open-questions.md) TLS/HTTPS 终结位置 | 独立运维 ADR |
| [OQ-OPS-4](open-questions.md) 回滚 / 发布策略 | ADR-0012 落地时配套 |
| [OQ-ARCH-2](open-questions.md) 统一错误处理与响应格式 | 已排入"顺带项"，建议 0009 之前落地 |
| [OQ-ARCH-4](open-questions.md) 列表 keyword 静默空操作 | 已排入"顺带项"，决策落 codegen 模板层 |
| [OQ-PROD-1](open-questions.md) PRD 缺失 | 独立产品议题 |

## 流程提醒

任何具体工作启动前：

1. 选定一个**单一目标**的会话（[`CLAUDE.md`](../../CLAUDE.md) 会话规则）。
2. 引用相关 ADR / 开放问题。
3. 若涉及重大决策 → 先在 Design 模式产出 ADR，评审通过后再 Implementation。
4. 改动小、可独立评审、仓库始终可工作；测试与文档同步。
5. Implementation 完成后回到本文勾选清单项并更新 ADR 状态为 `Implemented`。
