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

- [x] 2.1 迁移路径冒烟 `smoke/FlywayMigrationSmokeTest`：initdb-seeded PG 上 Flyway baseline(V1) + V2–V6 记入 `flyway_schema_history`(success=true)、无 failed 行；`ddl-auto=validate` 由 context 启动证明（绿）
- [x] 2.2 复原 `FlywayMigrationTest`（`@Disabled("ADR-0013 pending")` fresh-Flyway 占位，2 个方法 skipped），待 ADR-0013 解决后启用

## 3. auth-authorization 行为测试切片（characterization：测试既有产品码，绿=符合 spec）

> 复原同一产品码的被删 auth 测试 + INC-001 按 spec 重建为真实 DB 约束测试。逐类容器内实跑全绿。

- [x] 3.1 登录成功建会话 + 最小 `AuthSessionVO` → `AuthEndpointTest.login_validCredentials_returnsSessionProfileWithoutBearerTokens`
- [x] 3.2 登录失败统一 401 不泄露存在性 → `AuthEndpointTest.login_wrongPassword_returns401` 等（active-no-role/无 membership/平台带 membership 多分支）
- [x] 3.3 CSRF 写请求缺 token → 403 → `ErrorResponseSensitiveDataIntegrationTest.csrfRejectedLoginError_doesNotEchoSubmittedPassword`
- [x] 3.4 默认拒绝 + allowlist → `SecurityBoundaryIntegrationTest`（3 法：业务端点 401 / allowlist 可达 / 关闭控制器非存在性预言）
- [x] 3.5 角色撤销次请求 401 → `AuthEndpointTest.authenticatedRequest_afterRoleRevocationReturns401AndInvalidatesSession`
- [x] 3.6 账号停用次请求 401 → `AuthEndpointTest.authenticatedRequest_afterUserDisabledReturns401`
- [x] 3.7 跨租户 GET → 404 → `TenantIsolationIntegrationTest.kindergartenAdminCannotReadForeignTenantResourcesByValidId`
- [x] 3.8 客户端 kindergartenId 篡改 → 404 不写 → `TenantIsolationIntegrationTest.adminWriteRejectsForeignTenantKindergartenOverride`、`AuthEndpointTest.tenantWrite/cameraList_rejectsClientKindergartenOverride`
- [x] 3.9 列表仅本租户 → `TenantIsolationIntegrationTest.kindergartenAdminListSeesOnlyOwnTenantRoomsAndOwnRoomIsReachable`
- [x] 3.10 错误角色 → 403 → `GuardianChildAuthorizationIntegrationTest.children_kindergartenAdminRole_returns403`、`AuthEndpointTest.teacher_cannotReadSurveillanceResources`
- [x] 3.11 PLATFORM/KINDERGARTEN 角色越权读 → 403 → `AuthEndpointTest.kindergartenRole_cannotReadPlatformAiMetadata`、`platformTenantSelection_doesNotGrantCameraRead`
- [x] 3.12 401/错误响应体不含敏感数据 → `ErrorResponseSensitiveDataIntegrationTest`（4 法）
- [x] 3.13 登出 204 后旧 cookie 401 → `AuthEndpointTest.logout_invalidatesCurrentSession`、`logoutAll_revokesEverySessionForTheUser`
- [x] 3.+ 切片补全（同属 auth-authorization spec）：注册 PENDING（`AuthServiceRegistrationTest` + `AuthEndpointTest.register_*`）、审批授权（`Admin/PlatformAdminApprovalAuthorizationIntegrationTest`）、教师分配边界（`Teacher{Assignment,Child,Room}AuthorizationIntegrationTest`）、Guardian-child 边界（`GuardianChildAuthorizationIntegrationTest`）、安全审计（`SecurityAuditIntegrationTest`）、平台租户上下文选择（`AuthEndpointTest`）

## 4. 重建护栏（INC-001）+ 验收覆盖映射

- [x] 4.1 INC-001 真实 DB 约束测试 `v1/auth/PhoneUniquenessConstraintTest`：同号第二账号触发 `DataIntegrityViolationException`(`uq_user_account_phone`)；自带独立 fixture + `@BeforeEach` 清理避免共享容器污染（绿）
- [x] 4.2 `acceptance-coverage-auth-authorization.md`：spec 16 条 requirement → 测试类/方法映射，全部引用已实跑通过的真实测试

## 5. CI 门

- [x] 5.1 复原 `.github/workflows/backend-java-tests.yml`：develop/main push + PR 触发，setup-java temurin 21 + `./gradlew test --no-daemon --stacktrace`，always 上传测试报告 artifact〔注：沿用原文件名 `backend-java-tests.yml`，job 名「Gradle test (Java 21)」与 required-check 名一致〕
- [x] 5.2 容器内（gradle:8.7-jdk21 + DinD，等价 runner 路径）实跑 `gradle test` 全绿，证明 Testcontainers 在容器化 Docker 环境可用

## 6. Spec 更新（delta 已在本 change，apply 时随实现核对）

- [x] 6.1 `specs/testing-and-ci/spec.md` delta 与实际一致：后端测试门已存在并运行（`backend-java-tests.yml`）
- [x] 6.2 `specs/rebuild-guardrails/spec.md` delta 与实际一致：INC-001 + 验收覆盖已重建并移出 backlog；data-platform 三项保留

## 7. 验证与收尾（verification-before-completion）

- [x] 7.1 容器内 `gradle:8.7-jdk21` DooD 实跑 `./gradlew test` 全绿：**132 tests / 2 skipped(@Disabled) / 0 failures / 0 errors**，BUILD SUCCESSFUL
- [x] 7.2 确认零产品源码改动：`git status` 仅含 `src/test/**` + `.github/workflows/backend-java-tests.yml` + `openspec/**`，无 `src/main` 改动；build.gradle 未改（依赖本已齐）。TDD 未暴露需修的产品缺陷
- [ ] 7.3 requesting-code-review；按反馈修正
- [ ] 7.4 提交 develop；记录 follow-up：维护者把 Backend Java Tests 门加入 main 分支保护 required-status-checks
```

## 维护者批准项（apply 前确认）

> 本 change **不含**删除/迁移/schema/部署类高风险操作。新增 `.github/workflows/backend-tests.yml` 属 CI 配置新增（非删除/非分支保护改动）。分支保护 required-status-checks 的改动列为交付后维护者 follow-up（GitHub 侧操作，不在代码内），无需在 apply 阶段执行。
