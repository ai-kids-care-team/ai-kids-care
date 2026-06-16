---
id: SPEC-0002
title: "Admin 管理与审批端点（角色/用户/membership 审批与状态变更）"
status: Approved
owner: 维护者
created: 2026-06-16
updated: 2026-06-16
related_adrs:
  - ADR-0003
  - ADR-0009
  - ADR-0016
  - ADR-0019
  - ADR-0021
---

# SPEC-0002: Admin 管理与审批端点

> 本 spec 落地 [SPEC-0001](SPEC-0001-auth-authorization-tenant-sensitive-data-boundaries.md) 已规定但延后的**审批机制**（SPEC-0001 §"具体审批 UI 和邀请机制可由后续 Spec 定义"）。它定义园级与平台级管理员**审批/拒绝/撤销/状态变更**的后端端点、授权、事务与会话吊销，遵循 [ADR-0019](../decisions/adr/ADR-0019-effective-authorization-context-tenant-enforcement.md) §2/§4/§6/§7，并依赖 [ADR-0021](../decisions/adr/ADR-0021-admin-audit-schema-migration.md) 已落地的 `REJECTED` 状态与单-ACTIVE 完整性约束。

## 目标结果（Outcome）

同园 ACTIVE 院长/副院长（`KINDERGARTEN_ADMIN` 且 `teachers.level ∈ {DIRECTOR, VICE_DIRECTOR}`）可审批、拒绝本园 PENDING 的 Guardian/Teacher/后续管理员申请，并撤销/停用本园在职成员；ACTIVE `PLATFORM_IT_ADMIN` 可审批/拒绝 PENDING SUPERADMIN 申请并停用平台账户。所有状态推进在单一事务内完成 user + 业务档案 + membership + role assignment 的一致激活/停用，授权与写入消除 TOCTOU，停用/撤销类操作在事务提交后主动吊销目标用户的全部会话；任何越权、跨园、自审或对非 PENDING/非 ACTIVE 目标的操作被拒绝。

## 当前事实（Current Facts）

- 注册已在单一事务创建完整 PENDING 实体图（user/role assignment/业务档案/membership），但**不存在任何把 PENDING 推进到 ACTIVE/REJECTED 的写链或端点**（`AuthService.register`，`AuthService.java:45-91`）。
- 与审批相关的 Controller（`UserController`/`SuperadminController`/`TeacherController`/`GuardianController`）当前为**空体**（Phase 1A 关闭公共 operation）；其 service 仅 list/get 裸查询、无写、无 `@PreAuthorize`；相关 repository 缺少按 kindergarten+status 的 tenant-aware 查询（`Guardian`/`Superadmin` 连 `findByUserId` 都没有）。审批端点为**全新增**，非"重开"。
- `StatusEnum` 现含 `ACTIVE/PENDING/DISABLED/REJECTED`（ADR-0021，V2 已合入 develop）；`user_role_assignments`/`user_kindergarten_memberships` 现有"单 ACTIVE/用户"部分唯一索引 + scope CHECK（ADR-0021）。
- `SessionRevocationService.revokeAllForUser(Long userId)` 已就绪（`SessionRevocationService.java:30`），当前仅 `/auth/logout-all` 调用；其 Javadoc 预告状态变更操作落地后须在事务提交后调用。
- 集中授权：`@EnableMethodSecurity` + `@PreAuthorize("@authorizationPolicy.isAllowed(...)")` 读取每请求 `EffectiveAuthorizationContext`；`AuthorizationAction` 现有 7 个值，**无任何 admin/审批 action**。ADR-0019 §7 的 `KindergartenAdminPolicy`/`PlatformPolicy` 尚未实现（仅 `TeacherAssignmentPolicy` 存在）。
- `EffectiveAuthorizationContext` 不含 `teachers.level`（`EffectiveAuthorizationContext.java:7-16`）；审批授权需在 policy 内按 `context.userId()` 反查 `Teacher.level`。
- `child_guardian_relationships` 无 status 列（ADR-0019 §7）；Guardian 关系有效性由日期窗口 + 两端（guardian/membership）status 共同定义。

## 范围（Scope）

### In Scope

