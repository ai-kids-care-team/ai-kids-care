---
ADR: ADR-0019
title: "ADR-0019: 服务端有效授权上下文与租户强制边界"
status: Accepted
implementation: In Progress
date: 2026-06-14
deciders: 接手人起草，维护者 Accept（2026-06-14）
supersedes: []
superseded_by: null
related_specs:
  - SPEC-0001
---

# ADR-0019: 服务端有效授权上下文与租户强制边界

## 状态（Status）

Decision: `Accepted`

Implementation: `In Progress`（分阶段；阶段 2、3 已实现，阶段 4、5 部分完成，阶段 6 Release Gate 仅完成生产前置草案（TLS/compose CI）——均已随 [PR #89](https://github.com/ai-kids-care-team/ai-kids-care/pull/89) 合入 `develop`，详见下方状态块与 [SPEC-0001 实施记录](../../specs/SPEC-0001-auth-authorization-tenant-sensitive-data-boundaries.md)）

> 实施状态（2026-06-15；已随 [PR #89](https://github.com/ai-kids-care-team/ai-kids-care/pull/89) 合入 `develop`，merge commit `36cfdd4`，后续 `651ec20` 文档清扫）：阶段 2 Session Identity Foundation（Redis Spring Session、最小 `SessionPrincipal`、login/session/logout/csrf、cookie、去 JWT）与阶段 3 Context And Default Enforcement（`EffectiveAuthorizationContextFilter`/`Resolver`、每请求权威解析、`@EnableMethodSecurity` + 集中 `AuthorizationPolicy`、平台 tenant-context command、默认 `authenticated`、全发布 operation 分类）已实现。阶段 4 Tenant Repository Migration 部分完成：Class/Room/CCTV/CameraStream/DetectionSession/Announcement 的 tenant-aware 查询与隐藏 404、跨租户隔离测试锁定（`TenantIsolationIntegrationTest`）；未完成 DetectionEvent（控制器仍关闭、不可经 HTTP 触达）与 repository port 边界收敛。阶段 5 Relationship And Revocation 部分完成：`TeacherAssignmentPolicy`（classes/rooms 收紧为有效 assignment 级，surveillance cameras/streams/sessions 收为 `KINDERGARTEN_ADMIN`-only）、`SessionRevocationService` + `POST /auth/logout-all`；未完成 `GuardianChildPolicy`、状态变更事务的主动吊销触发、安全审计写入（受 `audit_logs.kindergarten_id NOT NULL` 限制，需 ADR-0012 迁移）。阶段 6 Release Gate 仅完成生产前置草案：Caddy 边缘 TLS + 生产 `SESSION_COOKIE_SECURE=true` + compose-config CI（见 [ADR-0017](ADR-0017-tls-https-termination.md)）；未完成 TLS 端到端验证、前端 lint/build CI gate 与 fresh independent review。合入前本地验证：后端 `gradlew test` 78 通过/0 失败/2 预期 skip；前端 scoped lint 0、生产构建 20 页。详见 [SPEC-0001 实施记录](../../specs/SPEC-0001-auth-authorization-tenant-sensitive-data-boundaries.md#2026-06-15-phase-23会话认证--有效授权上下文与默认拒绝)。

维护者于 2026-06-14 接受“每请求权威解析 + 集中式 policy + 显式 tenant-aware repository”方案。本 ADR 补足 [SPEC-0001](../../specs/SPEC-0001-auth-authorization-tenant-sensitive-data-boundaries.md) 的实施前决策门，并细化 [ADR-0003](ADR-0003-multitenancy-kindergarten-id.md)、[ADR-0009](ADR-0009-restore-auth-enforcement.md)、[ADR-0016](ADR-0016-server-side-session-auth.md) 与 [ADR-0017](ADR-0017-tls-https-termination.md) 之间的运行时边界。

## 背景（Context）

### As-built 事实

- `SecurityConfig` 当前关闭 CSRF、使用 `SessionCreationPolicy.STATELESS`、允许全部 `/api/v1/**`，且没有安装 JWT filter（`backend/src/main/java/com/ai_kids_care/v1/config/SecurityConfig.java:36-53`）。
- 登录和 refresh 只要求 user 为 ACTIVE 且恰有一条 ACTIVE role assignment；它们把 role 与 kindergarten scope 写入 token response，但没有校验匹配的 ACTIVE membership（`AuthService.java:110-121,145-156,275-287`）。
- `UserRoleAssignmentRepository` 仅提供“按 user + status 查询全部 role assignment”；`UserKindergartenMembershipRepository` 只有继承的通用 `JpaRepository` 方法，没有“唯一 ACTIVE membership”查询。
- 后端没有 `@PreAuthorize`、`@EnableMethodSecurity`、统一 `Principal`、Effective Authorization Context 或集中 tenant enforcement。
- 当前发布 11 个 Controller、40 个 operation：

| Controller 形态 | Operation 数 | As-built 约束 |
| --- | ---: | --- |
| Auth | 5 | login/refresh/register/availability/guardian verification；仍是 JWT contract |
| Kindergarten directory | 3 | 最小目录读取与注册查找 |
| AiModel/Menu/CommonCode | 11 | 平台或参考元数据，但仍有通用写接口 |
| Class/Room/Announcement | 15 | 通用 CRUD；tenant-scoped service 多数使用裸 `findAll` / `findById` |
| CctvCamera/CameraStream/DetectionSession | 6 | 部分 list 接受客户端 `kindergartenId`；detail 仍常用裸 `findById` |

- `ClassCreateDTO` / `ClassUpdateDTO` 接受客户端 `kindergartenId`，MapStruct 直接写入 `entity.kindergarten.id`（`ClassCreateDTO.java:19`、`ClassUpdateDTO.java:19`、`ClassMapper.java:17-24`）。Room 具有同形态。
- `CctvCameraController` 的 list 接受客户端 `kindergartenId`，但 detail 使用不带 tenant 的 `findById`；`DetectionSessionService` 的 list/detail 都不带 tenant（`CctvCameraController.java:24`、`CctvCameraService.java:21-25`、`DetectionSessionService.java:21-25`）。
- 多数 tenant-bearing repository 暴露通用 `findAll` / `findById` / `save` / `delete`，因此即使 Controller 增加角色注解，service 仍可通过合法 ID 访问另一租户资源。
- schema 使用共享数据库、共享 schema 和 `kindergarten_id` 复合外键。它能阻止跨园关联写错，但不能授权读取或修改另一园的合法记录。
- `user_kindergarten_memberships` 仅唯一约束 `(kindergarten_id,user_id)`；`user_role_assignments` 唯一约束 `(user_id,role,scope_type,scope_id)`，PostgreSQL 的 NULL 语义仍允许重复 PLATFORM scope 记录（`db/initdb/01_create_schema.sql:323-344,571-573`）。
- Guardian 关系表以 `start_date/end_date` 表达有效期，没有 status；Teacher/Class/Room assignment 具有 status 与时间窗口，但现有 repository 没有授权查询。复合外键只保证关系行内部同园（`db/initdb/01_create_schema.sql:1189-1207`）。
- 前端当前把 token、role 与 kindergartenId 放在 Redux 内存中，并发送 bearer token。Phase 1A 已停止从 localStorage、JWT payload 或 demo user ID 推断身份，但前端状态仍只用于体验，不能成为授权依据。

### Intended 约束

- [SPEC-0001](../../specs/SPEC-0001-auth-authorization-tenant-sensitive-data-boundaries.md) 要求每个业务请求由后端从有效 session、ACTIVE user、唯一 ACTIVE role assignment 和必要的 ACTIVE membership 派生授权上下文。
- [ADR-0016](ADR-0016-server-side-session-auth.md) 已选定 Spring Session + Redis + `httpOnly` cookie，明确取代 JWT；本 ADR 不把 JWT claim 模型搬进 session。
- [ADR-0009](ADR-0009-restore-auth-enforcement.md) 要求默认认证与公开白名单，但仅恢复 authenticated 不能解决 tenant 与资源关系授权。
- [ADR-0003](ADR-0003-multitenancy-kindergarten-id.md) 决定共享 schema + `kindergarten_id`；运行时隔离必须由应用层强制。
- [ADR-0017](ADR-0017-tls-https-termination.md) 是生产 session cookie 的硬前置，不改变授权语义。

## 决策（Decision）

采用 **服务端每请求权威解析 Effective Authorization Context、service 层集中授权 policy、repository 层显式 tenant/resource-scoped query**。Spring Session 只保存稳定身份和服务端验证后的会话状态；role、scope、membership 与资源访问必须以当前数据库事实为准。

### 1. Session Principal 与 Effective Context

Spring Session 中持久化最小 `SessionPrincipal`：

```text
principalName = "user:{userId}"
userId
loginId
roleAssignmentId
membershipId?        // PLATFORM scope 为空
establishedAt
```

约束：

- `principalName` 使用不可变 user ID，不使用可修改的 email/phone/loginId 做 session 索引主键。
- session 中持久化的 Spring Security `Authentication` 只证明已建立有效服务端 session；不得把业务 `ROLE_*` / scope 当作跨请求持久化的 `GrantedAuthority` 真相。method authorization 从当前 request 的 Effective Authorization Context 读取 role/capability。
- session 可保存 `selectedKindergartenId`，但只能由受保护的 tenant-context command 写入；客户端输入只是选择请求，不是授权事实。
- role、scope、membership status、selected tenant 有效性不得仅凭 Redis 中的序列化快照授权。
- 原始 session ID 属于 S0，不进入 API response、业务日志或 audit detail。授权上下文可持有内部 session handle；审计仅记录不可逆 fingerprint 或 correlation ID。

每个已认证请求由 `EffectiveAuthorizationContextResolver` 执行一次权威投影查询，生成不可变上下文：

```text
userId
effectiveRole
scopeType
scopeId
activeKindergartenId?     // 仅 KINDERGARTEN scope
selectedKindergartenId?   // 仅 PLATFORM scope，且已服务端验证
roleAssignmentId
membershipId?
sessionHandle             // 内部使用，不外泄
```

解析规则：

1. user 必须为 ACTIVE。
2. 必须恰有一条合法 ACTIVE role assignment，且与 session principal 的 assignment identity 一致；不得取最近一条或回退角色。
3. `KINDERGARTEN` scope 必须有且只有一个 ACTIVE membership，且它必须匹配 scopeId；membership 的 user、kindergarten 与 role assignment 必须一致，不得同时存在第二个幼儿园的 ACTIVE membership。
4. `PLATFORM` scope 的 scopeId 必须为空，且不得存在园级 membership。
5. 非法或漂移状态使当前 session 失效，请求返回 `401`。
6. 解析结果只缓存于当前 request，不跨请求作为授权真相复用。

### 2. 请求与分层边界

| 层 | 必须负责 | 禁止负责 |
| --- | --- | --- |
| Security filter / context resolver | 载入 session principal；权威解析当前 role/scope/membership；建立 request-scoped context；失效 session 返回 401 | 业务资源查询、Guardian/Teacher 关系判断 |
| Controller | 传输格式、Bean Validation、分页参数、调用 application service | 从 body/path/localStorage/JWT 推导 role 或 tenant；复制 policy 判断 |
| Method authorization | 默认拒绝；按 action + role + data classification 做粗粒度许可 | 用复杂 SpEL 查询数据库或承载资源关系 SQL |
| Application service | 在事务内调用集中 policy；使用 scoped repository port；执行业务不变量与写入 | 裸用 tenant entity 的 `findAll/findById` 后再内存过滤 |
| Resource policy | 统一 Guardian/Teacher/platform 资源关系判断；返回 allow/deny/hide 语义 | 直接序列化 response 或处理 HTTP |
| Repository / query port | 把 tenant、relationship、status、有效期放进 SQL/JPQL 条件 | 向业务 service 暴露不受限 tenant `findAll/findById` |
| Database | 复合 FK、唯一约束、事务与必要的锁，作为纵深防御 | 被视为完整 authorization engine |

启用 Spring Security method authorization，在 application service 边界使用少量自定义 meta-annotation 或集中 `AuthorizationManager`。注解只声明 action，例如 `CLASS_READ`、`CHILD_READ_S1`、`CAMERA_CONFIG_WRITE`；`AuthorizationManager` 读取 request-scoped Effective Authorization Context，而不是读取 session 中陈旧的业务 authority。角色矩阵与数据分类规则集中在 policy bean，不在每个 service 复制 if/else。

### 3. 平台角色的 Tenant Context

`POST /api/v1/auth/session/tenant-context` 采用以下语义：

1. 仅有效 PLATFORM scope session 可调用；园级账号调用返回 `403`。
2. 请求中的 kindergarten ID 是候选值。服务端必须验证 kindergarten 存在、可用，并验证当前平台角色有权进入该管理视图。
3. 验证成功后，服务端把 `selectedKindergartenId` 写入 session 并记录审计；切换同样审计。
4. 选择 tenant 不改变 `scopeType=PLATFORM`，不创建 membership，不授予园级 role。
5. 每个 action 仍按平台角色的数据分类矩阵授权。`PLATFORM_IT_ADMIN` 和 `SUPERADMIN` 不能因已选择 tenant 就访问人员 S1、live CCTV、录像或 detection evidence。
6. 需要 tenant context 但尚未选择时返回 `403`；目标 tenant 不存在或不可进入且需要隐藏存在性时返回 `404`。
7. tenant 被禁用后清除选中值；平台 principal 本身仍有效，后续需要 tenant 的操作返回 `403`，而不是把平台账号降级为园级账号。

### 4. Tenant-Aware Repository 与事务

- tenant-scoped list/get/update/delete 必须在查询条件中包含 effective kindergarten ID；禁止“先 `findById`，再比较 entity.kindergartenId”。
- Guardian/Teacher 资源查询必须把关系、status 和时间窗口放入查询，不得只检查“同园”。
- tenant entity 的原始 Spring Data repository 应限制在 infrastructure package；application service 只依赖资源专用 query/command port，避免继承的通用 `JpaRepository` 方法成为旁路。
- 平台级查询使用独立 port 和 contract，不与 tenant-scoped repository 复用任意 `kindergartenId` 参数。
- DTO 中的 `kindergartenId` 应逐步移除。迁移期若暂时保留，只能做一致性校验；实体关联仍从 Effective Authorization Context 派生，不一致按隐藏策略返回 `404`。
- 写入的所有关联 entity 必须通过同一个 scoped port 加载，复合 FK 只作为第二道防线。
- 授权检查、资源加载与业务写入必须位于同一个 `@Transactional` application service。对 S1 或权限敏感写入，role/membership revalidation 与资源写入必须使用行锁、条件更新或等价机制消除并发撤销 TOCTOU；具体锁实现留给 Implementation 设计，但不能只依赖 request filter 的早期检查。

### 5. HTTP 错误语义

| 情况 | HTTP | 规则 |
| --- | --- | --- |
| 无 session、session 过期、user/role/membership 不再有效 | `401` | invalidate 当前 session；不降级为匿名业务访问 |
| 已认证但 role/action/data classification 不允许 | `403` | 例如 Guardian 请求 CCTV、PLATFORM_IT_ADMIN 请求人员 S1 |
| CSRF 缺失或无效 | `403` | 由 Spring Security 统一处理 |
| role 允许该 action，但资源不在 effective tenant 或关系不存在 | `404` | 查询直接按 tenant/relationship 限定，避免暴露资源存在性 |
| 平台角色无权选择 tenant | `403` | 不执行 tenant lookup 细节回显 |
| 平台角色可选择，但目标 tenant 不存在或不可进入 | `404` | 使用通用 not-found body |
| 请求字段格式错误 | `400` | 不用于 authorization denial |

不得先以不受限查询确认资源存在，再决定返回 `403/404`；这会形成跨租户存在性 oracle。

### 6. Session 吊销与状态变化

- Spring Session Redis 使用 indexed repository，以稳定 `principalName` 查询并删除某用户的全部 session。
- logout 删除当前 session；“全部设备退出”、密码变更、user 禁用、role 撤销/替换、membership 结束由统一 `SessionRevocationService` 在状态事务提交后吊销该 user 的全部 session。
- 主动删除 Redis session 是快速路径；每请求权威解析是正确性兜底。即使删除消息延迟或某节点失败，下一请求仍因数据库状态无效返回 `401`。
- tenant context 切换只更新服务端 session 属性并审计，不改变 role/scope；角色或 membership 变化必须整会话吊销，不能原地降权后继续复用旧上下文。

### 7. Guardian / Teacher 资源策略扩展点

建立集中 `ResourceAccessPolicy` 扩展点，以 `AuthorizationAction + EffectiveAuthorizationContext + resource key` 返回 allow、forbid 或 hidden-not-found：

- `GuardianChildPolicy`：从 user 找到 ACTIVE guardian profile 与 ACTIVE membership，再验证 `child_guardian_relationships` 的 kindergarten、start_date 和 end_date。因关系表无 status，当前“有效”由日期窗口和两端状态共同定义。
- `TeacherAssignmentPolicy`：从 user 找到 ACTIVE teacher profile 与 ACTIVE membership，再验证 ACTIVE 且在时间窗口内的 `class_teacher_assignments`；访问 child/room/event 时继续通过 child-class、class-room、room-camera 等关系链限定。
- `KindergartenAdminPolicy`：要求同园 ACTIVE admin role/membership；涉及审批时再叠加 Teacher level 为 `DIRECTOR` / `VICE_DIRECTOR`，但 level 不能单独授予权限。
- `PlatformPolicy`：selected tenant 只是查询上下文；允许 action 仍由 PLATFORM_IT_ADMIN / SUPERADMIN 的 capability 和数据分类决定。

新增资源类型时扩展 policy 与 scoped query，不在 Controller 或每个 service 复制同一套 tenant 判断。

## 方案比较（Options）

| 方案 | 优点 | 代价 / 风险 | 结论 |
| --- | --- | --- | --- |
| A. role/scope 全量快照存入 session，直接用 `hasRole` + service 自行判断 tenant | 请求开销低，改动看似最少 | 状态撤销后 session 易陈旧；实质复制 JWT claim 信任模型；tenant/关系判断分散；平台选择 tenant 容易被误当授权 | 不采用 |
| B. 每请求权威投影 + 集中 method/resource policy + 显式 scoped repository | 撤销最迟下一请求生效；role/scope/tenant 来源清楚；查询天然防 IDOR；可按资源逐步迁移 | 每请求增加一次小型数据库投影查询；需重塑 repository 边界和补齐矩阵测试 | **推荐** |
| C. Hibernate 全局 filter 或 PostgreSQL RLS 自动注入 tenant | tenant 行级隔离强，遗漏单个查询时仍可能被拦截 | 平台跨园查询、Guardian/Teacher 关系与 connection pool 上下文复杂；隐藏行为难调试；仍不能替代 role/resource policy | 初始阶段不采用；可作为后续纵深防御 ADR |

## 测试矩阵与分阶段实施边界

### 必须覆盖的矩阵

| 维度 | 代表值 |
| --- | --- |
| Identity | anonymous、valid session、expired session、user disabled、role revoked、membership disabled |
| Role | GUARDIAN、TEACHER、KINDERGARTEN_ADMIN、PLATFORM_IT_ADMIN、SUPERADMIN |
| Scope | KINDERGARTEN、PLATFORM、platform selected tenant none/valid/invalid |
| Tenant | same tenant、other tenant、client kindergartenId tampered |
| Relationship | Guardian active/expired/absent；Teacher assignment active/expired/absent |
| Operation | list、get、create、update、delete、S1 detail/evidence |
| Result | allow、401、403、hidden 404；response 不泄露跨租户存在性 |

至少新增或扩展：

- `SecurityBoundaryIntegrationTest`
- `EffectiveAuthorizationContextIntegrationTest`
- `RoleAuthorizationIntegrationTest`
- `TenantIsolationIntegrationTest`
- `PlatformTenantContextIntegrationTest`
- `ResourceRelationshipAuthorizationIntegrationTest`
- `SessionRevocationIntegrationTest`
- 静态/架构检查：所有发布 operation 有 policy 分类；tenant service 不使用裸 `findAll/findById`；公共 DTO 不能以 role/status/kindergartenId 扩大权限。

### 分阶段边界

1. **Decision Gate**：已于 2026-06-14 由维护者 Accept；后续实现必须遵守本 ADR。
2. **Session Identity Foundation**：按 ADR-0016/0017 建 Redis session、最小 `SessionPrincipal`、login/session/logout、cookie、CSRF 与 HTTPS；移除 JWT/refresh 双轨。
3. **Context And Default Enforcement**：实现权威 context resolver、默认 authenticated、method authorization、平台 tenant-context command；未分类业务 endpoint 保持关闭。
4. **Tenant Repository Migration**：按资源切片迁移 tenant-aware list/get/write，优先 Class/Room/CCTV/DetectionSession 与仍发布的通用 CRUD。
5. **Relationship And Revocation**：接入 Guardian/Teacher policy、session 全量吊销、状态变更事务与审计。
6. **Release Gate**：完成全矩阵负向测试、frontend 去 token/去 tenant 推断、生产 compose/TLS/CI 门禁后，SPEC-0001 才能继续向 Implemented 推进。

本 ADR 不实现审批 UI、通知、AI 闭环、RRN 迁移、TLS 组件选型或数据库 schema migration。角色/scope 唯一约束的 schema 修复由后续 Implementation task 通过 ADR-0012 流程执行。

## 后果（Consequences）

- **正面**：服务端数据库成为 role/scope/membership 的唯一授权真相；session 可即时吊销且最迟下一请求拒绝；tenant 隔离进入查询边界；平台 tenant 选择与权限授予分离；Guardian/Teacher 关系可集中扩展。
- **负面 / 代价**：每请求增加小型授权投影查询；现有 codegen 风格的通用 repository/DTO 需要分阶段收敛；method security、事务顺序和 session 序列化需要明确测试。
- **影响范围**：backend security/auth/session、application services、tenant repositories、Controller contract、frontend auth client、Redis/production configuration、SPEC-0001 测试与实施计划。

## 合规与验证（Compliance）

- 默认白名单外 `/api/v1/**` 必须 authenticated；未分类 operation 不得发布。
- role、scope、membership、selected tenant 与资源关系必须由服务端解析；禁止信任客户端 role、kindergartenId、localStorage 或 JWT payload。
- tenant-scoped SQL/JPQL 必须包含 tenant 或关系条件；禁止 response 后过滤。
- 状态撤销后，Redis session 主动删除且下一请求权威校验返回 `401`。
- 同 tenant 正确角色允许；错误角色 `403`；跨 tenant/关系缺失使用隐藏 `404`。
- 生产上线仍受 ADR-0017 的 HTTPS、Secure cookie 与 HSTS 前置约束。
- 最终验证遵循 SPEC-0001 的 backend、frontend、compose、安全契约与 CI 门禁。

## 关联（References）

- [SPEC-0001：认证、授权、租户与敏感数据边界](../../specs/SPEC-0001-auth-authorization-tenant-sensitive-data-boundaries.md)
- [ADR-0003：以 kindergarten_id 实现多租户](ADR-0003-multitenancy-kindergarten-id.md)
- [ADR-0009：恢复后端鉴权强制](ADR-0009-restore-auth-enforcement.md)
- [ADR-0016：服务端会话鉴权](ADR-0016-server-side-session-auth.md)
- [ADR-0017：TLS/HTTPS 终结与强制](ADR-0017-tls-https-termination.md)
- [Spring Session 3.2 Redis configuration and principal session lookup](https://docs.spring.io/spring-session/reference/3.2/configuration/redis.html)
- [Spring Security 6.2 Method Security](https://docs.spring.io/spring-security/reference/6.2/servlet/authorization/method-security.html)
