# 测试（Testing）

> **ADR-0014 状态：Implemented（2026-06-08）**
> 本文档随 ADR-0014 测试基线落地一并更新。

---

## 测试分层

| 层 | 工具 | 目标 | 位置 |
|---|---|---|---|
| **集成测试（Integration）** | Spring Boot Test + Testcontainers PostgreSQL | HTTP 端点 + JPA + 真实 schema 验证 | `backend/src/test/java/` |
| **契约测试（Contract）** | JUnit 5 + MockMvc + reflection | 公共 JSON/OpenAPI schema、关闭端点、敏感字段与 generic write absence | `backend/src/test/java/com/ai_kids_care/v1/contract/` |
| 单元测试（Unit） | JUnit 5 + Mockito | 注册分支、落库边界及纯逻辑 | `backend/src/test/java/com/ai_kids_care/v1/service/` |
| E2E | 尚未建立 | 浏览器到后端的跨端流程 | — |

---

## 运行测试

```bash
# 前置条件：本地可用 Docker（Testcontainers 需要 Docker socket）
cd backend
./gradlew test

# 测试报告
open build/reports/tests/test/index.html
```

CI 自动运行同一套 `./gradlew test`：

- GitHub Actions：`.github/workflows/backend-java-tests.yml` 在推送或 Pull Request 到 `develop` / `main` 时运行，也支持手动触发。使用 GitHub hosted Ubuntu runner 的 Docker 执行 Testcontainers，并保留 7 天测试报告 artifact。
- Jenkins：`Jenkinsfile` 在 `Docker Compose Up` stage **之前**运行测试；失败即阻断部署。

Testcontainers 通过 Spring Boot BOM 的 `testcontainers.version` property 固定为 `1.21.4`；该 1.x patch line 包含近期 Docker Engine 兼容修复。

### 已知临时跳过：`FlywayMigrationTest`

`FlywayMigrationTest` 当前以 `@Disabled` 保留。原因不是缺少 `common_codes` 迁移：已接受的 [ADR-0013](../decisions/adr/ADR-0013-dictionary-tables-governance.md) 明确要求删除该表，而 Flyway V1 已按目标架构不创建它；当前失败来自尚未落地的遗留 `CommonCode` JPA 映射。

ADR-0013 Implementation 删除 `CommonCode` 实体与通用 CRUD 栈后，必须移除 `@Disabled` 并恢复“空数据库执行 Flyway V1 + `ddl-auto=validate`”门禁。CI 报告会把这两个测试显示为 skipped，避免把过渡状态误报为已验证通过。

---

## 基础设施

### BaseIntegrationTest

`backend/src/test/java/com/ai_kids_care/BaseIntegrationTest.java`

- 用 Testcontainers 启动 `postgres:16-alpine` 容器
- 将 `db/initdb/` 目录挂载为 `/docker-entrypoint-initdb.d`（按文件名顺序执行，与生产 initdb 完全一致）
- 每次测试运行同时验证 `ddl-auto=validate` 对 initdb 脚本的一致性（schema drift 会立即失败）
- `@DynamicPropertySource` 覆盖 datasource 属性；Neo4j 在测试 profile 中被排除

**容器复用**（可选加速）：在 `~/.testcontainers.properties` 添加
```properties
testcontainers.reuse.enable=true
```
启用后，跨 Gradle 调用复用同一容器，大幅缩短本地重复运行时间。

### 测试 profile（`application-test.yml`）

- 排除 `Neo4jAutoConfiguration`（测试路径均不涉及图存储）
- 保留 `ddl-auto: validate`（不降级为 `create-drop`，守护约束）

---

## 现有测试覆盖

### 认证端点（`AuthEndpointTest`）

