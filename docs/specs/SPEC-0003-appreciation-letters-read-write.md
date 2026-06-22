---
id: SPEC-0003
title: "感谢信（Appreciation Letters）读写端点"
status: Approved
implementation: Implemented (2026-06-20)
owner: "Lead/Planner"
created: 2026-06-19
updated: 2026-06-20
related_adrs: [ADR-0003, ADR-0009, ADR-0019, ADR-0028]
---

# SPEC-0003: 感谢信（Appreciation Letters）读写端点

> 对应 backlog 任务 **BE-5**（`docs/assessments/2026-06-18-followup-backlog.md`）。FE-2 依赖本 spec 落地后的契约。

## 目标结果（Outcome）

已认证的 **家长（GUARDIAN）** 可在本人所属 kindergarten 内向 **老师** 或 **园所** 写感谢信；同租户成员按可见性规则读取；作者可编辑/删除本人的信。**sender 身份与 tenant 一律由服务端从会话派生，客户端永不提交身份字段**（这正是 FE-2 的前提）。

## 当前事实（Current Facts）

> **（2026-06-20 FE-2 已完成，以下为 spec 起草时基线，描述实现前的状态）**

- 后端骨架已存在：`entity/AppreciationLetter`、`repository/AppreciationLetterRepository`、`mapper/AppreciationLetterMapper`、`vo/AppreciationLetterVO`、`type/AppreciationTargetTypeEnum{KINDERGARTEN,TEACHER}`、表 `appreciation_letters`（`db/initdb/01_create_schema.sql` + seed `46_appreciation_letters_seed.sql` + `V1__initial_baseline.sql`）。
- `service/AppreciationLetterService` 现仅有 `listAppreciationLetters` / `getAppreciationLetter`，且**两者都标注 `@PreAuthorize("denyAll()")`**（完全关闭）。**无 Controller，无 DTO，无写方法。**
- 表列（NOT NULL）：`letter_id`(PK)、`kindergarten_id`(FK 租户)、`sender_user_id`(FK)、`target_type`(enum)、`target_id`、`title`、`content`、`is_public`、`status`(status_enum)、`created_at`、`updated_at`。
- **三个守卫契约测试主动禁止本 spec 的目标状态**（此前写实现被有意移除并上锁）：
  - `SensitiveWriteContractTest`：`AppreciationLetterCreateDTO/UpdateDTO` 类缺席（L84-85）、`AppreciationLetterService.create/update/deleteAppreciationLetter` 方法缺席（L246-248）、`AppreciationLetterMapper.toEntity/updateEntity` 缺席（L276-277）。
  - `PublishedOpenApiContractTest`：OpenAPI 组件 `AppreciationLetterCreateDTO/UpdateDTO` 缺席（L269-270）、路径 `/api/v1/appreciation_letters` 与 `/{id}` 缺席（L377-378）。
  - 撤销这些守卫 = 撤销一项已生效的安全加固 → 由 **ADR-0028** 正式 supersede。
- 现 `AppreciationLetterVO` 暴露原始内部 ID：`kindergartenId`、`senderUserId`、`targetId`（见 §契约的 S0/S1 处理）。
- 前端 `appreciationLetters.api.ts` 全部端点现抛 `appreciationLettersUnavailable()`；写 payload 现含 `senderUserId`/`kindergartenId`/`status`（FE-2 将按本 spec 契约移除）。
- 既有授权范式（参照 `ChildrenController` / ADR-0019）：Controller 极薄；`@PreAuthorize` 落在 Service 层；`EffectiveAuthorizationContext` 服务端派生调用者 tenant/role/identity；细粒度作用域由 Repository SQL 强制；越权统一返回**隐藏 404 + 审计**；返回最小 VO。

## 范围（Scope）

### In Scope

- 新建 `AppreciationLetterController`，发布于 `/api/v1/appreciation_letters`：`GET`（列表，分页+keyword）、`GET /{id}`、`POST`（create）、`PUT /{id}`（update）、`DELETE /{id}`。
- Service 新增 `createAppreciationLetter` / `updateAppreciationLetter` / `deleteAppreciationLetter`，并解除 `list`/`get` 的 `denyAll()` 改为按 §角色 的 `@PreAuthorize`；全部经 `EffectiveAuthorizationContext` 派生 sender + tenant。
- 新增 DTO：`AppreciationLetterCreateDTO`、`AppreciationLetterUpdateDTO`——**不含** `senderUserId`、`kindergartenId`、`status`（这些服务端派生/管理）。
- `AppreciationLetterMapper` 新增 `toEntity`/`updateEntity`（仅映射客户端可控字段）。
- 翻转上述三个守卫契约测试为"存在/正向契约"，并补**正向**授权与契约测试（含跨租户拒绝、隐藏 404、客户端伪造 sender/tenant 被忽略）。
- 响应 VO 的 S0/S1 收敛（见 §契约）。

### Out of Scope

- **FE-2**（前端移除身份字段）——独立任务，依赖本 spec 契约，随后进行。
- Neo4j 投影；信创建的通知/推送（列为 OQ-4）。
- 园所管理员审核删除（列为 OQ-2）。

