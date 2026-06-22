## Context

迁移 Change 2 删除了 `backend/src/test/` 全部 33 个测试源文件、`backend-java-tests.yml` 与 `schema-digest-drift.yml` CI、以及 build.gradle 的 `mapstruct.unmappedTargetPolicy=ERROR`。后端现 0 测试、0 后端 CI 门。产品源码（会话认证、`EffectiveAuthorizationContextFilter`、`AuthorizationPolicy`、租户隔离、Flyway V1–V6）完整保留。

约束：
- `build.gradle` 已含测试依赖：`spring-boot-starter-test`、`spring-security-test`、`testcontainers:junit-jupiter`、`testcontainers:postgresql`，且声明 `testcontainers.version=1.21.4`、`useJUnitPlatform()`。
- 鉴权是**服务端会话**（Spring Session + Redis + httpOnly cookie `AI_KIDS_CARE_SESSION` + CSRF），不是 stateless JWT —— 测试不能靠注入 bearer token，必须走会话/`@WithMockUser`/真实登录建立会话。
- 数据层 PostgreSQL + Flyway；本机有 Docker（可跑 Testcontainers），CI 用 GitHub-hosted runner（默认带 Docker）。
- 本机无本地 Java/Gradle 链（CI-only 思维），但**有 Docker 部署环境**：运行时验证用 `gradle:8-jdk21` 容器 DooD 跑 `./gradlew test`。
- 按 `testing-and-ci` spec：测试是产品行为测试、按能力 TDD，不建独立 harness 守卫层。

## Goals / Non-Goals

**Goals:**
- 建立可复用、可被后续能力切片继承的后端测试地基（Testcontainers PG 基类 + 集成测试 base + MockMvc/Security 配置 + 测试 profile）。
- 以 TDD 完整重建 `auth-authorization` 能力的行为测试切片，覆盖其 spec 的核心 scenario（登录契约、默认拒绝、上下文重解析、租户隔离、RBAC、失败响应契约、INC-001 手机号唯一）。
- 产出 auth-authorization 验收覆盖映射（scenario → 测试），只引用真实存在的测试。
- 加回运行 `./gradlew test` 的 Backend Java Tests CI 门。
- 在 Docker 容器内实跑测试套件全绿，作为完成证据（verification-before-completion）。

**Non-Goals:**
- 不重建 data-platform 三护栏（INC-003/INC-005/schema-digest）与 notifications 测试/接线。
- 不追求覆盖率数字目标；以「spec 关键 scenario 有对应测试」为完成判据。
- 不恢复 `mapstruct.unmappedTargetPolicy=ERROR`（testing-and-ci spec 明确回退框架默认；MapStruct 守卫归 data-platform）。
- 不在本 change 内改 GitHub 分支保护 required-status-checks（维护者 follow-up）。

## Decisions

### D1：Testcontainers 单容器 + 每测试清库，而非每测试新容器
用一个会话级共享的 PostgreSQL 容器（`@Container static` + Singleton 模式或 JUnit 扩展），各测试间用事务回滚或 `@Sql`/truncate 清理。
- 理由：每测试起容器会让套件慢到不可用；共享容器是 Testcontainers 标准实践。
- 取舍：共享状态需严格清理纪律 —— 被删的 INC-001 正是「共享容器 fixture 手机号唯一」类问题，故清理策略与唯一约束测试一并设计，避免重蹈覆辙。
- 备选：`@DataJpaTest` + 嵌入式 H2 —— 否决，H2 与 PostgreSQL 方言/约束/Flyway 行为不一致，掩盖真实缺陷。

### D2：Flyway 在 Testcontainers 上真实迁移，而非 `ddl-auto`
测试 profile 用 Flyway 对真实 PG 跑 V1–V6，JPA `ddl-auto=validate`。
- 理由：迁移冒烟测试（取代 `FlywayMigrationTest`）要验证的就是迁移本身；且保证测试 schema = 生产 schema。