| 测试 | 端点 | 验证内容 |
|---|---|---|
| `login_validCredentials_returns200WithTokenFields` | `POST /auth/login` | 200 + accessToken/refreshToken/tokenType |
| `login_kindergartenScopedRole_returnsServerDerivedKindergartenId` | `POST /auth/login` | ACTIVE 园级角色返回 role assignment 派生的 `kindergartenId` |
| `login_wrongPassword_returns401` | `POST /auth/login` | Auth API 显式返回通用 401 JSON，不经受保护 `/error` |
| `login_activeUserWithoutActiveRole_returns401` | `POST /auth/login` | ACTIVE user 没有 ACTIVE role 时 401，不回退 GUARDIAN |
| `login_activeUserWithMultipleActiveRoles_returns401` | `POST /auth/login` | ACTIVE role 多于一条时 401，不取最近一条 |
| `refresh_validRefreshToken_returns200WithNewTokens` | `POST /auth/refresh` | 200 + 新 accessToken |
| `refresh_userWithoutActiveRole_returns401` | `POST /auth/refresh` | refresh token 有效但无 ACTIVE role 时 401 |
| `refresh_userWithMultipleActiveRoles_returns401` | `POST /auth/refresh` | refresh token 有效但 ACTIVE role 歧义时 401 |
| `refresh_pendingUser_returns401` | `POST /auth/refresh` | refresh token 有效但 user 已为 PENDING 时 401 |
| `refresh_invalidToken_returnsExplicit401` | `POST /auth/refresh` | 无法解析的 refresh token 由 Auth API 显式返回通用 401 JSON |
| `guardianChildVerification_returnsOnlyGenericMatchResultAndLegacyLookupIsClosed` | `POST /auth/guardian-child-verifications` | 正确/错误 RRN 均只返回 `verified`；旧 `/children/rrn` 明确返回 404 |
| `guardianChildVerificationAndRegistration_rejectMalformedRrnBeforePersistence` | Guardian verification/register | 非数字 RRN 返回 400，注册 users 零落库 |
| `register_superadminRole_createsPendingApplicationAndCannotLogin` | `POST /auth/register` | user/role/profile 全为 PENDING，客户端 ACTIVE 无效，随后登录 401 |
| `register_platformItAdminRole_isRejectedBeforePersistence` | `POST /auth/register` | 400 且 users 零落库 |
| `register_kindergartenRoles_createPendingProfileRoleAndMembership` | `POST /auth/register` | TEACHER、院长和副院长申请的 profile/role/membership 全为 PENDING |
| `register_kindergartenRoleLevelMismatch_isRejectedBeforePersistence` | `POST /auth/register` | 管理员 role/level 双向不一致时 400 且 users 零落库 |
| `register_guardianRole_createsPendingProfileRoleAndMembership` | `POST /auth/register` | Guardian profile/role/membership 全为 PENDING，scope 由匹配儿童派生 |
| `availability_existingLoginId_returnsUnavailable` | `GET /auth/register/availability` | `available=false`（seed 用户 admin） |
| `availability_newLoginId_returnsAvailable` | `GET /auth/register/availability` | `available=true`（随机未使用 ID） |

`PublishedOpenApiContractTest` 另固定 `/api/v1/children/rrn` path absence、专用 Guardian 验证 path presence、响应 schema 只能包含 `verified`，并精确列举 `AuthRegisterDTO` 的全部允许字段。

### 敏感数据与公共 API 契约

- `SensitiveResponseContractTest`：固定内部 VO 不含敏感存储字段，并验证已关闭资源不产生 MVC response；CameraStream 脱敏 response 继续做字段级断言。
- `SensitiveWriteContractTest`：固定敏感 generic create/update DTO、service 与 mapper 写链的 absence，并验证普通更新不覆盖内部敏感值。
- `SensitivePublicApiClosureContractTest`：固定 Graph、Audit Log、Notification、User、Child、Guardian、Teacher、DeviceToken、EventEvidenceFile、DetectionEvent 无公共 operation，Kindergarten 通用写入关闭且注册查找仅返回最小目录字段。
- `PublishedOpenApiContractTest`：扫描全部 v1 `@RestController` 发布的真实 `/v3/api-docs`，执行 S0 denylist、受限 S1 command allowlist、关键 path/method presence/absence 与 schema 精确字段检查。

### 检测事件关闭契约（`DetectionEventEndpointTest`）

| 测试 | 端点 | 验证内容 |
|---|---|---|
| `listDetectionEvents_isNotPublished` | `GET /detection_events?kindergartenId=...` | 完整应用明确返回 404；OpenAPI/standalone contract 证明 path 未发布 |
| `detectionEventDetail_isNotPublished` | `GET /detection_events/{id}?kindergartenId=...` | 完整应用明确返回 404；详情读取等待资源授权 |

2026-06-13 完整 backend suite：51 tests，49 passed，0 failed，0 errors，2 个 ADR-0013 预期 skip。
同轮 frontend production build 生成 20 个静态页面；Phase 1A 的 39 个已修改前端源码文件 scoped ESLint 为 0 error / 0 warning。全仓 lint 仍有 4 个既有 error 和 8 个 warning，均位于本阶段未修改文件。

---

## Characterization 测试约定

> "Characterization 测试固定**现有行为**，而非理想行为。"

1. 测试失败 = 行为发生变化。在变更是**预期的**时，更新测试并注明原因（如关闭路径从受保护 `/error` 产生的偶然 401 改为明确 404）。
2. 每次向高 blast-radius ADR（0012/0013/0010/0009）添加改动前，须先补 characterization 测试覆盖受影响路径。
3. 测试数据：优先使用 initdb seed 中的已知记录；写操作使用 UUID 后缀保证幂等。

---

## 命名约定

```
<subject>_<condition>_<expected>
// 示例
login_validCredentials_returns200WithTokenFields
availability_existingLoginId_returnsUnavailable
```

---

## 待确认

> ❓ 是否存在仓库之外的测试（如手工测试用例、Postman 集合）？
> ❓ Jenkins agent 的 Docker socket 已验证可用（Testcontainers 依赖）？详见 ADR-0014 "负面代价"。
> ❓ GitHub 仓库 branch protection 是否已把 `Backend Java Tests / Gradle test (Java 21)` 设为 required status check？workflow 已提供门禁信号，但 required 规则需在 GitHub 仓库设置中启用。
