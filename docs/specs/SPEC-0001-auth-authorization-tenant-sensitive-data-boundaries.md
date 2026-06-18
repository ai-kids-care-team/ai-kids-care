---
id: SPEC-0001
title: "认证、授权、租户与敏感数据边界"
status: Approved
implementation: Partial
owner: 维护者
created: 2026-06-10
updated: 2026-06-15
related_adrs:
  - ADR-0003
  - ADR-0009
  - ADR-0010
  - ADR-0014
  - ADR-0016
  - ADR-0017
  - ADR-0019
---

# SPEC-0001: 认证、授权、租户与敏感数据边界

## 目标结果（Outcome）

所有浏览器用户通过服务端会话完成身份认证；后端以服务端解析的有效角色与租户范围作为唯一授权依据，并对每个业务请求实施默认拒绝、角色校验、租户校验和必要的资源关系校验。密码哈希、RRN 哈希、摄像头凭据、推送 token、内部证据存储地址等敏感值不得通过通用 CRUD 暴露或被客户端直接写入。任何跨租户或高敏感数据访问都必须显式授权并留下可追溯审计。

本 Spec 定义目标行为与验收边界，不声明当前实现已经满足这些要求。

## 当前事实（Current Facts）

> **基线快照（2026-06-10）+ as-built 更新（2026-06-15，PR #89）**：本节为 spec 创建时的基线事实快照。其中关于"鉴权未启用 / 无运行时隔离"的若干条**已被实现取代**——会话认证（去 JWT）、默认拒绝白名单、每请求 Effective Authorization Context、集中 `@PreAuthorize` policy 与 tenant-aware 查询隔离均已落地合并。**当前权威状态以下方「实施记录」与 ADR-0016/0019/0017 为准**；以下条目保留为历史基线，已在变化处就地标注。

- `backend/.../config/SecurityConfig.java` 将 `/api/v1/**` 配置为 `permitAll()`，JWT filter 未加入过滤链，当前全部业务 API 可匿名访问。**（已变化 2026-06-15：现 `/api/v1/**` 默认 `authenticated`，移除 JWT filter/util，改 Spring Session + Redis。）**
- [ADR-0016](../decisions/adr/ADR-0016-server-side-session-auth.md) 已决定使用 Spring Session + Redis + `httpOnly` cookie 取代 JWT，但尚未实现。**（已变化：已实现并合并。）**
- `user_role_assignments` 已表达 `PLATFORM` / `KINDERGARTEN` scope；`user_kindergarten_memberships` 已表达用户与幼儿园的成员关系，但业务查询没有统一使用这些数据做运行时隔离。**（已变化：已发布租户资源由 EffectiveAuthorizationContext + tenant-aware 查询统一做运行时隔离。）**
- 多个 Controller、DTO 和前端调用仍接受客户端提供的 `kindergartenId`，该值尚未统一与服务端授权上下文绑定。Phase 1A 补充止血已移除前端从 JWT、localStorage 或 demo user ID 推断园区的逻辑，登录/刷新改为从 ACTIVE role assignment 返回服务端派生的园区 ID。
- Phase 1B 后，`POST /api/v1/auth/register` 对 `PLATFORM_IT_ADMIN` 在首次 persistence 前返回 `400`；`GUARDIAN`、`TEACHER`、`KINDERGARTEN_ADMIN`、`SUPERADMIN` 只创建 PENDING user、role assignment、业务档案和园级 membership。
- `AuthRegisterDTO` 不再发布客户端 `status` 字段；未知 `status=ACTIVE` 输入会被忽略。PENDING user、没有 ACTIVE role assignment 或存在多条 ACTIVE role assignment 的 user 登录/刷新返回通用 `401`，不再默认回退 `GUARDIAN` 或取最近一条。
- `DIRECTOR`（院长）和 `VICE_DIRECTOR`（副院长）属于 `LevelEnum`，不是独立 `UserRoleEnum`。Phase 1B 前后端都将这两个 level 约束为 `KINDERGARTEN_ADMIN` 申请；普通 `TEACHER` 不能通过伪造院长级别提交申请。
- Phase 1A 已从 `UserVO` 移除 `passwordHash`、email、phone，从 `ChildVO` 移除 `rrnEncrypted`、`rrnFirst6`、birth date、address，从 `GuardianVO` 移除 `rrnEncrypted`、`rrnFirst6`、address，从 `TeacherVO` 移除 `rrnEncrypted`、`rrnFirst6`、staff number 和 emergency contact；因人员/账户资料仍可被匿名枚举，四类 Controller 的公共读写 operation 现已全部关闭。
- `DeviceTokenVO` 与 `EventEvidenceFileVO` 已分别移除完整 `pushToken` 和内部 `storageUri`；因设备注册与证据元数据仍属受限数据，两类 Controller 的公共读写 operation 现已全部关闭。`CameraStream` 的 entity 仍内部存储 `sourceUrl`、`streamUser`、`playbackUrl` 和加密凭据列，但公共 `CameraStreamVO` 不返回可播放地址或凭据，且通用写删链已关闭。
- Kindergarten 公共 list/detail 和注册查找只返回最小目录字段；通用 `POST` / `PUT` / `DELETE` 与敏感 write DTO 已关闭。Graph、Audit Log、Notification controller 当前不发布公共 operation，等待资源授权或内部 command 架构后再开放。
- 后端未见 `@PreAuthorize`、统一 Authorization Context 或集中式 tenant enforcement；多处 tenant-scoped entity 使用不带租户条件的 `findById`。**（已变化 2026-06-15：已启用 `@EnableMethodSecurity` + 集中 `AuthorizationPolicy` + 每请求 `EffectiveAuthorizationContext`；已发布租户资源改用 `findByIdAndKindergarten_Id` 等带租户/关系条件的查询。）**
- 数据库复合外键可阻止跨园关联写错，但不能阻止应用读取或修改另一幼儿园的合法记录。
- `user_role_assignments` 允许 `PLATFORM` scope 使用 `scope_id=NULL`；当前普通 unique index 不足以阻止相同平台角色因 NULL 语义被重复授予，也未见约束强制 PLATFORM/KINDERGARTEN 与 `scope_id` 的合法组合。
- `audit_logs` 表仍存在，但公共 controller 当前不发布读写 operation；未见安全敏感操作的统一内部审计写入。其 `kindergarten_id` 为 `NOT NULL`，无法自然表达不属于某个幼儿园的平台级安全事件。
- 生产 TLS 已由 [ADR-0017](../decisions/adr/ADR-0017-tls-https-termination.md) 规定，但仓库当前仍以 HTTP 运行。**（已变化 2026-06-15：已起草 Caddy 边缘 TLS 终结 + 生产 `SESSION_COOKIE_SECURE=true`；端到端 HTTPS 待部署时验证，demo 仍 HTTP。）**

## 信任模型与定义

### 身份主体

- **Browser Principal**：通过服务端 session 认证的自然人用户。
- **Anonymous Principal**：没有有效 session 的请求者。
- **Service Principal**：AI、迁移、loader 等非浏览器进程。其凭据和授权不复用 Browser Principal；具体服务身份契约不在本 Spec 范围。

### 有效授权上下文（Effective Authorization Context）

每个已认证请求必须由后端构造不可由客户端伪造的上下文：

```text
userId
effectiveRole
scopeType
scopeId
activeKindergartenId (仅 KINDERGARTEN scope)
sessionId
```

上下文只能来自有效 session、ACTIVE 用户、ACTIVE 角色分配和 ACTIVE membership。请求参数、JSON body、localStorage、JWT payload 或前端推断值都不是授权依据。

### 数据分类

| 等级 | 示例 | 默认规则 |
| --- | --- | --- |
| S0 Secret / Credential | `password_hash`、原始密码、RRN 后 7 位与 hash、session id、reset/verification token、摄像头密码/密文/IV/key version、完整 push token、DB/Redis/Neo4j secret | 写入专用入口；永不进入通用 response、列表、日志、审计 detail |
| S1 Restricted PII / Safety Evidence | RRN 前 6 位、儿童资料、住址、个人电话/邮箱、紧急联系人、检测视频/图片、内部 `storageUri` | 仅业务必要角色和资源关系可访问；最小字段返回；访问需审计 |
| S2 Tenant Business Data | 班级、教室、摄像头元数据、事件状态、公告、通知规则 | 仅有效 tenant scope 内访问 |
| S3 Platform Metadata | 服务状态、模型版本、非敏感租户目录、聚合统计 | 依角色开放；不得夹带 S0/S1 |

## 范围（Scope）

### In Scope

- 服务端 session 登录、登出、会话失效、CSRF 和浏览器 cookie 行为。
- 公开端点白名单与所有业务端点默认需要认证。
- 匿名注册允许的角色、账户初始状态和特权账户 provisioning 边界。
- 角色授权、有效 scope 选择、服务端 tenant context 和跨租户拒绝规则。
- `GUARDIAN` 的儿童关系边界、`TEACHER` 的分配关系边界和园管理员的 tenant 边界。
- `PLATFORM_IT_ADMIN` 与 `SUPERADMIN` 的平台级最小权限边界。
- S0/S1 数据在请求 DTO、response VO、日志、审计和 Neo4j projection 中的处理规则。
- 安全相关错误状态、审计事件和自动化验证。
- 现有 Controller/API 的授权归类与迁移要求。

### Out of Scope

- RRN 从 BCrypt 迁移到 HMAC-SHA-256 + pepper 的数据迁移细节；该工作仍由 ADR-0010 跟踪。本 Spec 只要求立即停止暴露或直接写入存储 hash。
- TLS 终结组件选型与证书部署；由 ADR-0017 跟踪，但生产启用 session 前必须完成。
- 密码重置、验证码、邀请邮件和完整账户审批 UI 的产品流程。
- AI 直写 PostgreSQL 的 Service Principal 权限；由 ADR-0015 的实现 Spec 单独定义。
- 通知策略、检测事件事务复核和证据生命周期的完整业务流程。
- 全仓 API response envelope 重构；本 Spec 只固定安全相关 HTTP 语义。
- Neo4j 同步架构重做；本 Spec 仅规定图投影不得包含 S0，且 S1 必须有明确业务必要性。

## 公开端点与账户 Provisioning

### 公开端点白名单

生产目标白名单仅包括：

| Method / Path | 规则 |
| --- | --- |
| `POST /api/v1/auth/login` | 认证失败返回通用 `401`，不得暴露账号是否存在 |
| `POST /api/v1/auth/register` | 接受允许公开申请的角色，但只能创建 PENDING 申请，见下文 |
| `GET /api/v1/auth/register/availability` | 必须限速；响应不得返回用户资料 |
| 密码重置/验证码端点 | 仅在对应功能真正实现且具备限速、一次性 token 和防枚举后开放 |
| `OPTIONS /**` | 仅用于受控 CORS preflight |
| 健康检查 | 仅返回非敏感 readiness/liveness；如后续新增 |

