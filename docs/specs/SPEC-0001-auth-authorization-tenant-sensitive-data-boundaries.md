---
id: SPEC-0001
title: "认证、授权、租户与敏感数据边界"
status: Approved
implementation: Partial
owner: 维护者
created: 2026-06-10
updated: 2026-06-14
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

- `backend/.../config/SecurityConfig.java` 将 `/api/v1/**` 配置为 `permitAll()`，JWT filter 未加入过滤链，当前全部业务 API 可匿名访问。
- [ADR-0016](../decisions/adr/ADR-0016-server-side-session-auth.md) 已决定使用 Spring Session + Redis + `httpOnly` cookie 取代 JWT，但尚未实现。
- `user_role_assignments` 已表达 `PLATFORM` / `KINDERGARTEN` scope；`user_kindergarten_memberships` 已表达用户与幼儿园的成员关系，但业务查询没有统一使用这些数据做运行时隔离。
- 多个 Controller、DTO 和前端调用仍接受客户端提供的 `kindergartenId`，该值尚未统一与服务端授权上下文绑定。Phase 1A 补充止血已移除前端从 JWT、localStorage 或 demo user ID 推断园区的逻辑，登录/刷新改为从 ACTIVE role assignment 返回服务端派生的园区 ID。
- Phase 1B 后，`POST /api/v1/auth/register` 对 `PLATFORM_IT_ADMIN` 在首次 persistence 前返回 `400`；`GUARDIAN`、`TEACHER`、`KINDERGARTEN_ADMIN`、`SUPERADMIN` 只创建 PENDING user、role assignment、业务档案和园级 membership。
- `AuthRegisterDTO` 不再发布客户端 `status` 字段；未知 `status=ACTIVE` 输入会被忽略。PENDING user、没有 ACTIVE role assignment 或存在多条 ACTIVE role assignment 的 user 登录/刷新返回通用 `401`，不再默认回退 `GUARDIAN` 或取最近一条。
- `DIRECTOR`（院长）和 `VICE_DIRECTOR`（副院长）属于 `LevelEnum`，不是独立 `UserRoleEnum`。Phase 1B 前后端都将这两个 level 约束为 `KINDERGARTEN_ADMIN` 申请；普通 `TEACHER` 不能通过伪造院长级别提交申请。
- Phase 1A 已从 `UserVO` 移除 `passwordHash`、email、phone，从 `ChildVO` 移除 `rrnEncrypted`、`rrnFirst6`、birth date、address，从 `GuardianVO` 移除 `rrnEncrypted`、`rrnFirst6`、address，从 `TeacherVO` 移除 `rrnEncrypted`、`rrnFirst6`、staff number 和 emergency contact；因人员/账户资料仍可被匿名枚举，四类 Controller 的公共读写 operation 现已全部关闭。
- `DeviceTokenVO` 与 `EventEvidenceFileVO` 已分别移除完整 `pushToken` 和内部 `storageUri`；因设备注册与证据元数据仍属受限数据，两类 Controller 的公共读写 operation 现已全部关闭。`CameraStream` 的 entity 仍内部存储 `sourceUrl`、`streamUser`、`playbackUrl` 和加密凭据列，但公共 `CameraStreamVO` 不返回可播放地址或凭据，且通用写删链已关闭。
- Kindergarten 公共 list/detail 和注册查找只返回最小目录字段；通用 `POST` / `PUT` / `DELETE` 与敏感 write DTO 已关闭。Graph、Audit Log、Notification controller 当前不发布公共 operation，等待资源授权或内部 command 架构后再开放。
- 后端未见 `@PreAuthorize`、统一 Authorization Context 或集中式 tenant enforcement；多处 tenant-scoped entity 使用不带租户条件的 `findById`。
- 数据库复合外键可阻止跨园关联写错，但不能阻止应用读取或修改另一幼儿园的合法记录。
- `user_role_assignments` 允许 `PLATFORM` scope 使用 `scope_id=NULL`；当前普通 unique index 不足以阻止相同平台角色因 NULL 语义被重复授予，也未见约束强制 PLATFORM/KINDERGARTEN 与 `scope_id` 的合法组合。
- `audit_logs` 表仍存在，但公共 controller 当前不发布读写 operation；未见安全敏感操作的统一内部审计写入。其 `kindergarten_id` 为 `NOT NULL`，无法自然表达不属于某个幼儿园的平台级安全事件。
- 生产 TLS 已由 [ADR-0017](../decisions/adr/ADR-0017-tls-https-termination.md) 规定，但仓库当前仍以 HTTP 运行。

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

### 2026-06-15 Phase 2/3（会话认证 + 有效授权上下文与默认拒绝）— 本地验证通过，未合入

