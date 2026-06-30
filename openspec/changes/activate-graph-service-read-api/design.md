## Context

平台有一条 Neo4j 只读派生关系图（PostgreSQL 是 system-of-record，Neo4j 不含 PII，INC-003 由 `LoaderPiiProjectionGuardTest` 守护）。DB-3 后图随 loader 从 PG 重建，已新鲜可用。但唯一的图查询入口 `GraphService.getChildGraph` 被 `@PreAuthorize("denyAll()")` 刻意冻结——这是一个故意的"default-deny"安全姿态：service 未接任何 live controller，注解防止将来误用绕过授权（见 `GraphService` 注释）。

现状关键事实（已核对源码）：
- `GraphRepository.findChildGraph(childId)` 的 Cypher **没有任何 `kindergarten_id` 谓词**，按 `child_id` 全局匹配——直接激活会跨租户泄漏。
- loader 已把 `kindergarten_id` 投影到 Teacher/Class/Child/Guardian 节点（`CHILD_COLUMNS` 等白名单含 `kindergarten_id`），Kindergarten 节点以 `kindergarten_id` 为键——**租户谓词在 Cypher 内可落地**。
- repository miss 抛 `NoSuchElementException`，而 `ApiExceptionHandler` 只映射 `EntityNotFoundException`→404；`NoSuchElementException` 会逃逸成 500。
- `ChildGraphVO` 只承载图节点的非 PII 列（child name/childNo/gender/status、class、teacher、kindergarten、guardians + 边属性 relationship/is_primary/priority）。`name` 不在 INC-003 禁列、在 loader 白名单内，属允许显示字段。
- 前端 `graph.api.ts` 仅有 `ChildGraph` 类型定义，无 RTK Query endpoint、无组件消费。

授权范式（已核对）：`@PreAuthorize("@authorizationPolicy.isAllowed(T(...AuthorizationAction).XXX)")` 标在 **service** 方法上；`AuthorizationPolicy.isAllowed` 从 `EffectiveAuthorizationContextHolder.get()` 取 ThreadLocal context 做粗粒度 role+tenantIdentity 判断；细粒度租户隔离由 repository 查询内的 `kindergarten_id` 谓词强制（load-then-filter 禁止）。`DetectionEventService` 是最贴近的镜像：service `@PreAuthorize(DETECTION_EVENT_READ)` + `requireActiveKindergartenId()` + `findByIdAndKindergarten_Id(...).orElseThrow(EntityNotFoundException)`。

## Goals / Non-Goals

**Goals:**
- 让 `getChildGraph` 成为可达、tenant-safe、no-PII 的只读图查询 API。
- 用 `GRAPH_READ` 授权门替换 `denyAll()`，与既有 detection 看板的受众/范式对齐。
- 把 `kindergarten_id` 谓词写进 Cypher（非加载后过滤），跨租户/不存在统一 404。
- 保持 INC-003 不被削弱：VO 映射只读 Neo4j 节点，**绝不**回连 PostgreSQL 取 name 以外的字段或任何 PII。

**Non-Goals:**
- **不**新增任何到 Neo4j 的写路径（图仍由 loader 单向重建；Neo4j 非权威）。
- **不**实现增量/实时 PG→Neo4j 同步（仍是 DB-3 的 one-shot；图可能 stale，本变更不解决新鲜度）。
- **不**暴露任何 PII（图中本就没有 PII；本变更也不引入回连 PG 的 PII join）。
- **不**改 PostgreSQL schema / Flyway / JPA（无迁移）。
- 前端 UI 接线**默认不在本变更**（见 Decision 5 + Open Questions）。

## Decisions

### Decision 1：授权门 `GRAPH_READ` = tenant identity + TEACHER / KINDERGARTEN_ADMIN
新增 `AuthorizationAction.GRAPH_READ`，在 `AuthorizationPolicy` 加：
```
case GRAPH_READ -> tenantIdentity && (role == TEACHER || role == KINDERGARTEN_ADMIN);
```
**理由**：关系图含教师、共同监护人等跨主体信息，是园所运营视角的工具，受众应与 detection 看板（`DETECTION_EVENT_READ`）一致 = 本园 staff。
**Alternatives considered**：
- 把 `GUARDIAN` 纳入（镜像 `CHILD_READ` 的 GUARDIAN+TEACHER）——被否，因为一个监护人看到孩子的完整关系图会顺带看到**共同监护人**及教师，属隐私外溢；若未来需要，应是"监护人只见自己 + 孩子"的窄化变体，留作 Open Question。
- 纳入 `KINDERGARTEN_ADMIN` 之外的平台角色——被否，平台角色无 tenant identity，天然被 `tenantIdentity` 拒。

