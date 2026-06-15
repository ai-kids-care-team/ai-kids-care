---
ADR: ADR-0021
title: "ADR-0021: Admin 审批与安全审计的 schema 迁移（REJECTED 状态、role/membership 唯一约束、audit_logs 平台 scope）"
status: Accepted
implementation: Complete
date: 2026-06-15
deciders: 接手人起草，维护者 Accept（2026-06-15）
supersedes: []
superseded_by: null
related_specs:
  - SPEC-0001
---

# ADR-0021: Admin 审批与安全审计的 schema 迁移

> 本 ADR 把 SPEC-0001 第 4 阶段「资源关系与审计」与 admin 审批所**共享的数据库 schema 改动**合并为一次受 [ADR-0012](ADR-0012-production-data-lifecycle.md) 治理的迁移设计。它**只决定 schema**；admin 端点的规范 spec 与审计 writer 的应用层设计是后续独立 Design 产物。落地（DBML→迁移生成→评审→应用）委派 Implementation。

## 状态（Status）

Decision: `Accepted`（2026-06-15 维护者 Accept）

Implementation: `Complete`

> 维护者于 2026-06-15 拍板：(D1) **新增 `REJECTED`** 到 `status_enum`；(D2) **采纳混合形态 (b)**（`scope_type` + `kindergarten_id`/`user_id` 可空 + CHECK + 补 `effective_role`/`result`/`correlation_id`）。其余按推荐缺省。

> 实施状态（2026-06-16，已合入 develop）：V2 迁移落地于 `backend/src/main/resources/db/migration/V2__admin_audit_schema.sql`（commit `b9da5a6`）——含 `StatusEnum`+`REJECTED`、`user_role_assignments`/`user_kindergarten_memberships` 的单-ACTIVE 部分唯一索引 + scope CHECK、`audit_logs` 平台 scope + 补字段、`AuditLog` 实体、`db/dbml/schema.dbml` 更新、新增 `V2SchemaConstraintIntegrationTest`（5 项）。`db/initdb/01_create_schema.sql` 保持 V1 基线冻结（修正提交 `61e16ab`：先前误把 V2 写进 initdb 致 Flyway 重复应用失败，CI 捕获）。develop CI（Backend Java Tests + Compose Config + Frontend Lint & Build）全绿 → 约束/审计 schema 经 Testcontainers 端到端验证。残留：V2 文件的 Flyway 可应用性由手动 psql + 评审覆盖（CI 经 initdb 而非 Flyway，待 ADR-0013 解禁 `FlywayMigrationTest` 后自动化）；审计 writer / correlation-id filter / admin 端点属 #1/#2 后续。

## 背景（Context）

### 为何合并（事实）
2026-06-15 对候选目标 #1（安全审计写入）与 #2（Admin 管理端点）做并行只读 Discovery，发现两者撞上**同一批**数据库 schema 缺口，分别迁移会造成多次高风险 DB 变更与演示/生产基线多次漂移。合并为一次「安全阶段 schema bump」（Flyway `V2`）最稳。

### As-built schema 事实（经 `git`/`gh`/源码核实）
- `StatusEnum` 仅 `ACTIVE / PENDING / DISABLED`（`backend/.../enums/StatusEnum.java:3-7`；DB `status_enum`，`db/initdb/01_create_schema.sql:1`）。**无 `REJECTED`**，但 SPEC 多处描述 `PENDING → REJECTED`（`SPEC-0001:124,132`、验收 `:333,337`）。
- `user_role_assignments`：唯一约束为 `(user_id, role, scope_type, scope_id)`（`01_create_schema.sql:340-345`）。PostgreSQL 的 NULL 语义使 `PLATFORM`（`scope_id=NULL`）可重复授予；无 `scope_type/scope_id` 合法组合 CHECK（ADR-0019 §背景已记，`ADR-0019:48`）。
- `user_kindergarten_memberships`：唯一约束仅 `(kindergarten_id, user_id)`（`01_create_schema.sql:327`）。**无「单 ACTIVE membership/用户」约束**。
- 上述两点使「批准激活」可能造出第 2 条 ACTIVE role/membership，破坏 `EffectiveAuthorizationContextService.resolveIdentity` 的「恰一条 ACTIVE」前提（`:93,107`）——已批准用户随后登录会被判通用 `401`。**批准流必须与约束修复协同**。
- `audit_logs`：`kindergarten_id bigint NOT NULL` 且 `user_id bigint NOT NULL`（`01_create_schema.sql:472-482`，与 `V1__initial_baseline.sql:478-488` 字节一致）。缺 `effective_role`、`result`、`correlation_id`、`scope_type`。**全系统零写入方**（Grep 确认）。无法表达：平台级事件（无园）、actor 未知的登录失败/枚举尝试。SPEC `:284` 明确「可用 `scope_type+scope_id` 或等价的『平台事件允许 tenant id 为空』；**不得伪造 kindergarten id 代表平台**」。