Swagger/OpenAPI 在开发和测试环境可公开；生产环境必须关闭公开访问或限制为 `PLATFORM_IT_ADMIN`。`/auth/refresh` 在服务端 session 落地后移除。`/auth/logout` 必须要求有效 session。

### 匿名注册

1. 匿名调用者可以申请 `GUARDIAN`、`TEACHER`、`KINDERGARTEN_ADMIN` 或 `SUPERADMIN`。
2. `PLATFORM_IT_ADMIN` 不开放公开注册，只能通过受控 bootstrap、邀请或已认证平台管理流程创建。
3. 公开注册创建的 user、角色申请、业务档案和 membership 初始状态必须统一为 `PENDING`，在验证及审批完成前不得建立有效业务 session。
4. 客户端提供的 `status`、`scopeType`、`scopeId`、角色授予者或 ACTIVE 标志必须被拒绝或忽略；状态和 scope 由服务端决定。
5. 申请角色只代表待审批意图，不构成授权。审批操作必须验证审批主体、目标租户、申请状态和申请资料，并在单一事务中激活 user、档案、membership 和 role assignment。
6. `GUARDIAN`、`TEACHER` 及已有幼儿园的后续院长/副院长申请，由目标幼儿园现有院长或副院长审批。审批者必须持有该园 ACTIVE `KINDERGARTEN_ADMIN` role；`teachers.level` 必须为 `DIRECTOR` 或 `VICE_DIRECTOR`，且不得审批自己的申请。
7. 公开 Guardian 注册不得调用通用 `/children/rrn` 搜索并返回儿童列表。完整 RRN 只能提交给专用验证命令，结果使用通用成功/失败信息，不回显儿童 PII。
8. 公开 Teacher、院长或副院长申请不得仅凭客户端选择幼儿园即获得 ACTIVE membership 或 role。
9. `DIRECTOR` 和 `VICE_DIRECTOR` 获批后都使用 `KINDERGARTEN_ADMIN` role，职级差异保留在 `teachers.level`；审批授权检查依赖 role + tenant，不得仅凭客户端提交的 level。
10. `SUPERADMIN` 公开申请必须保持 PENDING，由 ACTIVE `PLATFORM_IT_ADMIN` 运维人员完成组织身份核验和审批；申请人不得参与自身审批。
11. 注册、批准、拒绝、撤销及重复申请均必须审计并具备限速、防自动化滥用和通用响应，避免账号与审批状态枚举。
12. 新幼儿园的首位院长不通过公开注册和审批链创建，而是通过受控账号发放流程 provision；发放过程必须验证幼儿园主体、固定 `DIRECTOR` level、`KINDERGARTEN_ADMIN` role 和目标 kindergarten，并记录操作审计。

具体审批 UI 和邀请机制可由后续 Spec 定义；在其落地前，PENDING 账户保持不可访问业务 API。

## 角色与权限（Actors And Permissions）

以下矩阵定义安全基线。尚未批准的访问默认拒绝。

| Actor | 允许行为 | Tenant / 数据边界 |
| --- | --- | --- |
| `Anonymous` | 白名单内登录、公开注册申请、受控查重 | 无业务数据访问；注册申请不产生 ACTIVE 权限 |
| `GUARDIAN` | 查看/维护本人账户的非敏感字段；查看与本人存在 ACTIVE guardian relationship 的儿童必要资料、相关公告和本人通知设置 | 只能访问关系表证明关联的儿童及其所属幼儿园；禁止查看 live CCTV、录像回放、检测 evidence、摄像头配置和内部存储信息 |
| `TEACHER` | 查看本人档案；查看被 ACTIVE assignment 分配的班级、教室、儿童和相关事件；在被授权范围内复核事件 | 必须同时满足 ACTIVE membership、tenant scope 和有效 teacher/class/room assignment |
| `KINDERGARTEN_ADMIN` | 院长和副院长管理本园用户申请、membership、班级、教室、摄像头元数据和检测事件；授予/撤销本园非平台角色 | 仅所属 kindergarten；不能读取 S0；不能创建平台角色；审批者 level 必须为 `DIRECTOR` 或 `VICE_DIRECTOR` |
| `PLATFORM_IT_ADMIN` | 平台运行、服务健康、AI model metadata、部署和非敏感租户目录 | 默认不得读取儿童/Guardian/Teacher PII、原始 evidence、通知正文或摄像头凭据；不得代替业务管理员修改 tenant 内容 |
| `SUPERADMIN` | 跨园治理、租户目录、多个幼儿园的管理入口和聚合监管数据；按明确业务权限管理 tenant | 暂时禁止读取具体儿童/Guardian/Teacher S1 数据、live CCTV、录像回放和 detection evidence；未来放宽必须由新 Spec/ADR 定义条件与审计 |

### 单账号、角色与 Scope 约束

- 一个自然人在业务上兼任平台角色、园级角色或 Guardian 时，必须使用不同账号。单个账号不得同时拥有不同 `UserRoleEnum`。
- `GUARDIAN`、`TEACHER`、`KINDERGARTEN_ADMIN` 账号只能有一个 `KINDERGARTEN` scope，且只能属于一个幼儿园。
- `SUPERADMIN`、`PLATFORM_IT_ADMIN` 使用单一 `PLATFORM` scope，可按其角色权限访问或管理多个幼儿园，但不得获得园级 role assignment 或 membership。
- 登录不得“取最近一条角色并静默使用”，也不得在角色缺失时回退 `GUARDIAN`。数据库和 service 必须保证每个账号只有一个合法 ACTIVE role assignment。
- 平台角色进入某个 tenant 管理视图时，目标 tenant 必须由服务端验证并记录为本次操作上下文；它不改变账号的 PLATFORM scope，也不能绕过该平台角色的数据分类限制。
- 角色或 membership 被禁用、撤销或过期后，相关 session 必须立即失效或在下一请求前拒绝。

## 业务流程（Business Flow）

### 1. 登录与建立 Session

1. 匿名用户提交 identifier 与密码。
2. 后端验证用户状态、密码和有效角色分配；失败统一返回 `401`。
3. 后端验证账号只有一个合法 ACTIVE role assignment。园级角色还必须有且只有一个匹配的 ACTIVE membership；不接受客户端指定角色或改变账号 scope。
4. 后端按唯一 role/scope 建立 session。平台角色可在后续请求中选择目标 tenant 管理视图，但该选择必须经过服务端验证。
5. session id 仅通过 `httpOnly` cookie 传输；生产使用 `Secure`、`SameSite=Lax`；写请求要求有效 CSRF token。
6. 响应只返回最小 session profile，不返回 token、hash 或内部 credential。

### 2. Tenant-Scoped 请求

1. Filter/interceptor 从 session 构造 Effective Authorization Context。
2. Controller/service 根据资源策略验证角色和 scope。
3. tenant-scoped 查询必须把 `activeKindergartenId` 作为强制条件。
4. path/body/query 中存在 `kindergartenId` 时，它只能作为业务一致性值；与 context 不同则拒绝，不能切换 tenant。
5. 对不属于当前 tenant 的资源 ID，返回 `404`，避免泄露资源存在性；角色整体无权执行某操作时返回 `403`。
6. 写入关联对象时，所有关联 entity 必须属于同一 effective tenant。

### 3. Guardian / Teacher 资源关系校验

1. Guardian 访问儿童数据时，后端验证 ACTIVE `child_guardian_relationships`。
2. Teacher 访问儿童、班级、教室或事件时，后端验证 ACTIVE membership 和请求时间点有效的 assignment。
3. 仅 tenant 相同但无业务关系，不足以授予 Guardian 或 Teacher 访问。
4. 关系不存在、已结束或状态非 ACTIVE 时拒绝并审计必要信息。

### 4. 敏感数据写入与读取

1. 原始密码、RRN、camera password 和 push token 仅由专用 command DTO 接收。
2. service 在内部完成 hash、加密或 credential 存储；客户端不能提交 `passwordHash`、`rrnHash`、ciphertext、IV 或 key version。
3. response mapper 按数据分类返回最小字段。S0 永不返回；S1 仅对获授权主体返回业务必要字段。
4. evidence 通过受授权的下载/流式端点访问；不得向浏览器返回内部 `file://`、容器路径或对象存储内部 URI。后续使用短期签名 URL 时也必须绑定授权和短有效期。
5. 日志、exception message 和 audit detail 对 S0/S1 做删除或掩码。

### 5. 登出与撤销

1. 已认证用户调用 logout，服务端删除 Redis session 并清除 cookie。
2. 密码变更、账户禁用、角色撤销、membership 结束和管理员强制下线必须使相关 session 失效。
3. 失效 session 后续请求返回 `401`，不得自动降级为匿名业务访问。

## 契约（Contracts）

### Auth API

| Contract | 目标 |
| --- | --- |
| `POST /auth/login` | 建立 server session；不返回 access/refresh token |
| `GET /auth/session` | 返回最小用户资料和唯一有效 role/scope |
| `POST /auth/session/tenant-context` | 仅平台角色选择服务端验证过的目标 kindergarten；不改变 PLATFORM scope |
| `POST /auth/logout` | invalidate session；幂等返回 `204` |
| `POST /auth/register` | 仅创建允许角色的 PENDING 申请 |
| `POST /auth/refresh` | session 模式下移除 |

建议的 session profile：

```json
{
  "userId": 123,
  "loginId": "user",
  "effectiveRole": "TEACHER",
  "scopeType": "KINDERGARTEN",
  "scopeId": 10
}
```

默认不包含 email、phone 或其它联系信息；只有经明确页面需求和字段级授权后才能增加。任何情况下都不得包含 hash、RRN、token 或 credential。

### Tenant Contract

- tenant-scoped browser API 的目标形态是不要求客户端提交 `kindergartenId`。
- 为兼容迁移暂时保留的 `kindergartenId` 必须与 effective context 完全一致；不一致返回拒绝。
- 所有 tenant-scoped `GET by id`、update、delete 和关联写入都必须通过 tenant-aware repository/service 查询。
- `PLATFORM_IT_ADMIN` / `SUPERADMIN` 的平台查询与 tenant context selection 必须使用不同 contract，不得借用任意 `kindergartenId` 参数绕过。
- 园级账号必须由数据库约束或等价强一致机制限制为单一 role、单一 kindergarten；平台账号必须限制为单一 PLATFORM role，且无园级 membership。
- role assignment 必须满足：`PLATFORM` scope 的 `scopeId` 为空，`KINDERGARTEN` scope 的 `scopeId` 为有效 kindergarten id；数据库约束必须把 NULL 视为同一平台 scope，防止重复授予同一角色。