- 园级审批端点：列出本园 PENDING 申请、批准、拒绝。
- 园级成员状态变更：停用本园在职 user / 撤销 role / 结束 membership。
- 平台级端点：列出 PENDING SUPERADMIN 申请、批准、拒绝、停用平台账户。
- 新增 `KindergartenAdminPolicy`、`PlatformPolicy`（ADR-0019 §7 扩展点）与对应 `AuthorizationAction`。
- 审批/拒绝/撤销的事务边界、TOCTOU 防护、状态机校验。
- 停用/撤销类操作在事务提交后接 `SessionRevocationService`。
- 补齐审批所需的 tenant-aware repository 查询。
- 负向/权限/租户/自审/状态前置的集成测试。

### Out of Scope

- 受控账号发放（新园首位院长、`PLATFORM_IT_ADMIN` bootstrap）——SPEC-0001 §匿名注册 12/决策 7；留独立 spec/受控流程。
- 开放 Guardian/Children 资源读取与 `GuardianChildPolicy`——属候选 #3，本 spec 仅审批 Guardian 申请、不开放 guardian 业务资源。
- 审批/拒绝/撤销的**审计写入**——依赖候选 #1（审计 writer + correlation-id filter）；本 spec 在端点内**预留** hook 点，writer 落地后接入。
- 审批/拒绝的**通知**（家长/教师邮件、Pushover）——属 ADR-0018，后续。
- 前端审批 UI——后续；本 spec 只定义后端契约。
- 密码重置/验证码/邀请邮件——SPEC-0001 §Out of Scope。

## 角色与权限（Actors And Permissions）

| Actor | 允许行为 | Tenant / 数据边界 |
| --- | --- | --- |
| `KINDERGARTEN_ADMIN`（level `DIRECTOR`/`VICE_DIRECTOR`） | 列出/批准/拒绝本园 PENDING Guardian/Teacher/后续院长·副院长申请；停用本园在职成员、撤销其园级 role、结束 membership | 仅 `activeKindergartenId` 所属园；不得审批自己；不得操作他园；不得创建/审批平台角色；不读 S0 |
| `PLATFORM_IT_ADMIN` | 列出/批准/拒绝 PENDING `SUPERADMIN` 申请；停用平台账户、撤销平台 role | 平台 scope；不得审批自己；不得借此读取园级 S1/CCTV/录像/检测 evidence（数据分类不变） |
| 其它角色（`TEACHER`/`GUARDIAN`/普通成员/匿名） | 无 | 任何本 spec 端点返回 `403`（已认证越权）或 `401`（未认证） |

## 业务流程（Business Flow）

### 1. 园级审批（approve）

1. 触发：园级管理员 `POST /api/v1/admin/kindergarten/registrations/{userId}/approve`。
2. 授权（`KindergartenAdminPolicy`，事务内）：调用者为 ACTIVE `KINDERGARTEN_ADMIN`、`teachers.level ∈ {DIRECTOR, VICE_DIRECTOR}`、其 `activeKindergartenId` == 目标申请所属园；`context.userId() != targetUserId`（禁自审）。
3. 状态校验与转换（单一 `@Transactional`）：目标 user/业务档案/membership/role assignment 必须均为 `PENDING` 且属同园；以条件更新 `UPDATE ... WHERE status='PENDING'`（或 `SELECT ... FOR UPDATE` 行锁）原子推进为 `ACTIVE`，防并发重复批准（TOCTOU）。激活后单-ACTIVE 约束（ADR-0021）保证不产生第二条 ACTIVE。
4. 副作用：预留审计 hook（actor/目标/园/result）；批准通常无既存 session 需吊销（PENDING 用户无 session）。
5. 失败：目标非 PENDING/跨园/不存在 → 隐藏式 `404`；调用者越权/level 不符/自审 → `403`；并发已被他人批准 → 条件更新影响 0 行 → `409` 或幂等 `404`（见 Open Questions）。

### 2. 园级拒绝（reject）

同授权与租户校验；状态 `PENDING → REJECTED`（ADR-0021 新值）。无 session 吊销（无 session）。

### 3. 园级停用/撤销（disable / revoke / end-membership）