### 治理约束
- 迁移受 [ADR-0012](ADR-0012-production-data-lifecycle.md) 治理：`db/dbml/schema.dbml` 为 schema 设计单一真相（反映当前**全量** schema，含 V2），应改 DBML 后生成迁移、人工评审入库；ERD `.mmd` 由 schema 重新派生。**关键（2026-06-16 勘误）**：`db/initdb/01_create_schema.sql` 是 **V1 基线快照、保持冻结不变**——schema 改动**只进 Flyway 迁移**（`V2+`）。测试/演示路径 = initdb 建 V1 → Flyway `baseline-on-migrate`（`baseline-version: 1`）在 V1 → 叠加 V2（见 `backend/src/main/resources/application.yml`）。**把 V2 改动也写进 initdb 会让 Flyway 重复应用同一 DDL 而报错**（CI 已验证此教训）。
- `FlywayMigrationTest` 当前整类 `@Disabled`（前置是 [ADR-0013](ADR-0013-dictionary-tables-governance.md) 删除遗留 `CommonCode` 映射），即 [ADR-0014](ADR-0014-test-baseline.md) 所述「2 个预期 skip」。本迁移**不得**夹带解禁逻辑、不得用新表规避解禁（ADR-0014 明确禁止）。
- 角色/scope 唯一约束修复，ADR-0019 §分阶段边界已指明「由后续 Implementation task 通过 ADR-0012 流程执行」——即本 ADR。

## 决策（Decision）

一次 Flyway `V2` 迁移（DBML-first 生成），覆盖三组改动，使 admin 审批与安全审计的实现得以解除 schema 阻塞：

### 1. 注册拒绝状态（D1，已采纳）
**决策**：新增 `REJECTED` 到 `status_enum`，用于 `PENDING → REJECTED` 的申请拒绝；与「曾激活后被停用」的 `DISABLED` 语义区分。备选见方案比较。

### 2. role/membership 完整性约束（推荐缺省）
- `user_role_assignments` 增加 CHECK：`(scope_type='PLATFORM' AND scope_id IS NULL) OR (scope_type='KINDERGARTEN' AND scope_id IS NOT NULL)`。
- `user_role_assignments` 增加部分唯一索引：`UNIQUE (user_id) WHERE status='ACTIVE'`（每用户至多一条 ACTIVE role assignment——把 SPEC「单账号单角色」「恰一条 ACTIVE」固化到 DB）。
- `user_kindergarten_memberships` 增加部分唯一索引：`UNIQUE (user_id) WHERE status='ACTIVE'`（每用户至多一条 ACTIVE membership；平台账号零 membership 由应用层保证）。
- 迁移前须先核查并清洗既有数据是否已违反这些约束（演示/种子数据），否则 `V2` 应用失败。

### 3. audit_logs 平台 scope + SPEC 字段补全（D2，已采纳）
**决策**（混合形态 b）：
- 新增 `scope_type`（复用 `user_role_assignments` 的 scope_type enum）；`kindergarten_id` 改 **可空并保留 FK**（园级事件填、平台级事件 NULL）；`user_id` 改 **可空**（actor 未知的登录失败/枚举）。
- 增加 CHECK：`(scope_type='PLATFORM' AND kindergarten_id IS NULL) OR (scope_type='KINDERGARTEN' AND kindergarten_id IS NOT NULL)`——正面回应 SPEC「不得伪造 kindergarten id」。
- 补 SPEC `:282` 要求的字段：`effective_role`（varchar/enum）、`result`（如 SUCCESS/DENIED/FAILURE）、`correlation_id`（varchar）。`action` 维持 varchar，由应用层 `AuditAction` 枚举约束（避免 DB enum 频繁迁移）。
- 该形态与 `EffectiveAuthorizationContext` 的 `scopeType/effectiveKindergartenId()` 同构，writer 零阻抗落列。

### 4. 边界
- 本 ADR 不定义 admin 端点契约（→ 后续 #2 spec）、不定义审计 writer 应用层设计与 correlation-id filter（→ 后续 #1 设计；correlation-id 机制当前不存在，需新增 request-id filter+MDC）。
- 不解禁 `FlywayMigrationTest`、不碰 `CommonCode`（ADR-0013 范畴）。
- `audit_logs` 的 append-only 数据库级强约束（REVOKE UPDATE/DELETE 或触发器）属高风险 DB 权限，单列为可选项，由维护者另行决定，不在本 V2 强制。