### D3：分层测试策略 —— 切片 + 全栈各司其职
- **会话/安全过滤链行为**用 `@SpringBootTest(webEnvironment=MOCK)` + `MockMvc` + `spring-security-test`，覆盖默认拒绝、401/403/404 契约、CSRF、allowlist。
- **`EffectiveAuthorizationContext` 重解析 / 角色撤销即 401 / 租户隔离**需真实 DB + 过滤器链，用全栈集成测试（Testcontainers PG）。
- **INC-001 手机号唯一**是 DB 约束行为，用 JPA/repository 集成测试触发唯一约束冲突断言。
- 理由：会话语义和租户隔离是过滤器 + JPQL 谓词的产物，纯单元 mock 无法验证；必须真实链路。

### D4：会话认证测试的认证方式
优先用真实 `POST /api/v1/auth/login` 建会话（端到端验证登录契约），其余受保护端点测试复用已认证会话或 `@WithMockUser` + 注入 `EffectiveAuthorizationContext` 双轨：契约测试走真登录，边界矩阵测试用 mock 主体降低 fixture 成本。
- 理由：登录契约必须真跑；但对每个租户/角色组合都真登录会让 fixture 爆炸。

### D5：测试包结构与命名
`backend/src/test/java/com/ai_kids_care/v1/` 下：`support/`（基类、Testcontainers 配置、fixture 构造器）、`auth/`（auth 行为测试）、`smoke/`（Flyway、context 加载）。命名 `*Test`（单元/切片）、`*IntegrationTest`（全栈）。
- 理由：后续能力切片在 `support/` 复用基类即可。

### D6：CI 门形态
新增 `.github/workflows/backend-tests.yml`，在 develop push 与 PR(→develop/main) 触发，`actions/setup-java@v4`(temurin 21) + `./gradlew test`，依赖 runner 内置 Docker 跑 Testcontainers；上传测试报告 artifact。
- 备选：复用 release.yml —— 否决，回归门应早于 release、独立可读。

## Risks / Trade-offs

- [TDD red 阶段对会话/过滤器链可能因 fixture 复杂而难写] → 先写最小的 context-load 与单条 allowlist 测试打通地基，再逐 scenario 扩展；地基 base 类吸收样板。
- [共享 Testcontainers 容器状态泄漏导致测试间互相污染（INC-001 同类陷阱）] → 在 base 类强制每测试清理，并把唯一约束测试设计为自带独立 fixture，纳入 review 检查项。
- [本机无 Java 链，无法直接 `./gradlew test`] → 用 `gradle:8-jdk21` 容器 DooD 实跑；首次会拉镜像与依赖，耗时较长但可行（参照前端用 node:20 容器的既有做法）。
- [TDD 可能暴露产品代码与 spec 不符的真实缺陷] → 不静默改产品码；遇到则停下、单列、按 systematic-debugging 处理或回报，保持「迁移零产品改动」基线可追溯。
- [CI runner Docker/Testcontainers 启动开销拉长 CI 时长] → 接受；安全系统的回归门价值高于数分钟 CI 成本，可后续用容器复用/并行优化。
- [新 CI 门尚未进 main 分支保护 required-status-checks，门可被绕过] → 作为维护者 follow-up 明确列出；本 change 先让门存在并对 PR 可见。

## Migration Plan

1. 地基先行：Testcontainers PG base + 测试 profile + context-load 冒烟测试跑通（容器内 `./gradlew test` 见绿）。
2. Flyway 迁移冒烟测试。
3. auth-authorization 切片按 spec scenario 逐条 TDD（red → green → refactor）。
4. INC-001 手机号唯一测试 + auth 验收覆盖映射文档。
5. 加 `backend-tests.yml` CI 门；更新 testing-and-ci / rebuild-guardrails spec（delta 已在本 change）。
6. 容器内全套件实跑全绿；requesting-code-review；develop 合入。
- 回滚：CI 门与测试均为新增，删 workflow + 测试目录即可回到当前状态；无产品码改动需回滚。

## Open Questions

- 测试用 Redis：用 Testcontainers Redis 容器，还是 Spring Session 测试用 map-based/嵌入式替身？倾向后者（会话语义可用 `MapSessionRepository` 或 mock 验证，避免再起一个容器），apply 阶段第 1 步确认。
- 登录契约测试所需的种子用户/角色/membership fixture 构造，是否复用 `db/` 既有 fixtures 还是测试内构造？apply 阶段地基步骤确定 fixture 来源。