1. 触发：`POST /api/v1/admin/kindergarten/members/{userId}/disable`（及/或细分的 revoke-role / end-membership，见 Open Questions）。
2. 授权同上 + 禁自审 + 同园。
3. 事务内将目标 user/membership/role assignment 由 `ACTIVE → DISABLED`（填 `revoked_at`/`revoked_by_user_id`/`left_at`），行锁/条件更新防 TOCTOU。
4. **提交后**调用 `SessionRevocationService.revokeAllForUser(targetUserId)`（ADR-0019 §6 快路径；每请求权威重解析为兜底）。
5. 预留审计 hook。

### 4. 平台级审批/拒绝/停用

1. `GET /api/v1/admin/platform/superadmin-registrations?status=PENDING`、`POST .../{userId}/approve|reject`、`POST /api/v1/admin/platform/users/{userId}/disable`。
2. 授权（`PlatformPolicy`）：调用者 ACTIVE `PLATFORM_IT_ADMIN`、禁自审、目标 role 为 `SUPERADMIN`（审批）。
3. SUPERADMIN 批准：单一事务激活 user + superadmin 档案 + `PLATFORM` role assignment（`scope_id` 必须 NULL，符合 ADR-0021 scope CHECK），无 membership。
4. 停用平台账户：`ACTIVE → DISABLED` + 提交后 `revokeAllForUser`。
5. 平台角色选择 tenant context 不因此扩权（数据分类矩阵不变，SPEC-0001 §角色与权限）。

## 契约（Contracts）

> 端点以 `userId` 为目标主键（见 Open Questions OQ-2）。所有写端点要求有效 session + CSRF；响应仅返回最小结果，不含 S0/S1。OpenAPI 契约以实现期生成的 `/v3/api-docs` 为准。

| Method / Path | 角色 | 行为 |
| --- | --- | --- |
| `GET /api/v1/admin/kindergarten/registrations?status=PENDING` | 园级 admin | 列出本园 PENDING 申请（最小字段：userId、申请角色、level、提交时间；不回显 RRN/联系方式等 S1） |
| `POST /api/v1/admin/kindergarten/registrations/{userId}/approve` | 园级 admin | PENDING→ACTIVE（user+档案+membership+role），返回 `204`/最小结果 |
| `POST /api/v1/admin/kindergarten/registrations/{userId}/reject` | 园级 admin | PENDING→REJECTED |
| `POST /api/v1/admin/kindergarten/members/{userId}/disable` | 园级 admin | ACTIVE→DISABLED + 吊销会话 |
| `GET /api/v1/admin/platform/superadmin-registrations?status=PENDING` | `PLATFORM_IT_ADMIN` | 列出 PENDING SUPERADMIN 申请（最小字段） |
| `POST /api/v1/admin/platform/superadmin-registrations/{userId}/approve` | `PLATFORM_IT_ADMIN` | PENDING→ACTIVE（user+superadmin+PLATFORM role） |
| `POST /api/v1/admin/platform/superadmin-registrations/{userId}/reject` | `PLATFORM_IT_ADMIN` | PENDING→REJECTED |
| `POST /api/v1/admin/platform/users/{userId}/disable` | `PLATFORM_IT_ADMIN` | ACTIVE→DISABLED + 吊销会话 |

新增授权 action（`AuthorizationAction`）：`KINDERGARTEN_ADMIN_APPROVAL_READ`、`KINDERGARTEN_ADMIN_APPROVAL_WRITE`、`KINDERGARTEN_ADMIN_MEMBER_WRITE`、`PLATFORM_SUPERADMIN_APPROVAL_READ`、`PLATFORM_SUPERADMIN_APPROVAL_WRITE`、`PLATFORM_USER_WRITE`。`@PreAuthorize` 只做粗粒度 role/capability 许可；同园 + level + 禁自审 + 目标状态等**资源关系判断在 `KindergartenAdminPolicy`/`PlatformPolicy` 内、事务中完成**（ADR-0019 §2）。

## 不变量（Invariants）