## 角色与权限（Actors And Permissions）

模型："**家长写 · 租户内可见**"。所有判定基于 `EffectiveAuthorizationContext`，跨租户一律隐藏 404。

| Actor | Create | Read | Update | Delete |
| --- | --- | --- | --- | --- |
| `GUARDIAN` | ✓ sender=本人，本人所属 kindergarten 内，`target_type∈{TEACHER,KINDERGARTEN}` 且 target 同租户 | 本人所写 + 同租户 `is_public=true` 的信 | 仅本人所写 | 仅本人所写（软删除，见 OQ-3） |
| `TEACHER` | ✗ | 同租户 `is_public=true` + 发给本人（`target_type=TEACHER ∧ target_id=本人`）的信 | ✗ | ✗ |
| `KINDERGARTEN_ADMIN` | ✗ | 本租户全部 | ✗ | 见 OQ-2（审核删除，默认本期 ✗） |
| `SUPERADMIN` / `PLATFORM_IT_ADMIN` | ✗ | ✗（非业务可见，平台运维不读业务内容） | ✗ | ✗ |

## 业务流程（Business Flow）

1. **Create**：GUARDIAN `POST` 提交 `{targetType, targetId, title, content, isPublic}`。
2. 服务端派生 `senderUserId=当前用户`、`kindergartenId=当前用户租户`；校验 `targetId` 在同租户内存在且 `targetType` 合法；`status` 由服务端置初始值。
3. 持久化 → 返回最小 VO（不回显原始 sender/tenant 内部 ID，见 §契约）。
4. **Read**：按 §角色 的可见性过滤（Repository SQL 强制）；命中不可见 → 隐藏 404 + 审计。
5. **Update**：仅作者，仅可改 `{title, content, isPublic}`；`sender/tenant/target/status` 不可经客户端改。
6. **Delete**：仅作者；软删除（OQ-3）。
7. **失败**：未认证 401；越权/跨租户/不可见 → 隐藏 404 + 审计；校验失败 400。

## 契约（Contracts）

- 路径：`/api/v1/appreciation_letters`（snake_case，与既有守卫测试一致）。
- `AppreciationLetterCreateDTO`：`targetType`(enum 字符串)、`targetId`(Long)、`title`(NotBlank)、`content`(NotBlank)、`isPublic`(Boolean)。**无身份字段**。
- `AppreciationLetterUpdateDTO`：`title`、`content`、`isPublic`。
- **响应 VO 的 S0/S1 收敛**（默认建议，待 OQ-1 确认）：以 BE-4 的实名解析替代原始 `senderUserId` → 暴露 `senderName`；以 target 显示名替代裸 `targetId`（或同时保留供前端跳转，按 SPEC-0001 §敏感分级核定）；移除响应中的 `kindergartenId`（租户对调用者隐含）。`status` 是否出现在公开响应按 OQ-3 定。
- **`editable` 归属信号（2026-06-20 FE-2 决策追加）**：VO 增加服务端派生的 `editable: Boolean`（= 当前调用者是否为本信作者）。FE 据此决定是否显示编辑/删除入口，**而不在客户端比对 `senderUserId`**（符合 SPEC-0001：UI 不从客户端数据推断身份）。非作者（含园所管理员、被致谢老师、同租户其他家长）一律 `editable=false`。该字段非敏感、加入 `PublishedOpenApiContractTest` 的 VO 字段锁集。
- 守卫契约测试翻转：`SensitiveWriteContractTest`/`PublishedOpenApiContractTest` 中针对 appreciation letters 的 5 处 `assert*Absent` 改为存在性/正向断言。

## 不变量（Invariants）

- 客户端提交的任何 `senderUserId`/`kindergartenId`/`status` 一律被忽略，以服务端派生值为准（即便字段被伪造也不得越权）。
- 写操作的 `sender` 恒等于当前会话用户；`tenant` 恒等于当前会话租户。
- 跨租户对象在读/写/详情中**不可区分于不存在**（隐藏 404）。
- target 必须与 sender 同租户且类型匹配，否则 400/404。

## 验收标准（Acceptance Criteria）

- [ ] GUARDIAN 可 create/读自己/编辑自己/删除自己；sender 与 tenant 由服务端派生。
- [ ] 客户端伪造 `senderUserId`/`kindergartenId`/`status` 被忽略（有测试证明）。
- [ ] 跨租户读/改/删 → 隐藏 404 + 审计（有测试）。
- [ ] TEACHER 仅见 public + 发给本人的；KINDERGARTEN_ADMIN 见本租户全部；SUPERADMIN/IT 不可见（有测试）。
- [ ] 未认证 401；非法 target 400/404。
- [ ] 三个守卫契约测试翻转后全绿，且新增正向契约/授权测试全绿。
- [ ] 响应 VO 不泄露超出 SPEC-0001 允许的 S0/S1 原始标识（按 OQ-1 结论）。
- [ ] OpenAPI 发布 `/api/v1/appreciation_letters` + `/{id}`，组件含两个 DTO。

## 验证（Verification）

