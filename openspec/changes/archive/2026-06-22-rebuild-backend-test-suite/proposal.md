## Why

迁移（Changes 0–3）拆除了后端整套测试（33 个测试源文件全删）与 Backend Java Tests CI 门，后端现处于**零回归保护**状态：任何破坏会话认证、租户隔离、默认拒绝边界的改动都能静默合入 develop。在向 main 发布前，必须先重建测试地基并把回归门加回，否则会把一个无保护网的安全敏感系统推上生产。

按 `testing-and-ci` spec 的既定哲学，后端测试以 superpowers TDD **按能力增量重建**、不维护独立守卫层。本 change 落地这套范式的第一步：建立可复用的测试地基，并完整重建**最高价值、回归风险最大的 `auth-authorization` 能力**的行为测试切片（含其名下的 INC-001 护栏与 spec 验收覆盖）。其余能力（data-platform、notifications 等）各自后续起 change 跟进。

## What Changes

- **新增测试地基**：Testcontainers PostgreSQL 基类（共享容器、每测试事务/清库）、`@SpringBootTest` 集成测试 base、MockMvc + Spring Security Test 配置、测试 profile 与 Redis/会话测试支撑、测试命名与分层约定。
- **Flyway 迁移冒烟测试**：对 V1–V6 迁移在真实 PostgreSQL 上 `migrate` 全过、schema 可加载（取代被删的 `FlywayMigrationTest`）。
- **Spring context 加载冒烟测试**：应用上下文在测试 profile 下可启动。
- **`auth-authorization` 行为测试切片**（按 spec 现有 requirement 重建，TDD）：
  - 会话登录契约（成功建会话 + 最小 `AuthSessionVO`、失败统一 401 不泄露账户存在性、CSRF 写请求门）。
  - 默认拒绝边界（`/api/v1/**` 匿名 401、公共 allowlist 可达）。
  - 每请求 `EffectiveAuthorizationContext` 重解析（角色撤销/账号停用次请求即 401）。
  - 租户隔离（跨租户 GET/写 → 404 隐藏存在性、列表仅本租户、客户端 `kindergartenId` 篡改拒绝）。
  - RBAC 集中策略（错误角色 403、PLATFORM_IT_ADMIN 不可读租户 S1）。
  - 认证失败响应契约（401/403/404 语义、响应体不含敏感数据）。
- **重建 INC-001 护栏**为 auth 能力测试：用户账号手机号唯一约束（DB 层 `uq_user_account_phone` 行为验证）。
- **重建 spec 验收覆盖映射**：auth-authorization 验收 scenario 到测试的覆盖映射只引用真实存在的测试。
- **加回 Backend Java Tests CI 门**：`.github/workflows/` 新增运行 `./gradlew test` 的 backend 测试 workflow（develop/PR 触发）。
- **更新 `testing-and-ci` spec**：翻转「后端测试门暂退」的 requirement —— 后端产品测试套件（首切片）已重建，CI SHALL 运行后端测试门。
- **更新 `rebuild-guardrails` spec**：从耐久 backlog 中移除已重建项（INC-001 手机号唯一、spec 验收覆盖映射，owner auth-authorization）。

Non-goals（明确不做）：
- **不**重建 data-platform 护栏（INC-003 loader PII、INC-005 MapStruct、schema-digest 漂移）—— 归属 data-platform，后续 change。
- **不**做 notifications 接线或其行为测试 —— 归属 notifications 接线 change。
- **不**改任何后端产品源码（除非 TDD 过程暴露与 spec 不符的真实缺陷，届时单独标注）。
- **不**恢复 main 分支保护的 required-status-checks 配置（GitHub 侧维护者操作，作为本 change 交付后的 follow-up 列出，不在代码内）。

## Capabilities

### New Capabilities
<!-- 无新增产品能力：测试归属既有能力，不是独立 capability。 -->
（无）

### Modified Capabilities
- `testing-and-ci`: 翻转「Backend 测试门在产品测试套件重建前暂退」requirement —— 首切片重建后，CI SHALL 运行后端 `./gradlew test` 门；保留「按能力增量 TDD、不维护独立守卫层」原则。
- `rebuild-guardrails`: 从「Guardrail backlog content」requirement 中移除已重建的两项（INC-001 手机号唯一、spec 验收覆盖映射，owner auth-authorization）；data-platform 名下三项继续保留在 backlog。

## Impact

- **新增代码**：`backend/src/test/java/...`（测试地基基类 + auth 行为测试切片 + Flyway/context 冒烟测试）、`backend/src/test/resources/`（测试 profile/配置）。
- **CI**：`.github/workflows/` 新增 backend 测试 workflow；`testing-and-ci` 与 `rebuild-guardrails` spec 更新。
- **依赖**：build.gradle 测试依赖已齐（spring-boot-starter-test、spring-security-test、testcontainers junit-jupiter + postgresql）；可能需补 testcontainers BOM/Redis 支撑（设计阶段确认）。
- **运行前提**：测试需要 Docker 守护进程（Testcontainers）。本机有 Docker 可做运行时验证；CI runner 需含 Docker（GitHub-hosted 默认有）。
- **产品源码**：默认零改动。
- **Follow-up（不在本 change 代码内）**：维护者在 GitHub 分支保护把新 backend 测试门加入 main 的 required-status-checks。