- 状态：实现于分支 `codex/spec-0001-auth-context`（基线 `478967d`），**未提交、未合入 `develop`**。遵循 [ADR-0019](../decisions/adr/ADR-0019-effective-authorization-context-tenant-enforcement.md) 的分阶段边界（阶段 2、3 完成，阶段 4 部分完成）。本节为实施证据/as-built 记录；下方“验收标准”勾选保留给维护者评审，本阶段未自行翻转。
- 会话认证（阶段 2）：引入 Redis 后端 Spring Session（`store-type=redis`、`repository-type=indexed`）、最小可序列化 `SessionPrincipal`（`user:{id}` + roleAssignmentId + membershipId）。`/auth/login` 建立服务端会话、只返回最小 `AuthSessionVO`（`@JsonInclude(NON_NULL)`，无 access/refresh token）；新增 `GET /auth/session`、`POST /auth/logout`（幂等 204）、`GET /auth/csrf`。启用 cookie CSRF（`XSRF-TOKEN` + `X-XSRF-TOKEN`）、`IF_REQUIRED` 会话、显式 SecurityContext 持久化、会话 cookie `AI_KIDS_CARE_SESSION`（httpOnly、`secure` 由 `SESSION_COOKIE_SECURE` 控制、`SameSite=Lax`）。删除 `JwtUtil`/`JwtAuthenticationFilter`/`TokenVO`/`TokenTypeEnum`/refresh、logout DTO 与 JJWT 依赖；前端去 bearer/localStorage、改 `withCredentials` + CSRF bootstrap + 会话 hydration（新增 `SessionBootstrap.tsx`、`csrf.ts`）。
- 有效授权上下文与默认拒绝（阶段 3）：新增 `EffectiveAuthorizationContextFilter`（接在 `SecurityContextHolderFilter` 之后）每请求调用 `EffectiveAuthorizationContextService.resolve` 做权威投影——校验 ACTIVE user、恰一条 ACTIVE role assignment 且与 principal 一致、KINDERGARTEN scope 的唯一 ACTIVE membership、PLATFORM scope 无 membership；非法/漂移 invalidate 会话并返回通用 `401`。`@EnableMethodSecurity` + 集中 `AuthorizationPolicy`（`@PreAuthorize` 读取 request-scoped context，不读会话内陈旧 authority）。`SecurityConfig` 默认 `/api/v1/**` `authenticated()`。`POST /auth/session/tenant-context` 仅 PLATFORM 角色可选已验证 tenant，写入会话且不改 scope/role、不授予园级权限；园级角色调用 `403`。`ApiExceptionHandler` 将 `EntityNotFoundException` 统一为隐藏式 `404 {"error":"Resource not found"}`。
- Tenant Repository Migration（阶段 4，部分）：Class/Room/CCTV/CameraStream/DetectionSession/Announcement 的部分 list/get/write 改为 tenant-aware 查询（`findByIdAndKindergarten_Id` 等）+ effective kindergarten 约束 + 客户端 `kindergartenId` 仅一致性校验、不一致按隐藏 `404`。**未完成**：DetectionEvent 仍信任客户端 `kindergartenId` 且未接集中 policy；全部发布 operation 的分类未完成。
- 测试：`AuthEndpointTest` 扩展至 29 项，覆盖会话登录/无 token 401/园级有无 membership、平台角色有 membership 401、错误密码/无 ACTIVE role/多 ACTIVE role 的通用 401、CSRF 契约、refresh 关闭 404、logout 失效、role 撤销下一请求 401 并失效会话、平台 tenant-context 选择不改角色、园级角色禁选平台 tenant-context（403）、平台选 tenant 不获 camera 读（403）、园级角色禁读平台 AI 元数据（403）、跨租户 Class 隐藏（list 不含外租户 + detail 404）、写入拒绝客户端 kindergarten 覆盖（404）、camera list 拒绝 kindergarten 覆盖（404）。
- 验证（2026-06-15，本地）：`cd backend; .\gradlew.bat test` → 8 类 / 61 tests / 0 failures / 0 errors / 2 预期 skip（`FlywayMigrationTest`，ADR-0013）。前端 21 改动文件 scoped ESLint 0 error / 0 warning；`npm run build` 成功生成 20 静态页。`git diff --check` PASS。全仓前端 lint 仍有既有 4 errors / 8 warnings（均在本阶段未修改文件）。
- 环境备注：本机 JDK 21 wepoll selector 的 AF_UNIX wakeup pipe 因用户 Temp 路径含空格/撇号失败，须以 `JAVA_TOOL_OPTIONS=-Djdk.net.unixdomain.tmpdir=D:\tmp` 运行测试；与产品代码无关。
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