### Decision 2：租户谓词写进 Cypher（锚点 + 沿途约束）
`GraphRepository.findChildGraph` 改签名为 `findChildGraph(Long childId, Long kindergartenId)`，Cypher：
```cypher
MATCH (ch:Child {child_id: $childId, kindergarten_id: $kgId})
OPTIONAL MATCH (c:Class {kindergarten_id: $kgId})-[:HAS_CHILD]->(ch)
OPTIONAL MATCH (t:Teacher {kindergarten_id: $kgId})-[:HAS_CLASS]->(c)
OPTIONAL MATCH (k:Kindergarten {kindergarten_id: $kgId})-[:HAS_TEACHER]->(t)
OPTIONAL MATCH (ch)-[rg:HAS_GUARDIAN]->(g:Guardian {kindergarten_id: $kgId})
RETURN ch, c, t, k, collect({guardian: g, relationship: rg.relationship,
        is_primary: rg.is_primary, priority: rg.priority}) AS guardians
```
**理由**：锚点 `{child_id, kindergarten_id}` 让跨租户 child 直接 miss → 404（隐藏存在性）；沿途 `{kindergarten_id: $kgId}` 是 defense-in-depth，即便图被错误装填出跨租户边也不会投影。`kgId` 来自 `requireActiveKindergartenId()`，**前端不传**。

### Decision 3：404-on-miss / 跨租户
`findChildGraph` 在 `!result.hasNext()` 时抛 `jakarta.persistence.EntityNotFoundException`（替换现 `NoSuchElementException`），由既有 `ApiExceptionHandler` 映射为 404 `{"error":"Resource not found"}`。跨租户 childId、不存在 childId、本园但无该 child 三种情况**同样** 404，不区分 403/200/500。

### Decision 4：VO 映射只读 Neo4j，禁回连 PG
沿用现有 `ChildGraphVO` 与 `GraphRepository` 的 Neo4j Driver 节点→VO 手工映射；**不**引入任何 JPA/PG 查询来补字段。这是 INC-003 的应用层延伸：图里没有 PII，VO 也不得通过回连 PG 把 rrn/birth_date/address/phone 等带回。`name`/`gender`/`status` 等已是图内允许字段。

### Decision 5：端点形状 + 前端接线（已纳入本变更，2026-06-30）
- 后端端点：`GET /api/v1/graph/children/{childId}` 与 `GET /api/v1/graph/teachers/{teacherId}`（路径用连字符 `graph/children`、`graph/teachers`，与 `detection-events` 同风格；id 是路径变量，但 tenant 维度**不**在 URL）。Controller 仅路由分发，`@PreAuthorize` 在 service。
- 前端：把 `graph.api.ts` 从类型桩补成 RTK Query endpoint（`getChildGraph` + `getTeacherGraph` 两个 query），并在 UI（如儿童详情页关系图，评估 reagraph）消费。契约与 `ChildGraphVO`/`TeacherGraphVO` 对齐。
**理由（裁定）**：维护者选择「要做就做全」，把后端 API、teacher 用例、前端消费一并落在本变更，而非拆多个 slice。安全核心（放松 `denyAll()` + 租户谓词进 Cypher + 404）仍是评审重点，前端/teacher 用例复用同一 `GRAPH_READ` 门与同一 `kindergarten_id` 谓词范式，不引入新授权面。