| 检查 | Command / Test | 预期结果 |
| --- | --- | --- |
| 契约守卫翻转 | `bash scripts/test-backend.sh '*ContractTest'` | 绿（断言已改为正向） |
| 授权边界 | `bash scripts/test-backend.sh '*AppreciationLetter*'` + `*SecurityBoundary*` | 绿，含跨租户/伪造/隐藏404 |
| 编译 | `bash scripts/test-backend.sh --compile` | 绿（DTO/Mapper 类型一致） |
| OpenAPI | `*PublishedOpenApiContractTest*` | 路径+组件存在 |

## 开放问题（Open Questions）

> 维护者 2026-06-19 决议：**OQ-1~OQ-5 全部采用默认建议**（"OQ 全默认"）。下列均已 Resolved，作为本 spec 的规范要求。

- **OQ-1（S0/S1 字段暴露）— Resolved=是**：响应 VO 以 `senderName`（复用 BE-4 实名解析）替代原始 `senderUserId`；以 target 显示名替代裸 `targetId`；移除响应中的 `kindergartenId`（租户对调用者隐含）。对照 SPEC-0001 §敏感分级最小暴露。
- **OQ-2（园所管理员审核删除）— Resolved=否**：本期 KINDERGARTEN_ADMIN 不可删信，仅作者可删。
- **OQ-3（软删除语义）— Resolved=是**：删除走软删除（`status` 置非 ACTIVE，物理保留）；`status` 不在公开响应回显。
- **OQ-4（创建通知）— Resolved=否**：本期写信不触发通知。
- **OQ-5（列表 keyword 过滤范围）— Resolved=title+content**：keyword 在调用者可见集内命中 title+content。

## 实施记录（Implementation Notes）

实施日期：2026-06-20（worktree: `salvage-codegen-templates`，branch: `worktree-salvage-codegen-templates`）

### 改动文件

**Backend main（新建/修改）：**
- `backend/src/main/java/com/ai_kids_care/v1/controller/AppreciationLetterController.java`（新建）
- `backend/src/main/java/com/ai_kids_care/v1/service/AppreciationLetterService.java`（完全重写）
- `backend/src/main/java/com/ai_kids_care/v1/dto/AppreciationLetterCreateDTO.java`（新建）
- `backend/src/main/java/com/ai_kids_care/v1/dto/AppreciationLetterUpdateDTO.java`（新建）
- `backend/src/main/java/com/ai_kids_care/v1/mapper/AppreciationLetterMapper.java`（扩展：删 toVO，加 toEntity/updateEntity）
- `backend/src/main/java/com/ai_kids_care/v1/vo/AppreciationLetterVO.java`（重写：移除 kindergartenId/senderUserId/targetId/status）
- `backend/src/main/java/com/ai_kids_care/v1/repository/AppreciationLetterRepository.java`（扩展：7 个作用域查询方法）
- `backend/src/main/java/com/ai_kids_care/v1/security/AuthorizationAction.java`（加 APPRECIATION_LETTER_READ/WRITE）
- `backend/src/main/java/com/ai_kids_care/v1/security/AuthorizationPolicy.java`（加对应 case）

**Backend tests（修改/新建）：**
- `backend/src/test/java/com/ai_kids_care/v1/contract/SensitiveWriteContractTest.java`（翻转 5 个 absent 断言为 present）
- `backend/src/test/java/com/ai_kids_care/v1/contract/PublishedOpenApiContractTest.java`（翻转 4 个 absent/pathAbsent 为 present，锁定 VO/DTO 字段集）
- `backend/src/test/java/com/ai_kids_care/v1/security/SecurityBoundaryIntegrationTest.java`（将 /api/v1/appreciation_letters 从 closed 列表迁入 published 列表）
- `backend/src/test/java/com/ai_kids_care/v1/security/AppreciationLetterAuthorizationIntegrationTest.java`（新建，20 个测试用例）

### 关键实施决策

- **JPQL enum 传参**：`StatusEnum.ACTIVE` 和 `AppreciationTargetTypeEnum.TEACHER` 经 `@Param` 传入，而非内联 JPQL 枚举字面量（规避 Hibernate + PostgreSQL named_enum 兼容问题）。
- **toVO 在 Service 实现**：`buildVO()` 私有方法直接构造 VO，含 Guardian/Teacher name lookup（N+1，Phase 1 可接受）。Mapper 无 `toVO`。
- **软删除**：`deleteAppreciationLetter` 将 `status` 置 `DISABLED`，物理行保留。

### 验证结果

| 命令 | 结果 |
| --- | --- |
| `bash scripts/test-backend.sh --compile` | BUILD SUCCESSFUL |
| `bash scripts/test-backend.sh '*ContractTest'` | BUILD SUCCESSFUL（全绿） |
| `bash scripts/test-backend.sh '*AppreciationLetter*'` | BUILD SUCCESSFUL（20/20 通过） |
| `bash scripts/test-backend.sh '*SecurityBoundary*'` | BUILD SUCCESSFUL（全绿） |
| `git diff --check` | 无错误（仅 LF/CRLF 警告） |