- 审批/拒绝/撤销的授权检查、资源加载与状态写入处于**同一事务**，并以行锁/条件更新消除并发撤销/重复批准 TOCTOU（ADR-0019 §4、SPEC-0001 §299）。
- 任何人不得审批自己的申请；园级管理员只能操作本园（`activeKindergartenId`）；平台审批仅限 `PLATFORM_IT_ADMIN` 对 `SUPERADMIN`。
- 批准激活后，目标账号仍满足"单账号单 ACTIVE role、园级单 ACTIVE membership、平台无 membership"（ADR-0021 约束兜底）。
- 跨园/不存在/非 PENDING（审批）或非 ACTIVE（停用）目标使用隐藏式 `404`，不暴露存在性；越权角色 `403`。
- 停用/撤销/角色替换在状态事务提交后吊销目标用户全部 session；最迟下一请求因 DB 状态失效返回 `401`（ADR-0019 §6）。
- 端点不返回 S0；列表/详情不回显 RRN、联系方式等 S1（最小字段）。
- 通用 CRUD 不得借这些端点重新打开 Phase 1A 已关闭的人员/账户写链或敏感字段（SPEC-0001 §38）。

## 验收标准（Acceptance Criteria）

- [ ] 同园 ACTIVE、level `DIRECTOR`/`VICE_DIRECTOR` 的 `KINDERGARTEN_ADMIN` 可批准本园 PENDING 申请；被批准账号随后可正常登录并解析出唯一 ACTIVE role/membership。
- [ ] 普通 `TEACHER`、非 director level、他园 admin、未认证者访问审批端点分别返回 `403`/`403`/隐藏 `404`/`401`。
- [ ] 自审（`context.userId()==targetUserId`）被拒绝。
- [ ] 拒绝使目标转为 `REJECTED`，该账号仍不能登录。
- [ ] 停用/撤销使目标转为 `DISABLED`，且**提交后其全部 session 被吊销**，旧 session 下一请求返回 `401`。
- [ ] 并发重复批准/撤销不产生第二条 ACTIVE 或不一致状态（条件更新/行锁验证）。
- [ ] `PLATFORM_IT_ADMIN` 可批准/拒绝/停用 SUPERADMIN；非平台角色访问平台端点 `403`；平台审批不授予园级或 S1 访问。
- [ ] 跨园目标（园级）与非 SUPERADMIN 目标（平台审批）返回隐藏 `404`。
- [ ] 端点响应与 OpenAPI schema 不含 S0；列表不回显 S1。
- [ ] 对应 SPEC-0001 验收项（注册与审批边界）由本 spec 端点满足。

## 验证（Verification）

| 检查 | Command / Test | 预期结果 |
| --- | --- | --- |
| 后端测试 | `cd backend; .\gradlew.bat test`（本机无 Java，经 GitHub Actions 验证） | 审批/拒绝/撤销 + 授权/租户/自审/状态前置/会话吊销负向测试全过 |
| 审批授权矩阵 | `AdminApprovalAuthorizationIntegrationTest`（新增） | allow/deny/hidden-404 与本 spec 一致 |
| 会话吊销 | 扩展 `SessionRevocationIntegrationTest` | 停用/撤销后目标 session 失效 |
| TOCTOU | 并发/条件更新测试 | 重复批准/撤销不破坏单-ACTIVE 不变量 |
| 敏感字段 | OpenAPI 契约扫描 | admin 端点 schema 无 S0、列表无 S1 |
| 最终 diff | `git diff --check` | 无 whitespace error |

## 开放问题（Open Questions）

> **维护者裁定（2026-06-16，spec Approved）**：OQ-1=**否**（发放留独立 spec）；OQ-2=**userId**；OQ-3=**单一 `disable`**；OQ-4=**已被他人处理（条件更新 0 行）→ 幂等成功/隐藏 `404`，不返回 `409`**；OQ-5=**并行**（端点先留审计 hook 点，#1 writer 落地后接入）。以下保留原始记述。

- **OQ-1（范围）**：受控账号发放（新园首位院长、`PLATFORM_IT_ADMIN` bootstrap，SPEC-0001 §匿名注册 12/决策 7）是否纳入本 spec？**建议：否**，留独立受控流程 spec。
- **OQ-2（端点建模）**：审批以 `userId` 为主键，还是引入聚合"申请单"资源？**建议：`userId`**（最小、与现有实体直接对应）。
- **OQ-3（停用粒度）**：园级"停用"用单一 `disable`（同时停 user+role+membership），还是细分 `revoke-role` / `end-membership` / `disable-user`？**建议：先单一 `disable`**（覆盖主用例），细分留后续。
- **OQ-4（幂等/并发响应）**：条件更新影响 0 行（已被他人处理）时返回 `409` 还是幂等成功/隐藏 `404`？需维护者定 HTTP 语义。
- **OQ-5（审计耦合）**：本 spec 预留审计 hook，但 writer 属 #1。是否要求 #1 先于本 spec 的实现合入，还是并行（hook 先留空、writer 落地后接）？**建议：并行**，端点先预留调用点。