### Sensitive DTO / VO Contract

以下字段从通用公共 contract 移除：

- `UserVO.passwordHash`
- `UserCreateDTO.passwordHash`
- `UserUpdateDTO.passwordHash`
- `ChildVO` / `GuardianVO` / `TeacherVO.rrnEncrypted`
- 对应 Create/Update DTO 的 `rrnEncrypted`
- `DeviceTokenVO.pushToken`
- `EventEvidenceFileVO.storageUri`

附加规则：

- `rrnFirst6` 默认不进入列表 response；仅在经批准的详情或验证流程中按业务必要性返回，优先返回 birth date 或掩码值。
- camera password、ciphertext、IV 和 key version 永不返回；`hasPassword` 可返回。
- `sourceUrl`、`streamUser` 仅 `KINDERGARTEN_ADMIN` 的受控配置接口可见；普通事件/CCTV 列表使用脱敏 view。
- push token 为 write-only；读取设备列表只返回 `deviceId`、platform、status、lastSeenAt 和掩码标识。
- audit log 不提供 public create/update/delete；由系统内部追加写入，获授权主体只读。
- Neo4j projection 不得包含密码 hash、RRN、地址、电话、email、push token 或 camera credential。

### 安全错误语义

| 情况 | HTTP |
| --- | --- |
| 未登录、session 失效、登录凭据错误 | `401` |
| 已登录但角色不允许 | `403` |
| tenant/resource relationship 不匹配且需隐藏存在性 | `404` |
| CSRF token 缺失或无效 | `403` |
| 请求字段无效 | `400` |
| 冲突或重复资源 | `409` |

错误 body 不得包含 stack trace、SQL、内部类名、secret 或 PII。

## 审计要求

至少记录以下事件：

- 登录成功、登录失败、登出、session 强制失效。
- 平台角色的 tenant context 选择和切换。
- 用户、角色、membership 的创建、批准、授予、撤销和状态变化。
- S1 数据读取：儿童详情、Guardian/Teacher 详情、检测 evidence 访问。
- 摄像头 stream 配置和 credential 更新。
- 跨租户访问尝试、角色拒绝和特权角色注册尝试。
- SUPERADMIN 进入具体 tenant context、reason 和访问资源；即使进入 tenant context 也不得访问当前禁止的 S1/evidence。

审计记录至少包含 actor user id、effective role/scope、action、resource type/id、tenant id、result、timestamp、request correlation id。IP/user-agent 按隐私和运维要求记录。不得记录原始请求 body、密码、RRN、token、credential 或 evidence 内容。

审计数据模型必须能表达平台级事件和幼儿园级事件。可通过 `scope_type + scope_id`，或等价的“平台事件允许 tenant id 为空”迁移实现；不得使用伪造 kindergarten id 代表平台。

## 不变量（Invariants）

- 除显式白名单外，任何 `/api/v1/**` 请求都必须有有效身份。
- 没有 ACTIVE role assignment 的用户不能建立业务 session；不得默认回退为 `GUARDIAN`。
- 单个账号只能有一个业务角色。园级账号只能属于一个幼儿园；平台账号不得拥有园级 membership。
- role assignment 的 scope type/id 组合必须合法且唯一，包括 `scope_id=NULL` 的平台角色。
- `KINDERGARTEN` scope 的 tenant id 只能来自服务端授权上下文。
- 任何 tenant-scoped 读写必须同时满足角色、scope 和必要的资源关系。
- 客户端提供的 `kindergartenId`、user id、role、status 不能扩大权限。
- 平台角色不是自动读取所有 PII 的通行证。
- S0 永不通过通用 API response、日志、错误或审计 detail 泄露。
- hash/ciphertext 是服务端存储表示，不是公共 API 可写字段。
- 通用 CRUD 不能创建、更新或删除审计记录。
- 授权检查和业务写入必须处于同一事务边界或使用能防止 TOCTOU 的等价机制。
- 前端隐藏菜单或 `ProtectedRoute` 仅用于体验，不构成安全控制。
- 生产 session 只有在 HTTPS、`Secure` cookie 和 CSRF 全部启用时才可上线。

## 分阶段交付

本 Spec 可拆为多个独立 Implementation session，但只有全部验收标准完成后才能标记 `Implemented`。

1. **暴露面止血**：公开注册仅创建允许角色的 PENDING 申请并拒绝 `PLATFORM_IT_ADMIN`；移除 S0 response/write DTO；关闭 audit 通用写删；补回归测试。
2. **会话认证**：Spring Session + Redis、login/session/logout、CSRF、前端去 JWT 化。
3. **授权与 Tenant Context**：默认认证、角色策略、context selection、tenant-aware query/write。
4. **资源关系与审计**：Guardian/Teacher relation policy、S1 访问审计、session 撤销。
5. **生产门禁**：TLS、CI 全套检查、负向安全测试和文档同步。

每阶段结束时仓库必须可构建、可测试，不允许以长期 `permitAll` 或双重身份机制作为过渡终态。

### 实施前决策门

“服务端派生 Effective Authorization Context、集中 tenant enforcement、平台角色进入 tenant context 的方式”属于跨模块且安全敏感的长期决策。[ADR-0019](../decisions/adr/ADR-0019-effective-authorization-context-tenant-enforcement.md) 已于 2026-06-14 由维护者 Accept，并关联 ADR-0003、ADR-0009、ADR-0016 与 ADR-0017。第 2/3 阶段实现必须遵守该 ADR，不得通过改写既有 Accepted ADR 隐式改变历史。

## 验收标准（Acceptance Criteria）

### Authentication

- [ ] 除白名单外，匿名访问每个业务 Controller 的代表性 endpoint 均返回 `401`。
- [ ] 正确凭据建立 Redis-backed session；response 不含 access/refresh token。
- [ ] session cookie 为 `httpOnly`；生产为 `Secure` + `SameSite=Lax`。
- [ ] 无效密码返回通用 `401`，不暴露账号存在性。
- [ ] logout 后旧 session 无法继续访问业务 API。
- [ ] 用户禁用、角色撤销或 membership 结束后，现有 session 被立即或在下一请求前拒绝。
- [ ] 写请求缺少或伪造 CSRF token 时返回 `403`。

### Registration And Privilege

- [ ] 匿名请求 `GUARDIAN`、`TEACHER`、`KINDERGARTEN_ADMIN` 或 `SUPERADMIN` 只创建一致的 PENDING 申请，不能直接访问业务 API。
- [x] 匿名请求 `PLATFORM_IT_ADMIN` 被拒绝且不创建任何数据。
- [x] 客户端提交 `status=ACTIVE`、scope 或授权者字段不能改变服务端结果。
- [x] 通用 child RRN 搜索不再匿名返回儿童资料。
- [ ] Guardian、Teacher 及后续院长/副院长申请只能由同园、ACTIVE、level 为 `DIRECTOR` 或 `VICE_DIRECTOR` 的 `KINDERGARTEN_ADMIN` 批准，且不能自批。
- [ ] `DIRECTOR` 和 `VICE_DIRECTOR` 获批后都具有 `KINDERGARTEN_ADMIN` role，普通 Teacher 不因伪造 level 获得审批权。
- [x] 现有 `register_superadminRole_returns201WithUserId` characterization 测试被替换为“创建 PENDING 且不能登录”的目标测试。

### Authorization And Tenant Isolation

- [ ] 每个 Controller endpoint 都有明确的公开、authenticated、role 或 resource policy；不存在未归类的业务 endpoint。
- [ ] 同园正确角色可以执行获授权操作。
- [ ] 错误角色访问同一资源返回 `403` 或策略规定的 `404`。
- [ ] tenant A 用户不能通过 path id、query、body 或分页/list API 读取、更新、删除 tenant B 数据。
- [ ] tenant A 用户不能把 tenant B entity 关联到 tenant A 写入。
- [ ] 修改客户端 `kindergartenId`、localStorage、Redux 或 demo user id 不会改变后端授权结果。
- [ ] Guardian 只能访问有 ACTIVE relationship 的儿童。
- [ ] Guardian 无法访问 live CCTV、录像回放、检测 evidence 或内部存储 URI。
- [ ] Teacher 只能访问有效 assignment 覆盖的班级/教室/儿童/事件。
- [ ] 园级账号无法获得第二个 role 或第二个 kindergarten membership；平台账号无法获得园级 role/membership。
- [ ] 平台角色选择目标 kindergarten 时由服务端验证，且选择 tenant 不会扩大该角色的数据分类权限。
- [ ] 非法 role/scope 组合和重复平台角色授予被数据库约束或等价强一致机制拒绝。
- [ ] 平台 IT 默认无法读取 tenant S1 数据。
- [ ] SUPERADMIN 即使进入 tenant context，也无法读取具体人员 S1、live CCTV、录像回放或 detection evidence。

### Sensitive Data

- [ ] 所有 API response 和 OpenAPI schema 中不存在 `passwordHash`、`rrnEncrypted`、`rrnHash`、camera ciphertext/IV/key version 或完整 push token。
- [ ] 通用 Create/Update DTO 不接受 password hash、RRN hash 或 camera ciphertext。
- [ ] list/detail/error/log/audit 中不泄露 S0。
- [ ] evidence response 不返回内部 `storageUri`。
- [ ] 非园管理员的 CCTV/event view 不返回 `sourceUrl` 或 `streamUser`。
- [ ] Neo4j loader/projection 不写入 S0，且默认不写入地址、电话、email 或 RRN。

### Audit And Verification

- [ ] 登录、登出、平台 tenant context 切换、角色变更、跨租户拒绝和 S1/evidence 读取产生审计记录。
- [ ] 平台级安全事件可在不伪造 kindergarten id 的情况下写入审计。
- [ ] 审计 API 对业务用户不可写、不可删；审计记录不包含 S0。
- [ ] 后端集成测试至少覆盖匿名/已认证、正确/错误角色、同租户/跨租户、关系存在/不存在和敏感字段 absence。
- [ ] 前端不再从 JWT、localStorage 或 demo user ID 推导权限或 tenant。
- [ ] CI 运行 backend tests、frontend lint/build 和 production compose config；任一失败阻断部署。
- [ ] security architecture、API 文档、personas/roles 和本 Spec implementation notes 与最终实现同步。

## 验证（Verification）

