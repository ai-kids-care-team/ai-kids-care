# 演进路线（Roadmap）

> 本文档跟踪**已 Accept 的 ADR 的实施进度**，并把每篇 ADR 的「落地范围」展开为可勾选清单。每篇 ADR 落地遵循 [`CLAUDE.md`](../../CLAUDE.md) 的会话规则——**单一目标、改动小、可独立评审、仓库始终可工作**。
>
> 基线：2026-05-29（5 篇前瞻 ADR Accept 当日）。本文从 placeholder 升级为实际路线图。

## 状态总览

| 顺序 | ADR | 决策摘要 | 状态 | 复杂度 |
| --- | --- | --- | --- | --- |
| 1 | [ADR-0011](../decisions/adr/ADR-0011-extract-codegen-subproject.md) | codegen → `pg-spring-crud-codegen/` 仓内迁址 | ✅ **Implemented (2026-05-29)** | 小 |
| 2 | [ADR-0012](../decisions/adr/ADR-0012-production-data-lifecycle.md) | 演示重置 vs 生产数据生命周期 + Flyway/Liquibase | 📋 **Backlog（next）** | 中 |
| 3 | [ADR-0013](../decisions/adr/ADR-0013-dictionary-tables-governance.md) | `menu` → C 静态；`common_codes` → β 后端 enum 端点 + 前端 i18n | 📋 Backlog | 中 |
| 4 | [ADR-0010](../decisions/adr/ADR-0010-rrn-one-way-hash.md) | RRN HMAC-SHA-256 + pepper（替代 BCrypt+候选集） | 📋 Backlog | 中-高 |
| 5 | [ADR-0009](../decisions/adr/ADR-0009-restore-auth-enforcement.md) | 恢复鉴权强制（含 access/refresh 区分、role claim、secret 外部化） | 📋 Backlog | 高 |

## 实施次序（已确认 2026-05-29）

**0011 → 0012 → 0013 → 0010 → 0009**（按风险递增 + 依赖关系排序）

| 依赖说明 | 影响顺序 |
| --- | --- |
| 0013 / 0010 涉及 schema 变更 | 必须排在 0012（迁移基础设施）之后 |
| 0010 引入 pepper 密钥管理范式 | 0009 复用同一路径，故 0010 在前 |
| 0009 翻转鉴权（最高风险，需测试基础设施配套） | 排最后，前面 ADR 落地时已具备测试基线 |
| 0011 是纯结构调整（零行为风险） | 排第一作为 Implementation 工作流的安全起点 |

---

## 待办清单（按 ADR 展开）

### ✅ ADR-0011 codegen 迁址 — 已完成（2026-05-29）

- [x] 14 个文件 `scripts/codegen/*` → `pg-spring-crud-codegen/*`
- [x] `pg-spring-crud-codegen/docker-compose.yml` 相对路径 `../../db` → `../db`
- [x] 新增 `pg-spring-crud-codegen/README.md`（自洽子工程说明）
- [x] 新增 `scripts/codegen/README.md`（软指针）
- [x] `CODEOWNERS` 增补 `/pg-spring-crud-codegen/` 条目
- [x] 13 处内部引用切换（root READMEs、docs/architecture/*、ADR-0004 等）

**未做**（按 ADR 范围）：未拆出独立 git 仓库——保持仓内迁址形态；后续 `git filter-repo` 带史拆出留待单独任务。

---

### 📋 ADR-0012 演示重置 vs 生产数据生命周期 — Next

- [ ] backend `build.gradle` 加 Flyway（或 Liquibase）插件 + 依赖
- [ ] 创建 `backend/src/main/resources/db/migration/V1__initial_baseline.sql`（内容 = 当前 `01_create_schema.sql`，作为基线）
- [ ] `application.yml` 配置 `spring.flyway.baseline-on-migrate=true`、`baseline-version=1`
- [ ] `docker-compose.yml` 拆分 profile：dev 保留 initdb seed，prod profile 不挂载 seed
- [ ] `Jenkinsfile` 区分 demo CI（`down --volumes` + 重建）vs 生产部署（无删卷、依赖迁移）
- [ ] 文档同步：`operations/{deployment,configuration,runbook}.md`、`data-architecture.md §3`、`ADR-0004` 链入迁移流程
- [ ] **关联待补**：备份/恢复 + 回滚策略（OQ-OPS-4，未决）；Neo4j 生产再同步策略（OQ-DATA-1，未决）——可在本 ADR 落地时一并记录后续 Design 议题

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
- [ ] **数据迁移**（生产）：既有 BCrypt 数据无法直接转 HMAC——过渡期**双读单写**：先按 HMAC 命中，未命中回退候选集 + BCrypt 匹配；用户/儿童下次提供完整 RRN 时把 HMAC 列回填。具体步骤建议立 **ADR-0014 子 ADR** 固化
- [ ] 文档同步：`security-architecture.md §4`、`data-architecture.md §6`、`product/glossary.md`

---

### 📋 ADR-0009 鉴权恢复 — Last

> **必要前置**：本 ADR 必须配套**测试基础设施**（[OQ-TEST-1](open-questions.md) 当前 `backend/src/test/` 为空）；翻转鉴权前应有 characterization 测试覆盖既有行为。

- [ ] **配套**：搭建 Spring Boot Test 基础设施（JUnit 5 + Testcontainers/H2 + `spring-security-test`）
- [ ] **配套**：为 4 个真正实现的端点（`POST /auth/login` `/refresh` `/register`、`GET /auth/register/availability`）补 characterization 测试
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

## 关联但未决的开放问题（参考）

下列问题与上述实施有交集但**未被任一已 Accept 的 ADR 直接覆盖**，落地过程中会触达，建议在相应时机立单独 ADR 或在落地 ADR 内记录后续 Design 议题：

| 开放问题 | 关联实施点 |
| --- | --- |
| [OQ-SEC-8](open-questions.md) 运行时多租户隔离强制 | ADR-0009 落地后浮现 |
| [OQ-AI-1](open-questions.md) AI 检测闭环落库 | 产品完整性核心，独立 Design 议题 |
| [OQ-AI-2](open-questions.md) 训练配置/数据集来源未文档化 | 与 AI 闭环关联 |
| [OQ-AI-3](open-questions.md) 模型标签集 vs `event_type_enum` 映射 | 与 AI 闭环关联 |
| [OQ-DATA-1](open-questions.md) PG → Neo4j 增量同步 | ADR-0012 落地时配套设计 |
| [OQ-OPS-3](open-questions.md) TLS/HTTPS 终结位置 | 独立运维 ADR |
| [OQ-OPS-4](open-questions.md) 回滚 / 发布策略 | ADR-0012 落地时配套 |
| [OQ-ARCH-2](open-questions.md) 统一错误处理与响应格式 | 独立后端 ADR |
| [OQ-PROD-1](open-questions.md) PRD 缺失 | 独立产品议题 |

## 流程提醒

任何具体工作启动前：

1. 选定一个**单一目标**的会话（[`CLAUDE.md`](../../CLAUDE.md) 会话规则）。
2. 引用相关 ADR / 开放问题。
3. 若涉及重大决策 → 先在 Design 模式产出 ADR，评审通过后再 Implementation。
4. 改动小、可独立评审、仓库始终可工作；测试与文档同步。
5. Implementation 完成后回到本文勾选清单项并更新 ADR 状态为 `Implemented`。