## 实施记录（Implementation Notes）

仅在本 spec `Approved` 后填写：对应提交与改动文件、新增/更新测试、数据/兼容策略、验证命令与结果、已知风险与后续。

### 2026-06-16 切片 A：园级审批/拒绝/停用端点（已合入 develop、CI 验证）

- 提交：`703e8b7`（实现）+ `d539f8d`（Lead 评审修复）。在 develop 上 `Backend Java Tests` + `Compose Config` + `Frontend Lint & Build` 全绿。
- 实现：`AdminKindergartenController`（`/api/v1/admin/kindergarten/**`，新建独立控制器、未触碰关闭的人员控制器）、`KindergartenAdminApprovalService`、`KindergartenAdminPolicy`（level DIRECTOR/VICE + 禁自审，事务内细判）、`PendingRegistrationVO`（最小字段无 S1）；新增 3 个园级 `AuthorizationAction` + `AuthorizationPolicy` 接线；补 tenant-aware repository 查询与条件更新（防 TOCTOU，`WHERE status=... AND scope_id=kg`）。四端点：list/approve/reject/disable，disable 在事务提交后 `SessionRevocationService.revokeAllForUser`；条件更新 0 行 → 隐藏 `404`（OQ-4）；审计 hook 以 `TODO(SPEC-0002 #1)` 预留。
- 测试：`AdminApprovalAuthorizationIntegrationTest`（25 项）——allow/deny/403/隐藏404/自审/跨园/状态前置/重复幂等/会话吊销/无 S1。
- Lead 评审修复（`d539f8d`）：(1) `reject()` 原先只查 User 行数、未查 kg-scoped membership/role → 跨园拒绝漏洞，补行数检查（0 行→回滚→隐藏 404）；(2) URA 条件更新的关联赋值由 JPQL 子查询改实体引用参数 `:actor`（Hibernate bulk update 稳定性）。
- 未做（切片 B）：平台级 SUPERADMIN approve/reject + 平台 user disable + `PlatformPolicy` + 平台 `AuthorizationAction`。审计 writer/通知/前端 UI 见 SPEC Out of Scope 与候选 #1。

### 2026-06-16 切片 B：平台级 SUPERADMIN 审批/拒绝/停用端点（已合入 develop、CI 验证）

- 提交：`7c0c545`（实现，Lead 评审未发现需修问题——子 agent 已落实切片 A 的两条教训）。在 develop 上三 workflow 全绿。
- 实现：`AdminPlatformController`（`/api/v1/admin/platform/**`）、`PlatformAdminApprovalService`、`PlatformPolicy`（仅禁自审）、3 个平台 `AuthorizationAction` + `AuthorizationPolicy` 接线（`scopeType==PLATFORM && role==PLATFORM_IT_ADMIN`，非 tenantIdentity）；`SuperadminRepository` 补 `findByUser_Id`+条件更新；`UserRoleAssignmentRepository` 补平台专用查询/更新。
- 平台特有：role assignment `scope_id` 为 NULL → 条件更新用 `scopeId IS NULL`（非 `= :scopeId`）+ `role=SUPERADMIN` 门；平台账户无 membership；`:actor` 实体引用赋值（非 JPQL 子查询）；行数门以 role assignment 条件更新为先，0 行→回滚→隐藏 404。disable 在事务提交后 `revokeAllForUser`。
- 保守取舍：`disable` 限 `role=SUPERADMIN`（不停用其它 `PLATFORM_IT_ADMIN`——安全不越权；SPEC「平台账户」如需放宽留后续）。
- 测试：`PlatformAdminApprovalAuthorizationIntegrationTest`（21 项）——同切片 A 的 allow/deny/403/隐藏404/自审/非SUPERADMIN目标/状态前置/重复幂等/会话吊销/无 S1。
- **SPEC-0002 两切片（园级+平台级）至此全部实现并经 develop CI 验证**；正式 release-gate fresh independent review 在下次 `develop→main` 发布进行（ADR-0020）。审计写入接入属候选 #1（端点已留 `TODO(SPEC-0002 #1)` hook）。