| 检查 | Command / Test | 预期结果 |
| --- | --- | --- |
| 后端全部测试 | `cd backend; .\gradlew.bat test` | session、授权、tenant、敏感字段负向测试全部通过 |
| 前端 lint | `cd frontend; npm.cmd run lint` | 0 errors |
| 前端构建 | `cd frontend; npm.cmd run build` | static export 成功 |
| 生产 Compose | `docker compose -f docker-compose.yml -f docker-compose.prod.yml config` | Redis/TLS/服务配置可解析且无不安全 fallback |
| Forbidden response fields | 生成 OpenAPI 后检查 `passwordHash|rrnEncrypted|rrnHash|pushToken|storageUri` | 公共 response schema 不包含禁用字段 |
| 匿名访问矩阵 | `SecurityBoundaryIntegrationTest` | 白名单外代表 endpoint 均为 `401` |
| 角色矩阵 | `RoleAuthorizationIntegrationTest` | allow/deny 与本 Spec 一致 |
| 租户隔离 | `TenantIsolationIntegrationTest` | list/get/write/delete 均无法跨园 |
| 注册与审批边界 | `RegistrationSecurityIntegrationTest` | PLATFORM_IT_ADMIN 公开注册被拒绝；其它允许角色仅创建一致的 PENDING 记录且不能登录 |
| 敏感字段 | `SensitiveDataContractTest` | response、错误和审计中不存在 S0 |
| 文档链接 | 仓库 Markdown link check | 无失效内部链接 |
| 最终 diff | `git diff --check` | 无 whitespace error |

## 已确认的产品决策

1. 除 `PLATFORM_IT_ADMIN` 外，`GUARDIAN`、`TEACHER`、`KINDERGARTEN_ADMIN`、`SUPERADMIN` 均可公开提交注册申请，但只能进入 PENDING，审批前不能登录业务系统。
2. `TEACHER` 只能访问其有效 assignment 实际覆盖的班级、教室、儿童和事件。
3. `GUARDIAN` 禁止观看 live CCTV、录像回放和检测 evidence。
4. `SUPERADMIN` 暂时禁止查看具体人员 PII、live CCTV、录像回放和检测 evidence；未来流程明确后通过新 Spec/ADR 放宽。
5. 单账号单角色。园级账号只能属于一个幼儿园；平台角色使用独立账号并可按权限查看、管理多个幼儿园。
6. Guardian 和 Teacher 申请由同园院长或副院长审批。代码模型中两者是 `LevelEnum`，因此二者都必须具有 `KINDERGARTEN_ADMIN` role，不能仅凭 level 获得审批权。
7. 新幼儿园的首位院长通过受控账号发放创建，不进入公开注册审批链。
8. `SUPERADMIN` 的公开申请由 ACTIVE `PLATFORM_IT_ADMIN` 运维人员核验组织身份并审批。

## 开放问题（Open Questions）

无。后续若改变公开注册角色、账号发放或审批责任，必须更新本 Spec；涉及跨模块安全边界变化时新增 ADR。

## 实施记录（Implementation Notes）

仅在本 Spec `Approved` 后填写。每个实施阶段需记录：

- 对应提交和改动文件。
- 新增/更新测试。
- 数据迁移或兼容策略。
- 验证命令和结果。
- 已知风险、临时限制和后续 Spec/ADR。

### 2026-06-10 Phase 1A：敏感数据暴露止血

- 状态：已随提交 `478967d security: complete sensitive API stop-bleed` 进入远端 `develop`；独立终审结论为 `FINAL REVIEW: PASS`。
- 公共 response 已移除 `passwordHash`、`rrnEncrypted`、完整 `pushToken`、内部 `storageUri`，并进一步从四类通用人员 response 移除公开环境下不应发布的 email、phone、RRN 前 6 位、儿童 birth date/address、Guardian address、Teacher staff number 和 emergency contact；前端类型已同步。
- User、Child、Guardian、Teacher 的通用 Create/Update DTO、service/mapper generic 写链和全部公共 Controller operation 均已关闭；内部 VO 仍保持最小字段，供未来受控 contract 复用。
- `DeviceToken`、`EventEvidenceFile` 的通用 DTO/service/mapper 写链和全部公共 Controller operation 已关闭。`CameraStream` 通用 create/update/delete 链关闭，但保留不含播放地址或凭据的 GET metadata。`pushToken`、`storageUri`、`sourceUrl`、`streamUser`、`playbackUrl` 与 camera credential 存储表示继续留在内部 entity。
- `DetectionEvent` 的全部公共 operation 已关闭；`CctvCamera` 与 `DetectionSession` 的通用写删链已关闭并保留脱敏 GET。DetectionSession Create/Update DTO 及 service/mapper 写链已删除。`EventReview`、`NotificationRule`、`Superadmin`、`AppreciationLetter` 当前不发布公共 operation。感谢信与检测事件前端路由保留但显示暂不可用，不发送匿名 API 请求。
- CCTV 前端仅为 `TEACHER` / `KINDERGARTEN_ADMIN` 在登录响应提供有效 kindergarten scope 时加载 camera/stream 元数据，不再下载 DetectionEvent、扫描 Teacher 目录或请求不存在的 `/common_codes/code_group/detection_events`。DetectionEvent 公共 list/detail 已关闭，相关前端路由只显示待授权提示。公共 CameraStream 不再返回播放地址，幼儿园直播 fallback 已移除，因此 Session/授权专用播放接口实现前不提供 live stream。
- 浏览器登录态与 bearer token 只保存在 Redux 内存，不写入或恢复自 localStorage；401 清空内存会话并提示重新登录，刷新页面同样需要重新登录。前端不再从 JWT、localStorage 或 demo user ID 推断园区。
- 尚未实现安全语义的 `logout`、修改密码、密码重置和验证码 controller mapping 已撤下；对应前端入口显示暂不可用，OpenAPI contract 固定这些路径和 schema 不被发布。
- Kindergarten 公共 list/detail 与注册查找只发布 `id/name/regionCode/code/status` 等最小目录字段；通用 `POST` / `PUT` / `DELETE`、Create/Update DTO 与 service/mapper 写链已关闭。注册查找不回显 business registration number、address 或联系人信息。
- Graph、Audit Log、Notification controller 当前不发布公共 operation；Neo4j graph repository、audit/notification 内部 service 和存储模型保留，等待资源授权或内部 command 架构后再接回。
- 新增 `SensitiveResponseContractTest`、`SensitiveWriteContractTest`、`SensitivePublicApiClosureContractTest` 和独立的 `PublishedOpenApiContractTest`；后者使用隔离的 Spring Boot MockMvc context 发布真实 `/v3/api-docs`，不加载 DataSource / JPA / Flyway / Neo4j / Testcontainers，并限制 S1 字段只能出现在显式批准的专用 command schema。补充测试固定 `playbackUrl` absence、DetectionSession 写链 absence/405，以及 User/Child/Guardian/Teacher/DeviceToken/EventEvidenceFile/DetectionEvent path 与 schema absence。
- 2026-06-13 最终预审验证：完整 backend `test` 通过，共 51 项（49 passed、0 failed、0 errors、2 个 ADR-0013 预期 skip）；P2 聚焦的 auth service/controller 与关闭路径共 28 项单独通过。
- frontend production build 通过并生成 20 个静态页面；Phase 1A 的 39 个已修改前端源码文件 scoped ESLint 为 0 error / 0 warning。全仓 lint 仍被 4 个既有 error 和 8 个 warning 阻断，均位于本阶段未修改文件。
- Session、授权、tenant isolation、其余资源的 S1 字段级授权与最小化，以及内部 append-only audit writer 仍待后续阶段实现。

### 2026-06-11 Phase 1B：公开注册权限收敛

- 状态：已随提交 `478967d security: complete sensitive API stop-bleed` 进入远端 `develop`；独立终审结论为 `FINAL REVIEW: PASS`。
- 公开注册允许 `GUARDIAN`、`TEACHER`、`KINDERGARTEN_ADMIN`、`SUPERADMIN` 提交申请；user、role assignment、业务 profile 和园级 membership 统一写为 `PENDING`。客户端 `status=ACTIVE` 不属于公开 DTO，无法改变服务端结果。
- `PLATFORM_IT_ADMIN` 公开注册在首次 persistence 前返回 `400`，不会创建 user/profile/role/membership。
- Guardian 的 kindergarten scope 和 membership 只从服务端匹配到的儿童记录派生，客户端提交的 `kindergartenId` 不参与授权范围写入。
- 返回完整 `ChildVO` 的通用 `/children/rrn` 已关闭；新增专用 `POST /auth/guardian-child-verifications`，只返回 `{verified}`，不回显 child ID、姓名、班级或其他 PII。注册事务仍会再次验证完整 RRN。
- 公开注册与儿童验证的 RRN 字段同时在 DTO 与 service 层要求纯数字 6/7 位，非法格式在 persistence 或儿童查询前返回 `400`。
- `KINDERGARTEN_ADMIN` 申请只接受 `DIRECTOR` / `VICE_DIRECTOR` level；普通 `TEACHER` 申请拒绝这两个管理员 level。前端对院长和副院长统一提交 `KINDERGARTEN_ADMIN`。
- login/refresh 要求 ACTIVE user 且 ACTIVE role assignment 数量严格等于一；错误凭据、无效 refresh token、缺失或多条 ACTIVE role 均由 Auth API 显式返回通用 `401 {"error":"Authentication failed"}`，不依赖受保护 `/error`、不回退 `GUARDIAN` 或取最近一条。ERROR dispatcher 仅用于保留真实 MVC 错误状态，已关闭路径在完整应用和 standalone MVC/OpenAPI contract 中固定为 `404` / absence。前端同时拒绝缺失/未知 role 或空 access token 的登录响应。PENDING 申请使用正确密码登录仍返回通用 `401`。
- 前端移除 `PLATFORM_IT_ADMIN` 注册选项及专用表单，儿童验证只显示通用结果；注册 payload 使用按角色区分的显式 TypeScript 联合类型，不接受 `childId`、`status` 或 scope 字段。注册成功后只显示待审批提示，不再自动登录或写 token/localStorage。
- `AuthEndpointTest` 覆盖四类允许角色的 PENDING persistence、平台角色零落库、客户端 ACTIVE 无效、Guardian 伪造园区无效、儿童验证最小响应与非法 RRN 拒绝、role/level 不一致拒绝、PENDING/无 ACTIVE role/多 ACTIVE role/无效 refresh token 的显式 401 契约，以及园级 ACTIVE role 的服务端 `kindergartenId` 返回；纯 Mockito service test 固定同一异常语义。`DetectionEventEndpointTest` 固定完整应用中的关闭路径 `404`，`PublishedOpenApiContractTest` 固定旧 RRN 与 DetectionEvent path absence、专用验证 path presence、最小响应 schema 和公开注册 DTO 完整字段集合。
- 已知限制：审批 endpoint/激活事务尚未实现；`child_guardian_relationships` 无 status 列，Guardian 申请仍创建关系行，但 PENDING guardian/membership/role 使其当前不构成有效授权关系。`/api/v1/**` 仍处于 `permitAll` 演示态，因此“不能登录”尚不等于业务 API 已受保护；Session、Redis、tenant enforcement 和资源关系授权不在本阶段范围。

