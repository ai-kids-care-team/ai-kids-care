## Why

DB-3（已归档 `2026-06-30-neo4j-sync-from-postgres`）让 Neo4j 派生关系图在每次 loader 运行时从 PostgreSQL 重建，图现在是新鲜可用的（节点 User/Kindergarten/Teacher/Class/Child/Guardian/Role，关系 HAS_ROLE/HAS_TEACHER/HAS_CLASS/HAS_CHILD/HAS_GUARDIAN，且 INC-003 保证不含 PII）。但后端 `GraphService` 仍是 `@PreAuthorize("denyAll()")` 的**休眠**状态——唯一的图查询用例 `getChildGraph` 不可达，前端 `graph.api.ts` 只有一个 `ChildGraph` 类型桩、没有任何调用。本变更激活该只读查询面：定义用例、用 tenant-scoped 授权替换 `denyAll()`、把 `kindergarten_id` 谓词写进 Cypher，让"新鲜的图"真正能被教职员查询。

## What Changes

- **激活 `GraphService.getChildGraph`**：移除 `@PreAuthorize("denyAll()")`，替换为 `@PreAuthorize("@authorizationPolicy.isAllowed(... GRAPH_READ)")`（service 方法级，镜像 `DetectionEventService` 范式）。
- **新增 `GRAPH_READ` AuthorizationAction**：粗粒度门 = 有效 tenant identity + `TEACHER` / `KINDERGARTEN_ADMIN`（与 detection 看板受众一致）；在 `AuthorizationPolicy` 增对应 `case`。
- **租户谓词写进 Cypher**：`GraphRepository.findChildGraph` 增加 `kindergartenId` 参数，锚点 `MATCH (ch:Child {child_id: $childId, kindergarten_id: $kgId})`，并对沿途 Class/Teacher/Guardian 约束同一 `kindergarten_id`（防跨租户边泄漏）。`kindergartenId` 来自 `EffectiveAuthorizationContextHolder.requireActiveKindergartenId()`（ThreadLocal），**不**来自 URL/请求参数。
- **跨租户/不存在 → 404**：把 repository 的 `NoSuchElementException`（当前未被异常处理器映射，会变 500）改为 `EntityNotFoundException`（`ApiExceptionHandler` 已映射 404），隐藏存在性。
- **新增 `GraphController`**：`GET /api/v1/graph/children/{childId}` → `ChildGraphVO`（默认受 default-deny 保护，**不**进 `/api/v1/internal/**` 前缀、**不**进 CSRF 豁免）。
- **第二用例 `getTeacherGraph`（已纳入本变更，维护者 2026-06-30 裁定）**：`(Teacher)-[:HAS_CLASS]->(Class)-[:HAS_CHILD]->(Child)`，为教师列出其班级与班内儿童；新 `TeacherGraphVO` + Cypher，同样 `kindergarten_id` 谓词 + 404、同 `GRAPH_READ` 门。`GET /api/v1/graph/teachers/{teacherId}`。
- **前端接线（已纳入本变更）**：把 `graph.api.ts` 从类型桩补成 RTK Query endpoint（child + teacher 两个 query）并在 UI 消费；契约与 `ChildGraphVO`/`TeacherGraphVO` 对齐。

## Capabilities

### New Capabilities
<!-- 无新增 capability：图查询面已属 data-platform。 -->

### Modified Capabilities
- `data-platform`: 把"Graph query reads from Neo4j only"从一个数据层断言，收紧/扩展为一个**可达且 tenant-safe 的只读查询 API** 要求——租户谓词必须在 Cypher 内、跨租户返回 404、授权门替换 `denyAll()`、VO 映射只读 Neo4j 节点不回连 PG（不削弱 INC-003）。

## Impact

- **Backend**：`AuthorizationAction`（+`GRAPH_READ`）、`AuthorizationPolicy`（+case）、`GraphService`（替换注解 + 取 active kg；+`getTeacherGraph`）、`GraphRepository`（+kgId 参数/谓词、异常类型；+`findTeacherGraph`）、新 `GraphController`（child + teacher 两端点）、新 `TeacherGraphVO`。
- **安全配置**：新端点自动受 `anyRequest().authenticated()` 保护，无需改 `SecurityFilterChain`；须确认不误入 internal/CSRF 豁免。
- **无 schema 迁移**：Neo4j 是只读派生视图；PostgreSQL/Flyway 不动。
- **测试基座（已知缺口）**：后端测试基座当前**无 Neo4j 容器**（`BaseIntegrationTest` 在 `application-test.yml` 显式排除 Neo4j、`LoaderPiiProjectionGuardTest` 是纯源码扫描）。本变更为验真 Cypher 租户隔离，**引入 Neo4j Testcontainer** 到测试基座（维护者裁定）。
- **Frontend**：`graph.api.ts` RTK Query（child + teacher）+ UI 消费（已纳入本变更）。
- **不触及**：AI 子系统、Neo4j loader、写路径。
