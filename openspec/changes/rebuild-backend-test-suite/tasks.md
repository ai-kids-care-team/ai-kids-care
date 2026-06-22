## 1. 测试地基（先打通可运行的最小绿）

- [x] 1.1 build.gradle 测试依赖已齐（spring-boot-starter-test / spring-security-test / testcontainers junit-jupiter+postgresql，版本经 `ext testcontainers.version=1.21.4` + spring dep-mgmt 管控）；无需改动、未恢复 mapstruct=ERROR
- [x] 1.2 复原 `application-test.yml`（test profile：排除 Neo4j 自动配置、Redis 空密码、rrn/camera/ai 测试密钥；Flyway/ddl-auto 继承 main application.yml）
- [x] 1.3 `BaseIntegrationTest`（root 测试包）：单例共享 PostgreSQL 容器（`withCopyFileToContainer` 注入 `../db/initdb` 支持 DooD）+ `@DynamicPropertySource` 注入数据源；〔注：采用已验证的根包 base，未另建 `support/` 子包〕
- [x] 1.4 全栈集成 base = `BaseIntegrationTest`（`@SpringBootTest(RANDOM_PORT)`+`@ActiveProfiles("test")`）；MockMvc 由各测试类 `@AutoConfigureMockMvc` 接入（沿用原模式）
- [x] 1.5 Redis/会话方案：用 Testcontainers `redis:7-alpine`（已在 base，`@DynamicPropertySource` 注入 host/port）——选真实容器以验证 Spring Session indexed repository 真实语义
- [x] 1.6 fixture 来源：容器由 `db/initdb` 全量 seed；各测试类按需用 `JdbcTemplate` + `ON CONFLICT` 幂等 upsert 自带独立数据（沿用原模式，避免共享容器污染）
- [x] 1.7 context-load 冒烟测试 `smoke/ContextLoadSmokeTest` 容器内跑绿（tests=1 skipped=0 failures=0，BUILD SUCCESSFUL 2m18s）——整条工具链验证可用

## 2. 迁移冒烟测试

> **apply 期发现（ADR-0013 阻塞）**：「空库从 V1 跑 Flyway 建全 schema」这条路当年的 `FlywayMigrationTest` 是 `@Disabled` —— 因 `CommonCode` JPA 实体仍映射 `common_codes`，但该表只在 `db/initdb/03_CommonCode.sql`、**不在** Flyway 迁移里，故 fresh-Flyway 后 `ddl-auto=validate` 失败。ADR-0013 至今未决（实体在、迁移无该表）。本 change **不**解 ADR-0013（产品/schema 决策，超范围）。故 2.1 改为验证**真实可绿的部署路径**；fresh-Flyway 路径作阻塞项记录。

- [ ] 2.1 [GREEN] 迁移路径冒烟：在 initdb 初始化的 Testcontainers PG 上，Flyway baseline(V1) + V2–V6 成功记入 `flyway_schema_history`、且 JPA `ddl-auto=validate` 通过（context 能起即证；额外断言 V2–V6 success=true）
- [ ] 2.2 记录 fresh-Flyway(空库 V1) 路径为 ADR-0013 阻塞项：保留一个 `@Disabled("ADR-0013 pending")` 的占位测试，附原因说明，待 ADR-0013 解决后启用

## 3. auth-authorization 行为测试切片（逐 scenario TDD：red → green → refactor）

- [ ] 3.1 登录成功建会话 + 返回最小 `AuthSessionVO`（无 token/RRN/密码哈希）；设置 httpOnly `AI_KIDS_CARE_SESSION` cookie
- [ ] 3.2 登录失败统一 `401 {"error":"Authentication failed"}`（无效凭证/INACTIVE/0 或多个 ACTIVE 角色），不泄露账户存在性、不建会话
- [ ] 3.3 CSRF：写请求（POST/PUT/DELETE/PATCH）缺有效 `X-XSRF-TOKEN` → 403，操作不执行
- [ ] 3.4 默认拒绝：`/api/v1/**` 匿名业务端点 → 401；公共 allowlist 端点匿名可达
- [ ] 3.5 `EffectiveAuthorizationContext` 重解析：角色撤销后下一请求 → 会话失效 401
- [ ] 3.6 `EffectiveAuthorizationContext` 重解析：账号停用后下一请求 → 401
- [ ] 3.7 租户隔离：跨租户 `GET /{id}` → 404（隐藏存在性）
- [ ] 3.8 租户隔离：客户端 body `kindergartenId` 篡改为他租户 → 404，且不写任何数据
- [ ] 3.9 租户隔离：列表端点仅返回本租户资源
- [ ] 3.10 RBAC：错误角色访问（如 TEACHER 请求 cctv_cameras）→ 403
- [ ] 3.11 RBAC：PLATFORM_IT_ADMIN 请求租户 S1 PII 端点 → 403
- [ ] 3.12 失败响应契约：401 响应体不含 password/RRN/sessionId/stacktrace/内部字段名
- [ ] 3.13 登出：`POST /api/v1/auth/logout` 删会话 + 清 cookie + 204；旧 cookie 后续请求 → 401

## 4. 重建护栏（INC-001）+ 验收覆盖映射

- [ ] 4.1 [RED→GREEN] INC-001：用户账号手机号唯一约束（`uq_user_account_phone`）—— 第二条同号插入触发唯一约束冲突；fixture 自带独立数据避免共享容器污染
- [ ] 4.2 产出 auth-authorization 验收覆盖映射文档（scenario → 测试方法），校验只引用真实存在的测试

## 5. CI 门

- [ ] 5.1 新增 `.github/workflows/backend-tests.yml`：develop push + PR(→develop/main) 触发，setup-java temurin 21 + `./gradlew test`，上传测试报告 artifact
- [ ] 5.2 本地（容器内）模拟该 workflow 命令跑通，确认 runner Docker/Testcontainers 路径可用

## 6. Spec 更新（delta 已在本 change，apply 时随实现核对）

- [ ] 6.1 核对 `specs/testing-and-ci/spec.md` delta 与实际 CI 行为一致（后端测试门已运行）
- [ ] 6.2 核对 `specs/rebuild-guardrails/spec.md` delta 与实际一致（INC-001 + 验收覆盖已移出 backlog、data-platform 三项保留）

## 7. 验证与收尾（verification-before-completion）

- [ ] 7.1 容器内 `gradle:8-jdk21` DooD 实跑 `./gradlew test` 全套件全绿，留存输出作为证据
- [ ] 7.2 确认未改任何后端产品源码（`git diff` 仅含 src/test、build.gradle 测试依赖、.github/workflows、openspec）；如 TDD 暴露真实缺陷则单列回报
- [ ] 7.3 requesting-code-review；按反馈修正
- [ ] 7.4 提交 develop；记录 follow-up：维护者把 Backend Java Tests 门加入 main 分支保护 required-status-checks
```

## 维护者批准项（apply 前确认）

> 本 change **不含**删除/迁移/schema/部署类高风险操作。新增 `.github/workflows/backend-tests.yml` 属 CI 配置新增（非删除/非分支保护改动）。分支保护 required-status-checks 的改动列为交付后维护者 follow-up（GitHub 侧操作，不在代码内），无需在 apply 阶段执行。