### 2026-06-15 Phase 2/3（会话认证 + 有效授权上下文与默认拒绝）

- 状态：实现于分支 `codex/spec-0001-auth-context`（基线 `478967d`），**已随 [PR #89](https://github.com/ai-kids-care-team/ai-kids-care/pull/89) 合入 `develop`**（merge commit `36cfdd4`；后续 `651ec20` 文档清扫）。本小节连同下方切片 1-5 与「已发布 VO 主键 id」修复，构成该分支合入 `develop` 的整体内容。遵循 [ADR-0019](../decisions/adr/ADR-0019-effective-authorization-context-tenant-enforcement.md) 的分阶段边界（阶段 2、3 完成，阶段 4、5 部分完成）。本节为实施证据/as-built 记录；下方“验收标准”勾选保留给维护者评审，本阶段未自行翻转。
- 会话认证（阶段 2）：引入 Redis 后端 Spring Session（`store-type=redis`、`repository-type=indexed`）、最小可序列化 `SessionPrincipal`（`user:{id}` + roleAssignmentId + membershipId）。`/auth/login` 建立服务端会话、只返回最小 `AuthSessionVO`（`@JsonInclude(NON_NULL)`，无 access/refresh token）；新增 `GET /auth/session`、`POST /auth/logout`（幂等 204）、`GET /auth/csrf`。启用 cookie CSRF（`XSRF-TOKEN` + `X-XSRF-TOKEN`）、`IF_REQUIRED` 会话、显式 SecurityContext 持久化、会话 cookie `AI_KIDS_CARE_SESSION`（httpOnly、`secure` 由 `SESSION_COOKIE_SECURE` 控制、`SameSite=Lax`）。删除 `JwtUtil`/`JwtAuthenticationFilter`/`TokenVO`/`TokenTypeEnum`/refresh、logout DTO 与 JJWT 依赖；前端去 bearer/localStorage、改 `withCredentials` + CSRF bootstrap + 会话 hydration（新增 `SessionBootstrap.tsx`、`csrf.ts`）。
- 有效授权上下文与默认拒绝（阶段 3）：新增 `EffectiveAuthorizationContextFilter`（接在 `SecurityContextHolderFilter` 之后）每请求调用 `EffectiveAuthorizationContextService.resolve` 做权威投影——校验 ACTIVE user、恰一条 ACTIVE role assignment 且与 principal 一致、KINDERGARTEN scope 的唯一 ACTIVE membership、PLATFORM scope 无 membership；非法/漂移 invalidate 会话并返回通用 `401`。`@EnableMethodSecurity` + 集中 `AuthorizationPolicy`（`@PreAuthorize` 读取 request-scoped context，不读会话内陈旧 authority）。`SecurityConfig` 默认 `/api/v1/**` `authenticated()`。`POST /auth/session/tenant-context` 仅 PLATFORM 角色可选已验证 tenant，写入会话且不改 scope/role、不授予园级权限；园级角色调用 `403`。`ApiExceptionHandler` 将 `EntityNotFoundException` 统一为隐藏式 `404 {"error":"Resource not found"}`。
- Tenant Repository Migration（阶段 4，部分）：Class/Room/CCTV/CameraStream/DetectionSession/Announcement 的部分 list/get/write 改为 tenant-aware 查询（`findByIdAndKindergarten_Id` 等）+ effective kindergarten 约束 + 客户端 `kindergartenId` 仅一致性校验、不一致按隐藏 `404`。**未完成**：DetectionEvent 仍信任客户端 `kindergartenId` 且未接集中 policy；全部发布 operation 的分类未完成。
- 测试：`AuthEndpointTest` 扩展至 29 项，覆盖会话登录/无 token 401/园级有无 membership、平台角色有 membership 401、错误密码/无 ACTIVE role/多 ACTIVE role 的通用 401、CSRF 契约、refresh 关闭 404、logout 失效、role 撤销下一请求 401 并失效会话、平台 tenant-context 选择不改角色、园级角色禁选平台 tenant-context（403）、平台选 tenant 不获 camera 读（403）、园级角色禁读平台 AI 元数据（403）、跨租户 Class 隐藏（list 不含外租户 + detail 404）、写入拒绝客户端 kindergarten 覆盖（404）、camera list 拒绝 kindergarten 覆盖（404）。
- 验证（2026-06-15，本地）：`cd backend; .\gradlew.bat test` → 8 类 / 61 tests / 0 failures / 0 errors / 2 预期 skip（`FlywayMigrationTest`，ADR-0013）。前端 21 改动文件 scoped ESLint 0 error / 0 warning；`npm run build` 成功生成 20 静态页。`git diff --check` PASS。全仓前端 lint 仍有既有 4 errors / 8 warnings（均在本阶段未修改文件）。
- 环境备注（与产品代码无关，仅影响测试运行机器）：在**用户 Temp 路径含空格/撇号**的机器上，JDK 21 wepoll selector 的 AF_UNIX wakeup pipe 会失败（“Unable to establish loopback connection”），须以 `JAVA_TOOL_OPTIONS=-Djdk.net.unixdomain.tmpdir=<无空格/撇号的目录>` 运行测试（当时机器使用 `D:\tmp`）；Temp 路径本身不含空格/撇号的机器无需此项。
- 已知限制 / 后续：阶段 4 未完（DetectionEvent + 半完成资源的 tenant repository migration）；阶段 5 Guardian/Teacher 关系策略、全会话吊销（`SessionRevocationService`）、状态事务衔接与安全审计未实现；阶段 6 TLS（ADR-0017）、生产 compose、CI 全套门禁与 fresh independent review 未完成。在全部验收标准完成并通过发布门前，本 Spec 不得标记 `Implemented`。

#### 切片 1（2026-06-15）：默认拒绝白名单收敛 + operation 分类核验

- 收敛 `SecurityConfig` 公开白名单：移除已删除/已关闭、违反「白名单外必须有身份」不变量的 3 个匹配项——`POST /api/v1/auth/refresh`（端点已删）、`GET /api/v1/children/rrn`（已关闭、前端改用 `/auth/guardian-child-verifications`）、`GET /api/v1/detection_events/**`（已关闭、前端仅经 `/common_codes?context=detection_events` 取枚举）。它们现落入默认拒绝：匿名 → `401`，不再是匿名 `404` 存在性 oracle。
- 收敛后的公开白名单 = CSRF bootstrap、`register/availability`、`login`/`register`/`guardian-child-verifications`、以及注册流程登录前所需的 S3 只读目录/参考（`kindergartens`/`common_codes`/`menus` 的 GET；前端 `useSignupForm`/`TopBar` 登录前调用，无 S0/S1）。Swagger/api-docs 公开仅限非生产（生产关闭留 TLS/生产门禁切片）。
- 核验：所有 active controller 的发布 operation 均已显式 policy 分类——`ai_models`(CRUD)/`common_codes`(写) = `PLATFORM_*`；`classes`/`rooms`/`announcements`(CRUD)、`detection_sessions`(读) = `TENANT_*`；`cctv_cameras`/`camera_streams`(读) = `TENANT_CAMERA_READ`；`kindergartens`/`menus`/`common_codes` 的 GET = 公开 S3 目录/参考。其余 14 个 controller 不发布任何 operation，统一默认拒绝。
- 测试：新增 `SecurityBoundaryIntegrationTest`（匿名访问矩阵：发布业务端点 `401`、关闭 controller `401`、公开白名单 `200`）；`AuthEndpointTest` 的 `refresh_isClosed`/legacy 与 `DetectionEventEndpointTest` 的关闭路径断言由 `404` 改 `401`。两个 standalone 契约测试（`SensitivePublicApiClosureContractTest`/`SensitiveWriteContractTest`）不经安全链、仍验证「无 handler」契约，未受影响。
- 验证：先以更新后的断言跑 RED（5 项失败精准定位过宽白名单），移除匹配项后跑 GREEN——后端 `gradlew test` 64 通过 / 0 失败 / 2 预期 skip。`git diff --check` PASS。本切片仅改后端（`SecurityConfig` + 4 个测试文件），前端未触碰。

#### 切片 2（2026-06-15）：跨租户隔离测试锁定（Tenant Repository Migration）

- 核查结论（事实）：**所有已发布的租户控制器其服务层查询已完成 tenant-aware 迁移**——`ClassService`/`RoomService`（`findByIdAndKindergarten_Id`/`findAllByKindergarten_Id` + `requireSameKindergarten` 写覆盖校验）、`AnnouncementService`（`findByIdAndActiveAuthorMembership` + author 取自 context）、`CctvCameraService`/`CameraStreamService`/`DetectionSessionService`（经关系链 `findBy...CctvCameras_Kindergarten_Id`）。先前“部分”表述偏保守。
- 故本切片为**纯测试锁定**（无生产代码改动）：新增 `TenantIsolationIntegrationTest`，以 kindergarten 2 的 TEACHER/KINDERGARTEN_ADMIN 为主体、kindergarten 1 既有 seed 资源为“真实但外租户”，断言：(1) `rooms`/`cctv_cameras`/`camera_streams`/`detection_sessions` 的 get-by-id 用合法外租户 id → 隐藏 `404 {"error":"Resource not found"}`；(2) `rooms` list 仅含本租户行且本租户 get-by-id 可达 `200`（证明 404 是租户限定而非一刀切）；(3) `KINDERGARTEN_ADMIN` 写入携带外租户 `kindergartenId` → 隐藏 `404`，不切换租户。
- DetectionEvent（事实修正）：`DetectionEventController` 不发布任何 operation，`DetectionEventService` 在生产代码中无任何注入/调用方，当前不可经 HTTP 触达；其信任客户端 `kindergartenId` 的问题留待控制器/AI-sink 重开时处理，不在本切片范围。
- 验证：`TenantIsolationIntegrationTest` 3 项通过；全量后端 `gradlew test` 67 通过 / 0 失败 / 2 预期 skip。`git diff --check` PASS。仅新增一个后端测试文件，前端未触碰。
- 未完成 / 后续：tenant 实体的原始 `JpaRepository`（裸 `findAll`/`findById`）仍暴露给 service，ADR-0019 §4 的“repository port 边界收敛”属架构性改造，留后续切片；Guardian/Teacher 关系策略（切片 3）、会话吊销/审计（切片 4）未动。

#### 切片 3（2026-06-15）：Teacher assignment-级资源策略（classes 首个资源）

- 维护者决定（选项 1）：把 Teacher 的资源访问从“园级”收紧为“有效 assignment 级”。本切片落地**第一个资源 classes**（关系链最浅、教师核心资源）；rooms/cameras/detection_sessions 沿 class-room-camera 关系链的收紧留后续切片。
- 实现 ADR-0019 §7 的 `TeacherAssignmentPolicy`：`GET /classes` 的 list 与 get-by-id 对 `TEACHER` 仅返回其**有效 assignment** 覆盖的班级（teacher 档案 ACTIVE + `class_teacher_assignments` ACTIVE 且 `start_date <= today < end_date/∞`）；`KINDERGARTEN_ADMIN` 保持园级全量。同租户但未分配的班级 → 隐藏 `404`（关系条件写进 JPQL EXISTS 子查询，不做加载后过滤）。
- 改动：`ClassRepository` 新增两条 assignment-scoped JPQL（list/get）；新增 `TeacherAssignmentPolicy` bean（集中“谁是 assignment-scoped”的判定，role==TEACHER）；`ClassService` 的 list/get 按 policy 分流。create/update/delete 仍为 `KINDERGARTEN_ADMIN`-only（`TENANT_S2_WRITE`）、园级，不变。
- Guardian 半：相关控制器仍关闭，无实时消费端，本切片未动（待开放 guardian 资源时再接 `GuardianChildPolicy`）。
- 测试：新增 `TeacherAssignmentAuthorizationIntegrationTest`（3）——TEACHER 仅见有效 assignment 的班级（assigned get→200、unassigned get→隐藏 404、list 计数=1）；DISABLED 或过期 assignment → 见空（list=0、get→404）；`KINDERGARTEN_ADMIN` 园级全量（list=全部、unassigned get→200）。
- 既有缺陷发现（不在本切片范围，已另开任务）：`ClassMapper.toVO` 用 `@Mapping(target="classId", ignore=true)` 永远忽略 `classId`，导致 `GET /classes` 返回的 `classId` 恒为 null。注意 `kindergartenId` 仍被正常映射（`source="kindergarten.id"`），不受影响；本切片测试改用 list 计数 + URL path id 规避了 null 的 `classId`，未修该 mapper。
- 验证：先 RED（2 个 teacher 测试失败、admin 控制项通过）→ 实现后 GREEN；全量后端 `gradlew test` 70 通过 / 0 失败 / 2 预期 skip。前端**不调用 `/classes`**（grep 确认），收紧对前端零影响，无需前端改动。`git diff --check` PASS。

#### 切片 3 续（2026-06-15）：Teacher assignment-级 rooms（关系链第二跳）

- 按 SPEC teacher 可见“교실/rooms”继续收紧：room 对 TEACHER 可见 = room ←(ACTIVE `class_room_assignment`，窗口含 now)← class ←(ACTIVE `class_teacher_assignment`，窗口含 today)← 该 teacher（teacher 档案与两段 assignment 均 ACTIVE）；`KINDERGARTEN_ADMIN` 仍园级。`GET /rooms` 的 list/get-by-id 同 classes 模式按 `TeacherAssignmentPolicy` 分流，未分配房间隐藏 `404`。
- 实现：`RoomRepository` 两条 assignment-scoped JPQL（嵌套 EXISTS：room→class_room_assignment→class_teacher_assignment；两表时间窗类型不同——`class_teacher_assignments` 用 `date`、`class_room_assignments` 用 `timestamptz`，分别传 `today`/`nowTs`，关系条件全部写进 SQL）；复用 `TeacherAssignmentPolicy`；`RoomService` 的 list/get 分流，写操作仍 `KINDERGARTEN_ADMIN`-only 园级。
- 测试：新增 `TeacherRoomAssignmentAuthorizationIntegrationTest`（3，造 fresh class+room+完整关系链）。并把切片 2 的 `TenantIsolationIntegrationTest` 主体由 `TEACHER` 改为 `KINDERGARTEN_ADMIN`——因 TEACHER 现为 assignment-级，纯租户隔离测试应以**园级主体**表达（其任一 `404` 来自租户边界而非 assignment 收紧）。
- 未做（SPEC 边界判断，待维护者定）：`cctv_cameras`/`camera_streams`/`detection_sessions` 对 TEACHER 的可见性——SPEC actor 矩阵中 teacher 仅列“班级/교실/儿童/相关事件”，未明确列入 live CCTV/stream/session，是否沿 room-camera 链对 teacher 收紧（还是干脆不向 teacher 开放）需产品判断，本片不越界。
- 验证：RED（2 teacher room 测试失败、admin 通过）→ GREEN；全量后端 `gradlew test` 73 通过 / 0 失败 / 2 预期 skip。前端**不调用 `/rooms`**（grep 确认），零影响。`git diff --check` PASS。

#### 切片 3 续（2026-06-15）：Teacher 禁止访问 surveillance（cameras/streams/sessions）

- 维护者裁定：**teacher 不能**看 cameras / streams / sessions（SPEC actor 矩阵中 teacher 仅列“班级/교실/儿童/相关事件”，未列 live CCTV/stream/session）。故把这三类收为 **`KINDERGARTEN_ADMIN`-only**，teacher 访问返回 `403`。
- 实现：`AuthorizationAction.TENANT_CAMERA_READ` 改名为 `TENANT_SURVEILLANCE_READ` 并在 `AuthorizationPolicy` 收为 admin-only；`CctvCameraService`/`CameraStreamService` 改挂 `TENANT_SURVEILLANCE_READ`；`DetectionSessionService` 由 `TENANT_S2_READ`（teacher+admin）改挂 `TENANT_SURVEILLANCE_READ`。`TENANT_S2_READ`（classes/rooms，teacher assignment-级）保持 teacher+admin。
- 前端：`CctvDashboardPage` 的 `canViewLiveStreams` 收紧为仅 `KINDERGARTEN_ADMIN`——教师走受限视图、不再请求已 `403` 的 camera/stream 接口（前端隐藏仅 UX，后端 `403` 才是安全控制）。
- 测试：`AuthEndpointTest` 新增 `teacher_cannotReadSurveillanceResources`（teacher GET `cctv_cameras`/`camera_streams`/`detection_sessions` → `403`；前两者带 `kindergartenId` 以越过必填参数绑定 `400`、抵达 method authorization）；`cameraList_rejectsClientKindergartenOverride` 主体由 teacher 改为 `KINDERGARTEN_ADMIN`（teacher 已无 camera 权限）。
- 验证：RED（teacher→403 测试失败；其间发现 cctv/streams 缺 `kindergartenId` 先返回 400，补参修正）→ GREEN；全量后端 `gradlew test` 74 通过 / 0 失败 / 2 预期 skip；前端 `CctvDashboardPage` scoped ESLint 0 error/warning，生产构建 20 页成功。`git diff --check` PASS。

#### 切片 4（2026-06-15）：会话吊销机制（部分）

- 实现 ADR-0019 §6 的 `SessionRevocationService`（Redis indexed）：按 principalName `user:{id}` 经 `FindByIndexNameSessionRepository.findByPrincipalName` 查出并删除该 user 的全部 session（principalName 与 `SessionPrincipal.getName()` 一致）。
- 新增 `POST /api/v1/auth/logout-all`（authenticated + CSRF）：吊销当前 user 的全部 session（含当前会话），返回 `204`。这是该机制当前唯一可用的实时触发端点。
- 强化吊销回退测试：除既有 role 撤销 → `401`，新增 **user 禁用 → 401**、**membership 结束 → 401**（均由 `EffectiveAuthorizationContextFilter` 每请求权威重解析兜底，覆盖对应验收项）；并验证 `logout-all` 后该 user 的两个会话都 `401`。
- 已确认未做（阻断/超范围）：状态变更**主动触发**（角色撤销/用户禁用/membership 结束在管理操作的状态事务提交后调用 `SessionRevocationService`）暂无实时端点可挂——相关 admin 管理控制器仍关闭；每请求重解析已保证正确性（撤销后下一请求 `401`），待这些 admin 端点发布时再在事务提交后接入吊销快路径。**安全审计写入**受 `audit_logs.kindergarten_id NOT NULL`（无法表达平台级事件）的 schema 限制，需 ADR-0012 迁移，本片不做。前端暂无“全部登出”按钮（端点已就绪，UI 后续加）。
- 验证：RED（`logout-all` 失败，user 禁用 / membership 结束通过）→ GREEN；全量后端 `gradlew test` 77 通过 / 0 失败 / 2 预期 skip。`git diff --check` PASS。

#### 切片 5（部分，2026-06-15）：生产 TLS（Caddy）+ Secure cookie + compose-config CI

- 维护者裁定 **TLS 组件 = Caddy**（ADR-0017 决策第 1 条"二选一属实现"），并回溯更新 ADR-0017（implementation In Progress + 选型 + 部署时验证说明）。
- 起草边缘 TLS 终结：`infra/caddy/Caddyfile`（自动 ACME 证书 + 强制 HTTP→HTTPS + HSTS + 反代现有 frontend nginx）。
- `docker-compose.prod.yml`：新增 `caddy` 服务独占宿主 `80/443`；`frontend` 用 Compose `!reset` 取消基线宿主端口发布（生产仅经 Caddy 暴露）；`backend` 设 `SESSION_COOKIE_SECURE=true`（依赖 HTTPS 边缘）。
- CI：新增 `.github/workflows/compose-config.yml`，校验 base（demo）与 merged production compose 可解析（对应 SPEC 验收的 production compose config 门）。
- 验证：`docker compose -f docker-compose.yml -f docker-compose.prod.yml config` 通过；合并结果确认 frontend 无宿主端口、caddy 占 80/443、backend `SESSION_COOKIE_SECURE: "true"`。**仅 compose 结构性验证**——真实证书签发、端口绑定、HTTPS 端到端**须部署时验证**（本地/CI 无域名/证书，无法验证 TLS 本身）。
- 未做（部署时/后续）：前端 lint/build CI gate（受全仓 4 个**既有** lint error 阻断，需先修或单独决定 gate 策略）；TLS 端到端与 HSTS preload 提交；生产 `.env`（`DOMAIN`/`ACME_EMAIL`）。负向测试矩阵（匿名 401、跨租户 404、角色 allow/deny、平台 tenant-context、吊销）已在切片 1-4 的测试类中基本覆盖。

#### 修复（2026-06-15）：已发布 VO 主键 id（原 ClassMapper 缺陷扩展）

- 切片 3 发现的 `ClassMapper.toVO` 忽略 `classId`，经扫描属 **codegen 通病**：13 个 mapper 的 `toVO` 都 `@Mapping(target="<x>Id", ignore=true)`。其中**已发布控制器**受影响 4 个——`AiModelMapper(modelId)` / `ClassMapper(classId)` / `RoomMapper(roomId)` / `DetectionSessionMapper(sessionId)`，导致其 GET 返回的 VO 主键 id 恒为 null（`kindergartenId` 等其它字段不受影响）。
- 修复这 4 个（`ignore=true` → `source="id"`）。新增 `AuthEndpointTest.publishedVos_includeTheirPrimaryId` 断言四类 get-by-id 返回非空主键（RED → GREEN）。
- 其余 9 个 mapper 属**关闭控制器**（User/Guardian/Teacher/AuditLog/DeviceToken/EventEvidenceFile/Notification/NotificationRule/Superadmin），是同源潜在问题，待其控制器重开时一并修，不在本次范围。
- 验证：全量后端 `gradlew test` 78 通过 / 0 失败 / 2 预期 skip。`git diff --check` PASS。

#### 切片 6（2026-06-16）：安全审计 writer（候选 #1，审计要求 §270-298）

- 本节为实施证据 / as-built 记录；`### Audit And Verification` 验收勾选保留给维护者评审，本切片未自行翻转（且 §367 多项跨前端 / CI / 负向测试，非本切片可独立闭合）。schema 前置见 [ADR-0021](../decisions/adr/ADR-0021-admin-audit-schema-migration.md)（V2）。
- 新增 `com.ai_kids_care.v1.security.audit` 包：`AuditAction` / `AuditResult` 枚举（应用层约束 `action`/`result` varchar）、`AuditEvent`（不可变载荷）、`SecurityAuditWriter`（**直写 `audit_logs`，不复用被关闭的 `AuditLogService` CRUD 栈**；独立事务 `REQUIRES_NEW` 使拒绝 / 失败在业务回滚路径上仍持久；best-effort 写入失败只记日志、不阻断业务；只落结构化字段，从 MDC 取 correlation id、从当前请求取 ip/user-agent；PLATFORM 强制 `kindergarten_id=NULL`）、`CorrelationIdFilter`（`HIGHEST_PRECEDENCE`，随机 / 净化入站 `X-Correlation-Id`→MDC + 响应头，不用 session id）、`SecurityAuditAccessDeniedHandler`（已认证 403 → `AUTHORIZATION_DENIED/DENIED` + 统一 JSON）。
- 接入点：`AuthController`（登录成功 / 失败、登出、`logout-all`、tenant-context 切换成功 / 拒绝）；`KindergartenAdminApprovalService` / `PlatformAdminApprovalService` 6 处审批 hook（成功 `SUCCESS`，捕获 `EntityNotFoundException`/`ResponseStatusException` 写 `DENIED` 后重抛——覆盖细粒度 403 与跨租户隐藏 404）；`SecurityConfig` 接 `accessDeniedHandler`。平台事件 `scope_type=PLATFORM` 且 `kindergarten_id=NULL`（不伪造 kg id——§284），所选 tenant 落 `resource_id`。S1/evidence 读取预留 `AuditAction.S1_EVIDENCE_READ`（对应控制器空壳、无活动调用点）。as-built 同步 `security-architecture.md §7`。
- 测试：新增 `SecurityAuditIntegrationTest`（按 `X-Correlation-Id` 精确定位本次请求审计行）——登录成功 / 失败 / 登出、tenant 切换平台 scope 无伪造 kg id、园级 approve 角色变更、错误角色 403 经 AccessDeniedHandler 写 DENIED、审计行不含 S0、审计 API 对业务用户不可读 / 写 / 删。
- 验证：**本机无 Java/JAVA_HOME**，后端测试由 GitHub Actions「Backend Java Tests」（Testcontainers + initdb→Flyway V2）执行；本地 `git diff --check` PASS。
- 已知限制 / 后续 OQ：拒绝洪泛限流 / 采样（每个 403 / CSRF 失败写一行）；可信 `X-Forwarded-For` 客户端 IP（待边缘反代定案 [ADR-0017](../decisions/adr/ADR-0017-tls-https-termination.md)，现记 `remoteAddr`）；DB 级 append-only `REVOKE`/触发器（维护者另定，ADR-0021 §4）；残留极罕见孤儿 SUCCESS（业务 commit 阶段失败时，REQUIRES_NEW 审计已提交）。

#### 切片 7（2026-06-16）：Guardian→child 关系策略（资源关系 §3 / §349）

- 维护者设计决策（2026-06-16）：① 关系活跃语义 = **`end_date` 窗**（`end_date IS NULL OR end_date >= today`），**不加 status 列、无 schema 迁移**（`child_guardian_relationships` 仍无 status 列；Guardian 审批前 membership/role 为 PENDING → 登录即被拒，批准后关系行已存在即活跃，故 end_date 窗充分）。② 首切片**仅 Guardian→child 读**（Teacher→child / 感谢信 / 通知作后续）。
- 实现：新增最小 `GuardianChildVO(childId, name, status)`（不含 RRN/address/birth_date/childNo）；`AuthorizationAction.GUARDIAN_CHILD_READ` + `AuthorizationPolicy`（`tenantIdentity && role==GUARDIAN`）；`ChildRepository` 两条关系-scoped JPQL（活跃关系 EXISTS 子查询在 SQL 内强制：guardian 档案 ACTIVE + `Guardian.user_id` 匹配 + end_date 窗 + 同租户 + child ACTIVE）；`ChildrenService.listRelatedChildren`/`getRelatedChild`；`ChildrenController` 重开 `GET /children` 与 `GET /children/{id}`（仅 GUARDIAN）。无 ACTIVE 关系（跨租户 / 不存在 / 已结束）→ **审计 `AUTHORIZATION_DENIED`（复用切片 6 writer）+ 隐藏 404**（§3.4）。完整 `ChildVO`、通用 create/update/delete 仍关闭（写操作 → 405）。
- 契约护栏更新（重开敏感资源的护栏，非移除）：`SensitivePublicApiClosureContractTest`（ChildrenController 不再空壳）、`SensitiveWriteContractTest`（children 写 → 405）、`PublishedOpenApiContractTest`（`/children`·`/children/{id}` GET 由 absent→present、锁 `GuardianChildVO` 仅 3 字段；`ChildVO` 仍 absent、`/children/rrn` 仍 absent）。`SensitiveResponseContractTest` 的 `ChildVO` 字段断言不变（`getChild`/`ChildVO` 保留未动）。
- 测试：新增 `GuardianChildAuthorizationIntegrationTest`——仅 ACTIVE 关系儿童可见、无关系/已结束/跨租户 → 隐藏 404、无关系写 DENIED 审计行、TEACHER → 403、未认证 → 401、响应最小字段无 S0/S1。
- 验证：本机无 Java，后端由 GitHub Actions「Backend Java Tests」执行；`git diff --check` PASS。
- defer（记录）：成功 S1 读取审计（跨切面，后续）；className/gender 字段；**Teacher→child / 事件**（§351 余项）；感谢信 / 通知 Guardian 资源。

#### 切片 8（2026-06-16）：负向测试套件——错误响应敏感数据 absence（§390「error」腿）

- Discovery 盘点（事实）：§372 负向矩阵（匿名/已认证、正确/错误角色、同租户/跨租户、关系存在/不存在、敏感字段 absence）五个维度均已被现有 9 个安全/契约测试类覆盖；§390「response、错误和审计中不存在 S0」的 **response 腿**（`SensitiveResponseContractTest`/`PublishedOpenApiContractTest`）与 **审计腿**（`SecurityAuditIntegrationTest.auditRecords_doNotContainS0`）已覆盖，唯 **error 腿**无测试。本切片只补此缺口，不重复造已覆盖项。
- 事实核验：错误响应当前均为固定 `{"error":"..."}` 或空体——`ApiExceptionHandler`(EntityNotFound→隐藏 404 定值)、`AuthController` 局部 handler(`MethodArgumentNotValidException`→仅 `getDefaultMessage()`、不含 rejectedValue；`ResponseStatusException`→`getReason()`)、`HttpStatusEntryPoint`(401 空体)、`SecurityAuditAccessDeniedHandler`(403 `{"error":"Access denied"}`)。`application.yml` 未设 `server.error.*`，故 Spring Boot 默认 `include-message/binding-errors/stacktrace=never`。即错误腿**当前安全 by construction**，本测试为**回归护栏**。
- 测试：新增 `ErrorResponseSensitiveDataIntegrationTest`（4 项，集成测试经真实安全链）——(1) 400 注册校验失败植入口令/RRN 金丝雀不回显；(2) 400 报文不可解析不回显原始字节、不带 stacktrace/exception；(3) 403 CSRF 缺失不回显提交口令；(4) 401 未认证不暴露 S0/内部。共享 `assertNoSensitiveLeakage` 扫描金丝雀明文 + S0/内部存储字段名（camel+snake：`passwordHash`/`rrnEncrypted`/`ciphertext`/`pushToken`/`storageUri`/`sourceUrl`/`streamUser` 等）+ 内部信息 JSON key（`"trace"`/`"exception"`/`"stackTrace"`）。隐藏 404 `{"error":"Resource not found"}` 契约已由 GuardianChild/AdminApproval 覆盖，不重复。
- 验证：本机无 Java，后端由 GitHub Actions「Backend Java Tests」执行；`git diff --check` PASS。仅新增一个后端测试文件，无生产代码改动，前端未触碰。
- 未做 / 后续（ops 子项，与负向测试套件分离）：loader×Flyway 竞态（OQ-OPS-1）、备份（OQ-OPS-4）、生产 `.env`；以及 §372/§390 验收勾选保留维护者评审（多项跨前端/CI，非本切片可独立闭合）。

#### 切片 9（2026-06-16）：Neo4j loader 去 S0/PII 投影（§365）

- 取证：ops 排查（OQ-OPS-1）发现 `db/ne4j_kindergartens/` 的 loader 把 S0/PII 投影进 Neo4j 节点，违反 §365「Neo4j loader/projection 不写入 S0，且默认不写入地址、电话、email 或 RRN」。全量盘点 6 个含投影的脚本（User×2[CSV+PG]、Kindergarten、Teacher、Child、Guardian）；其余 no400/700/800/900/950/1000 仅结构/关系属性、无敏感字段。
- 实现（sub-agent[sonnet] 落地，Lead fresh-context 复审后并入）：从每个脚本的**可执行投影**（Cypher `SET` + 参数；`db100_insert_users.py` 另含 SQL `SELECT` 与 `normalize_user_row`）移除 §365 禁止字段——User: `password_hash`(S0)/`email`/`phone`；Kindergarten: `address`/`contact_phone`/`contact_email`；Teacher: `rrn_encrypted`/`rrn_first6`/`emergency_contact_phone`/`emergency_contact_name`；Child: `rrn_first6`/`rrn_encrypted`/`birth_date`/`address`；Guardian: `rrn_encrypted`/`rrn_first6`/`address`。保留 id/姓名/`login_id`/`status`/结构/关系/时间戳字段。CSV 源快照不动（不在 Neo4j 内）。
- 既有 demo 图清理：新增 `no000_scrub_sensitive.py`（幂等 `REMOVE` 五个 Label 的上述属性，因 MERGE+SET 不删旧属性），并接为 `run_all.sh` 首条；`SETUP_GUIDE.md` 同步。
- 边界保留（Lead 决定，待维护者复核）：`business_registration_no`（法人登记号，非个人 PII）与 `contact_name`（联系人姓名；§365 禁止集为 地址/电话/email/RRN/S0，未含「姓名」）予以保留；如需更严最小化可后续收紧。
- 验证（静态）：本机 Python 3.12 对 7 个变更脚本 `python -m py_compile` 全过；`git diff --check` PASS；SET↔参数一致性逐脚本核对无悬挂 `$param`。
- 验证（运行时，2026-06-16，本机 Docker 起 db[含 initdb 种子]+neo4j 全栈）：跑全部 loader 后,Neo4j 五 Label 经 `sum(CASE … IS NOT NULL …)` 全量统计——**禁止字段计数全部为 0**（User 1000 节点 / Kindergarten 3 / Teacher 60 / Child 420 / Guardian 840），KEEP 字段（login_id/status/name/child_no）非空。`no000` scrub 另经「注入金丝雀 → 运行 scrub → 计数归 0 且节点总数 1000 不变」验证其清理既有图有效。CI 仍不覆盖 loader（运行时验证为本机 Docker 一次性手动执行）。
- 已知（非本切片缺陷）：`run_all.sh` 在 **Windows 工作树**（`autocrlf=true`）签出为 CRLF，本机构建镜像后 `exec ./run_all.sh` 因 `#!/bin/bash\r` 失败（"no such file or directory"）；仓库内为 LF，故 GitHub Actions/CD（Linux 签出）与演示机（拉 GHCR 预构建镜像）不受影响。仅影响本机 Windows 构建。可加 `.gitattributes` `*.sh text eol=lf` 根治（待维护者定）。本次运行时验证以直接 `python <script>.py`（Python 容忍 CRLF）绕过。
- 复审：实现=sub-agent，复审/集成+运行时验证=Lead（≠实现会话），符合 ADR-0020 sub-agent fresh-review 要求。

#### 切片 10（2026-06-16）：关闭 controller mapper 主键修复（T6 #6，闭合「修复（2026-06-15）」遗留项）

- 「修复（2026-06-15）：已发布 VO 主键 id」当时只修 4 个**已发布** mapper（AiModel/Class/Room/DetectionSession），并记「其余 9 个关闭 controller mapper 同源、待重开时一并修」。本切片提前清掉该债。
- 修复 9 个关闭 controller mapper 的 `toVO`：`@Mapping(target="<x>Id", ignore=true)` → `@Mapping(source="id", target="<x>Id")`——UserMapper(userId)/GuardianMapper(guardianId)/TeacherMapper(teacherId)/AuditLogMapper(auditId)/DeviceTokenMapper(deviceId)/EventEvidenceFileMapper(evidenceId)/NotificationMapper(notificationId)/NotificationRuleMapper(ruleId)/SuperadminMapper(superadminId)。9 个 entity 主键 Java 字段均为 `id`（列名 `<x>_id`），故 `source="id"` 一致正确；其余 nested-id 映射不动。
- 测试：这些 controller **仍关闭**（无发布端点可断言），改动与 4 个已发布、已被 `AuthEndpointTest.publishedVos_includeTheirPrimaryId` 运行时验证的 mapper **同范式**，由 MapStruct 编译（CI）校验 `source="id"` 合法。`SensitiveWriteContractTest` 仅断言这些 mapper 的 `toEntity`/`updateEntity` 缺席（不受 `toVO` 改动影响）；无测试断言旧的 null-id 行为。重开任一 controller 时按 ClassMapper 范式补端点级断言。

#### 切片 11（2026-06-16）：Teacher assignment-scoped 儿童读取（§351，T2 余项之一）

- §351 + 产品决策 #2：TEACHER 只能访问其有效 assignment 覆盖的儿童。复用 T2 的 `GET /children`·`/children/{id}` 端点与最小 `GuardianChildVO`（不新增公共面）。
- 实现：`AuthorizationAction.GUARDIAN_CHILD_READ` 泛化为 `CHILD_READ`（粗粒度门 = `tenantIdentity && (GUARDIAN || TEACHER)`，KINDERGARTEN_ADMIN 不入门）；`ChildrenService` 两方法按 `context.role()` 分流（GUARDIAN→关系查询；TEACHER→assignment 查询；default→空）；`ChildRepository` 新增两条 teacher 嵌套 EXISTS JPQL（child → ACTIVE `child_class_assignment`（日期窗含 today）→ class → ACTIVE `class_teacher_assignment`（日期窗含 today）→ teacher 档案 ACTIVE + `teachers.user.id` 匹配；同租户 + child ACTIVE，关系条件全在 SQL 内强制）。无 assignment / 已结束 / 跨租户 → 审计 `AUTHORIZATION_DENIED` + 隐藏 404（与 guardian 同路径）。`ChildrenController` 与契约测试不动（发布的 path/VO 不变）。
- 测试：`GuardianChildAuthorizationIntegrationTest` 删除已失效的 `children_teacherRole_returns403`（teacher 不再 403）；新增 `TeacherChildAuthorizationIntegrationTest`（仅 ACTIVE assignment 班级内儿童可见、无 assignment 同租户→404+DENIED 审计、CTA 已结束→404、跨租户→404、未认证→401、最小字段无 S0/S1）。
- 实现=sub-agent[sonnet]，复审/集成=Lead（≠实现会话，ADR-0020）。本机无 Java → CI 唯一验证（JPQL 镜像 RoomRepository 嵌套 EXISTS + ChildRepository guardian 范式 + TeacherAssignmentAuthorizationIntegrationTest 的 enum/helper 范式，逐一核对）。
- defer（§351 余项）：Teacher→detection_events（耦合 [ADR-0015](../decisions/adr/ADR-0015-ai-detection-closed-loop.md) AI 闭环，需设计）、感谢信 / 通知（notifications = 实现 [ADR-0018](../decisions/adr/ADR-0018-notification-subsystem.md) Accepted + Flyway 迁移，独立 session）。

#### 切片 12（2026-06-16）：通知只读子系统 + V3 迁移（ADR-0018 A3d / OQ-DATA-3）

- 前置 V3 迁移（OQ-DATA-3）：放宽 `notifications` 的 `sent_at`/`fail_reason` DROP NOT NULL + `retry_count` DEFAULT 0（待发态），`Notification` 实体同步可空性（见 commit `125d835`）。
- A3d 通知**只读**：重开 `NotificationController` GET `/notifications`·`/notifications/{id}`，返回**最小** `NotificationReadVO`（notificationId/title/body/status/createdAt——排除 channel/dedupeKey/sentAt/failReason/retryCount/recipientUserId/kindergartenId）。`AuthorizationAction.NOTIFICATION_READ` 粗门 = `tenantIdentity && (GUARDIAN || TEACHER || KINDERGARTEN_ADMIN)`；`NotificationService` 按角色分流（KINDERGARTEN_ADMIN → 园级查询；其余受体 → recipient-scoped 自己的）；细粒度作用域由 `NotificationRepository` 4 条 JPQL 在 SQL 内强制（用 notifications 直连 `kindergarten_id` 列做租户）。无权限（他人通知/跨租户/不存在）→ 审计 AUTHORIZATION_DENIED + 隐藏 404。`Notification` 实体新增 `kindergarten` 关联映射既有 `kindergarten_id` 列；`NotificationMapper` 的 closed 写方法加 `@Mapping(target="kindergarten", ignore=true)`。完整 `NotificationVO`、写链仍关闭（写 → 405）。
- 契约护栏（镜像 T2）：`SensitivePublicApiClosureContractTest`（NotificationController 移出空壳列表/404 representative）、`PublishedOpenApiContractTest`（`/notifications` GET present + 锁 `NotificationReadVO` 5 字段；完整 `NotificationVO` 仍 absent）、`SensitiveWriteContractTest`（notifications 写 → 405 + 传 service mock）。
- 测试：新增 `NotificationReadAuthorizationIntegrationTest`（受体仅见自己、他人通知隐藏 404 + DENIED 审计、KINDERGARTEN_ADMIN 见全园、跨租户 404、未认证 401、最小字段无内部/S0）。
- 实现=sub-agent[sonnet]，复审/集成=Lead（≠实现会话，ADR-0020）。本机无 Java → CI 唯一验证（Lead 复核：JPQL/契约镜像 T2、admin 无 profile 经 `EffectiveAuthorizationContextService.resolve` 确认可解析、membership ON CONFLICT 经 `uq_ukm_kg_user` 唯一索引确认有效、channel/status enum 值核对）。
- defer 仍在：Teacher→detection_events / 感谢信 / 通知**写链与规则**（notification_rules）/ AI 闭环（ADR-0015）。

#### 提案 / Proposed（待 Lead 批准）：DetectionEvent / EventReview 重开前置条件（N-7，2026-06-17 审计）

> **状态：Proposed / 待 Lead 批准。本节为非规范性说明，不修改本 Spec 的任何需求、范围或验收标准。**

`DetectionEventController` 与 `EventReviewController` 于 Phase 1A（2026-06-10）作为止血措施被有意关闭。该闭合是暂时止血，不是对底层问题的修复。2026-06-17 复核审计（FLOW-001）确认相关问题代码仍在代码库中，重开前必须满足以下三项前置条件，否则将把 P0 安全债带回可访问 API 路径。

**前置条件（须全部满足，并由维护者显式批准重开）：**

1. **前后端契约 casing 统一**：`DetectionEvent` / `EventReview` 的前后端字段命名需在 snake_case（后端 JSON）与 camelCase（前端 TypeScript）之间完成系统性统一，消除 FLOW-001 记录的字段不匹配问题。
2. **review 与 status 写入收敛为单一原子事务**：`EventReview` 创建与关联 `DetectionEvent` 状态字段更新必须处于同一事务边界，符合本 Spec 不变量「授权检查和业务写入必须处于同一事务边界或使用能防止 TOCTOU 的等价机制」（§299）。
3. **tenant-aware policy 接入**：重开的控制器必须遵循 SPEC-0001 的 authz-read-slice 模式，通过 `@PreAuthorize` 门 + 角色分支 + scoped JPQL 实现租户策略，不得仅依赖已关闭状态作为安全边界。

**根因记录**：FLOW-001（`docs/assessments/2026-06-17-followup-audit.md`，表格第 4 行 + §N-7）。

**当前状态**：`DetectionEventController` 已于 2026-06-17（N-6）删除（由空壳→不存在，保护更强）；`EventReviewController` 保持空壳（零 handler）。两者均不可经 HTTP 触达，作为止血机制，直至以上三项条件全部满足并由维护者显式批准重开为止。任何重开操作必须先提交或更新对应 Spec / ADR，并通过本仓库的 Pre-review Gate 和 Integration Gate。