### Decision 6：第二用例 `getTeacherGraph`（已纳入本变更，2026-06-30）
`(Teacher{kindergarten_id})-[:HAS_CLASS]->(Class)-[:HAS_CHILD]->(Child)` 为教师列出其班级与班内儿童，有产品价值且图模型支持。本变更新增 `TeacherGraphVO` + `findTeacherGraph` Cypher，**复用** child 用例同样的 `kindergarten_id` 谓词（锚点 `MATCH (t:Teacher {teacher_id:$teacherId, kindergarten_id:$kgId})`）、404-on-miss、`GRAPH_READ` 门。规格新增对应 requirement（teacher-centric 查询亦 SHALL 可达且 tenant-safe）。

### Decision 7：引入 Neo4j Testcontainer 到测试基座（已定，2026-06-30）
后端测试基座当前**无 Neo4j 容器**（`BaseIntegrationTest` 在 `application-test.yml` 显式排除 Neo4j；`LoaderPiiProjectionGuardTest` 是纯源码静态扫描，不连图库）。本变更是安全敏感的跨租户隔离激活，仅 mock Driver 单测**无法验真 Cypher 的 `kindergarten_id` 谓词是否真的隔离跨租户边**。**决策**：引入 Neo4j Testcontainer（最小图 fixture：两租户各一 child + class + teacher + guardian），写真 Cypher 集成测试覆盖「本园 200 / 跨租户 404 / 不存在 404 一致」。这是基座级改动（新增容器、可能拖慢测试），但对放松 `denyAll()` 的安全变更是必要的验证强度。

## Risks / Trade-offs

- **[放松 denyAll() 引入越权风险]** → 租户谓词在 Cypher 锚点强制 + service 取 `requireActiveKindergartenId()`（不信任 URL）+ 跨租户 404；新增针对"跨租户 childId 返回 404、同 childId 不同租户互不可见"的集成测试（需 Neo4j Testcontainer）。
- **[图 stale 导致查询返回过期关系]** → 这是 DB-3 既有的 one-shot 取舍，本变更不解决；VO/文档应表明图反映 loader 运行时刻。Non-goal。
- **[VO 未来被加字段时误回连 PG]** → 在 `GraphService`/`GraphRepository` 注释 + 规格 scenario 固化"只读 Neo4j、禁回连 PG PII"；可加一个断言 repository 不依赖 JPA 的轻量守护（Open Question）。
- **[Neo4j Testcontainer 不在现有 backend 测试基座]** → 需确认 `BaseIntegrationTest` 是否已起 Neo4j；若无，测试策略需补（见 Open Questions），否则该用例只能单测 mock Driver。
- **[teacher 细粒度作用域缺失]** → 当前门只到"本园 staff"，未限制"教师只见自己 assignment 班级的 child 图"。`CHILD_READ` 路径是在 SQL 内做 assignment-scoped 细化的；图路径暂只做 tenant 级隔离。是否需要 per-assignment 细化见 Open Questions。

## Open Questions

1. **角色范围**：`GUARDIAN` 是否需要一个窄化的 child-graph（只见自己 + 孩子、隐藏共同监护人/教师）？**本提案默认仅 TEACHER + KINDERGARTEN_ADMIN**（保持默认；GUARDIAN 窄化图若需要列后续）。
2. **教师细粒度**：teacher 是否应被限制为只查其 ACTIVE assignment 班级内的 child（像 `CHILD_READ` 那样 SQL-scoped），还是 tenant 级隔离足够？**默认 tenant 级**；per-assignment 细化列后续（注意：`getTeacherGraph` 默认让 teacher 见本园任意 teacher 的图，是否需收紧为「只见自己」待评审）。
3. ~~**第二用例时机**~~ **已定（2026-06-30）：`getTeacherGraph` 纳入本变更**（Decision 6）。
4. ~~**前端归属**~~ **已定（2026-06-30）：前端接线纳入本变更**（Decision 5）。
5. **端点命名/形状**：`GET /api/v1/graph/children/{childId}`、`/graph/teachers/{teacherId}` 是否最终形状？是否要分页/裁剪 guardians？（默认不分页，图规模小。）
6. ~~**测试基座**~~ **已定（2026-06-30）：引入 Neo4j Testcontainer**（Decision 7）。
7. **VO 回连守护**：是否值得加一个静态/架构测试断言 `GraphRepository` 不依赖任何 JPA repository（防未来回连 PG）？（倾向加一个轻量守护，列入 tasks。）