## 方案比较（Options）

### D1：REJECTED 状态
| 方案 | 优点 | 代价/风险 | 结论 |
| --- | --- | --- | --- |
| A. 新增 `REJECTED` enum 值（**推荐**） | 语义清晰；与 SPEC 文字一致；「拒绝申请」≠「停用账号」 | PostgreSQL `ALTER TYPE ... ADD VALUE` 在旧版不可在事务内执行、且 enum 值**不可删/不可改序**（近乎不可逆）；需评估 PG 版本 | 倾向采纳 |
| B. 复用 `DISABLED` 表示拒绝 | 零 enum 迁移 | 语义混淆（拒绝 vs 停用无法区分）；审计/查询要靠额外字段或 reason 辨别 | 备选（若不愿动 enum） |

### D2：audit_logs 平台 scope
| 维度 | (a) `kindergarten_id` 改可空 | (b) 增 `scope_type` + `kindergarten_id`/`user_id` 可空 + CHECK（**推荐**） |
| --- | --- | --- |
| 平台/园 判别 | 靠约定，无显式列 | `scope_type` 显式，与 role assignment 模型同构 |
| 约束可校验 | 难（无法区分平台 vs 漏填） | 可加 CHECK 固化「平台=园 id 必空」 |
| actor 未知 | 仍需单独放开 `user_id` | 同样放开 `user_id`，语义自洽 |
| 改动面 | 最小 | 略大（加列+CHECK），但一次到位 |
| 与 EAC 同构 | 否 | 是（writer 零阻抗） |

## 后果（Consequences）

- **正面**：一次迁移同时解锁 #2（批准/拒绝流的状态与完整性约束）与 #1（平台级/匿名审计可写）；把 SPEC 的「单账号单角色/单园」「恰一条 ACTIVE」从应用层推进到 DB 层纵深防御；audit schema 一次补齐 SPEC 要求字段。
- **负面 / 代价**：高风险 DB 迁移（schema + enum）；`ALTER TYPE ADD VALUE`（若选 D1-A）近不可逆；既有数据若违反新唯一约束/CHECK 需先清洗；DBML 与 ERD 随当前 schema 更新，`db/initdb` 保持 V1 基线冻结（**不**随 V2 改，否则 Flyway 重复应用报错）。
- **影响范围**：`db/dbml/schema.dbml`、`backend/.../db/migration/V2__*.sql`、`db/initdb/01_create_schema.sql`、相关 JPA 实体（`UserRoleAssignment`/`UserKindergartenMembership`/`AuditLog`）、`StatusEnum`、后续 #1/#2 实现、`FlywayMigrationTest` 的 `coreTablesCreatedByV1` spot-check（待 ADR-0013 解禁后补 `audit_logs` 校验）。

## 合规与验证（Compliance）

- 迁移须走 DBML-first：改 `schema.dbml` → 生成 `V2` → 人工评审。`db/initdb/01_create_schema.sql` 保持 V1 基线冻结、**不**随 V2 更新（测试/演示经 Flyway 叠加 V2）。
- `V2` 在空库 `V1` 之后干净执行；既有种子/演示数据不违反新约束（迁移前核查）。
- 后端 `gradlew test`（含 Testcontainers）通过；新增针对约束（重复 ACTIVE role/membership 被拒）、audit 平台事件可写（`scope_type=PLATFORM` 且 `kindergarten_id=NULL`）的测试。
- 不解禁 `FlywayMigrationTest`、不夹带 `CommonCode` 改动。
- 后续 #1/#2 实现以本 schema 为前提；审计字段与 SPEC `:282` 对齐。

## 关联（References）

- [SPEC-0001](../../specs/SPEC-0001-auth-authorization-tenant-sensitive-data-boundaries.md)（第 4 阶段「资源关系与审计」、审计要求 `:270-284`、不变量 `:290-298`）
- [ADR-0012](ADR-0012-production-data-lifecycle.md)（迁移流程治理：DBML-first）、[ADR-0013](ADR-0013-dictionary-tables-governance.md)、[ADR-0014](ADR-0014-test-baseline.md)（FlywayMigrationTest 基线）
- [ADR-0019](ADR-0019-effective-authorization-context-tenant-enforcement.md)（§4 事务/TOCTOU、§背景 唯一约束缺口 `:48`、§分阶段边界 委托本迁移）
- db/initdb/01_create_schema.sql、backend/.../entity/AuditLog.java、backend/.../enums/StatusEnum.java
