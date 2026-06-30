## 1. 授权门（GRAPH_READ）

- [x] 1.1 在 `AuthorizationAction` 枚举新增 `GRAPH_READ`（带注释：tenant identity + TEACHER/KINDERGARTEN_ADMIN 粗门，细粒度租户隔离由 Cypher kindergarten_id 谓词强制）
- [x] 1.2 在 `AuthorizationPolicy.isAllowed` 新增 `case GRAPH_READ -> tenantIdentity && (role == TEACHER || role == KINDERGARTEN_ADMIN);`

## 2. Repository 租户谓词 + 404

- [x] 2.1 把 `GraphRepository.findChildGraph(Long childId)` 改签名为 `findChildGraph(Long childId, Long kindergartenId)`
- [x] 2.2 Cypher 锚点改为 `MATCH (ch:Child {child_id: $childId, kindergarten_id: $kgId})`，并给沿途 `Class`/`Teacher`/`Kindergarten`/`Guardian` 加 `{kindergarten_id: $kgId}` 约束；绑定 `$kgId` 参数
- [x] 2.3 把 miss 时抛出的 `NoSuchElementException` 改为 `jakarta.persistence.EntityNotFoundException`（由 `ApiExceptionHandler` 映射 404）

## 3. Service 激活

- [x] 3.1 移除 `GraphService.getChildGraph` 上的 `@PreAuthorize("denyAll()")`
- [x] 3.2 加 `@PreAuthorize("@authorizationPolicy.isAllowed(T(com.ai_kids_care.v1.security.AuthorizationAction).GRAPH_READ)")` + `@Transactional(readOnly = true)`
- [x] 3.3 在方法内取 `Long kindergartenId = EffectiveAuthorizationContextHolder.requireActiveKindergartenId();` 并传入 `graphRepository.findChildGraph(childId, kindergartenId)`
- [x] 3.4 更新 `GraphService` 注释（去掉"未接入 controller / denyAll"措辞，写明 tenant-scoped + GRAPH_READ）

## 4. Controller

- [x] 4.1 新增 `GraphController`（`@RestController @RequestMapping("/api/v1/graph")`），仅注入 `GraphService`
- [x] 4.2 `GET /children/{childId}` → `ResponseEntity<ChildGraphVO>`，仅路由分发（不在 controller 加授权/取 kindergartenId）
- [x] 4.3 确认端点未误入 `/api/v1/internal/**` 前缀、未进 CSRF 豁免、受 default-deny 保护（无需改 `SecurityFilterChain`）

## 5. 测试（tenant-safety / no-PII 为一等公民）

- [x] 5.1 引入 Neo4j Testcontainer 到测试基座（design Decision 7）：最小双租户图 fixture（各一 child+class+teacher+guardian），供真 Cypher 集成测试；确认与既有 PG/Redis testcontainer 基座共存、`application-test.yml` 不再全局排除 Neo4j（仅排除非图测试路径）
- [x] 5.2 集成测试：本园 staff 取本园 child → 200 且 VO 字段正确（真 Cypher）
- [x] 5.3 集成测试：跨租户 childId → 404；不存在 childId → 404（两者响应一致，隐藏存在性）
- [x] 5.4 集成/单元测试：无 tenant identity / GUARDIAN / 平台角色 → 拒绝
- [x] 5.5 断言/审查：Cypher 含 `kindergarten_id` 谓词、VO 映射不回连 PG（no-PII 路径）
- [x] 5.6 轻量守护测试：断言 `GraphRepository` 不依赖任何 JPA repository（防未来回连 PG，design OQ7）
- [x] 5.7 teacher-graph 同等测试：本园 teacher → 200；跨租户 teacherId → 404；Cypher 含 `kindergarten_id` 谓词

## 6. 前端接线（已纳入本变更）

- [x] 6.1 把 `frontend/src/services/apis/graph.api.ts` 从类型桩补成 RTK Query endpoint（`getChildGraph` 命中 `GET /api/v1/graph/children/{childId}`、`getTeacherGraph` 命中 `GET /api/v1/graph/teachers/{teacherId}`），经 `baseApi` 注入 CSRF/会话
- [x] 6.2 在 UI 消费（如儿童详情页关系图，评估 reagraph）；类型与 `ChildGraphVO`/`TeacherGraphVO` 对齐
- [x] 6.3 前端 `npm run lint && npm run build`（本机 node 在 PATH）通过

## 7. 第二用例 getTeacherGraph（已纳入本变更）

- [x] 7.1 新增 `TeacherGraphVO`（teacher → classes → children）
- [x] 7.2 `GraphRepository.findTeacherGraph(Long teacherId, Long kindergartenId)`（Cypher 锚点 `MATCH (t:Teacher {teacher_id:$teacherId, kindergarten_id:$kgId})`-[:HAS_CLASS]->(Class)-[:HAS_CHILD]->(Child)，同样 kindergarten_id 谓词 + 404）
- [x] 7.3 `GraphService.getTeacherGraph`（GRAPH_READ + requireActiveKindergartenId）+ `GraphController` `GET /api/v1/graph/teachers/{teacherId}`

## 8. 验收

- [x] 8.1 `cd backend && ./gradlew test` 通过（含新增 graph 测试）— Lead DooD 串行运行：**全套件绿**，`GraphReadApiTest` 11/11、`GraphIntegrationTest`/no-PG-join guard 通过、零回归（修了一处 fixture phone 冲突 `bb1ce61`）
- [x] 8.2 `openspec validate activate-graph-service-read-api --strict` 通过
- [x] 8.3 自审：未新增 Neo4j 写路径、未实现增量同步、响应无 PII、无 schema 迁移
