# 测试（Testing）

> **ADR-0014 状态：Implemented（2026-06-08）**
> 本文档随 ADR-0014 测试基线落地一并更新。

---

## 测试分层

| 层 | 工具 | 目标 | 位置 |
|---|---|---|---|
| **集成测试（Integration）** | Spring Boot Test + Testcontainers PostgreSQL | HTTP 端点 + JPA + 真实 schema 验证 | `backend/src/test/java/` |
| 单元测试（Unit）*（待补）* | JUnit 5 + Mockito | 纯逻辑、工具类 | 同上（后续按需补充） |
| E2E / 契约测试 | 不在当前范围 | 见 ADR-0014 "显式不做" | — |

---

## 运行测试

```bash
# 前置条件：本地可用 Docker（Testcontainers 需要 Docker socket）
cd backend
./gradlew test

# 测试报告
open build/reports/tests/test/index.html
```

CI（Jenkinsfile）在 `Docker Compose Up` stage **之前**自动运行 `./gradlew test`；失败即阻断部署。

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
| `login_wrongPassword_returns500` | `POST /auth/login` | 当前行为：500（OQ-ARCH-2 全局异常处理落地后改为 401） |
| `refresh_validRefreshToken_returns200WithNewTokens` | `POST /auth/refresh` | 200 + 新 accessToken |
| `register_superadminRole_returns201WithUserId` | `POST /auth/register` | 201 + userId |
| `availability_existingLoginId_returnsUnavailable` | `GET /auth/register/availability` | `available=false`（seed 用户 admin） |
| `availability_newLoginId_returnsAvailable` | `GET /auth/register/availability` | `available=true`（随机未使用 ID） |

### 检测事件读路径（`DetectionEventEndpointTest`）

| 测试 | 端点 | 验证内容 |
|---|---|---|
| `listDetectionEvents_returns200WithPageStructure` | `GET /detection_events` | 200 + Spring Page 结构 |
| `listDetectionEvents_seedDataPresent_contentIsNonEmpty` | `GET /detection_events` | content 非空（seed 数据存在） |
| `listDetectionEvents_withPageSize_respectsRequestedSize` | `GET /detection_events?size=3` | content.size ≤ 3 |

---

## Characterization 测试约定

> "Characterization 测试固定**现有行为**，而非理想行为。"

1. 测试失败 = 行为发生变化。在变更是**预期的**时，更新测试并注明原因（如 `login_wrongPassword` 将来改为 401）。
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
