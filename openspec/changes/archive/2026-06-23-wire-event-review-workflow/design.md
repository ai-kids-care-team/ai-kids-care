## Context

闭环步骤②。已核实:
- `EventReview` 实体齐(event/kindergarten/user reviewer/from_status/result_status/comment/created_at);append-only(每事件可多条复核行,无唯一约束)。
- `EventReviewService` 全 `denyAll()`(list via Specification / get / getLatest);`EventReviewController` 空壳;`EventReviewRepository` = JpaRepository + JpaSpecificationExecutor + `findTopByDetectionEvents_IdOrderByIdDesc`。
- `detection_events.status` 无服务可更新(`DetectionEventService` denyAll);`DetectionEventRepository.findByIdAndKindergarten_Id` 可租户 scoped 加载。
- `EventStatusEnum`: OPEN/ACKNOWLEDGED/IN_REVIEW/RESOLVED/DISMISSED/ESCALATED。
- 授权模式:`@PreAuthorize(@authorizationPolicy.isAllowed(ACTION))` 粗门 + service 用 `EffectiveAuthorizationContextHolder` 租户 scoped(镜像 NotificationService 读路径)。
- 无 schema 改动。

## Goals / Non-Goals

**Goals:** staff 对 detection 事件提交复核(写 event_reviews + 更新 detection_event.status)+ 复核记录读 API;租户 scoped;ADMIN/TEACHER。
**Non-Goals:** 步骤③ 家长通知(explore 后另做);detection-event 读 API;SMS/前端;schema 改动;confirm 本期不触发任何通知。

## Decisions

### D1：confirm 端点写 review + 更新事件状态(同一事务)
`POST /api/v1/event_reviews {eventId, resultStatus, comment?}`:
1. `kgId = requireActiveKindergartenId()`;`reviewerId = require().userId()`。
2. `event = detectionEventRepository.findByIdAndKindergarten_Id(eventId, kgId)` → 空则 `EntityNotFoundException`(隐藏 404,不泄露跨园事件)。
3. `fromStatus = event.getStatus()`。
4. 校验 `resultStatus ∈ {ACKNOWLEDGED,IN_REVIEW,RESOLVED,DISMISSED,ESCALATED}`(排除 OPEN)→ 否则 `IllegalArgumentException`(400)。
5. 写 `EventReview(detectionEvents=event, kindergarten=event.getKindergarten(), user=getReferenceById(reviewerId), fromStatus, resultStatus, comment)` → save。
6. `event.setStatus(resultStatus)` → `detectionEventRepository.save(event)`(JPA UPDATE;dedup_key 未映射,UPDATE 不触及它)。
- `@Transactional`:review 行 + 状态更新原子。
- 返回 201 + `EventReviewVO`。

### D2：授权 = EVENT_REVIEW_WRITE / EVENT_REVIEW_READ(ADMIN/TEACHER + tenant identity)
`AuthorizationPolicy`: 两 action → `tenantIdentity && (role==KINDERGARTEN_ADMIN || role==TEACHER)`(镜像 TENANT_S2_READ 模式)。替换 EventReviewService 三方法的 denyAll(write→confirm 用 WRITE;list/get→READ;getLatest 保 denyAll 留作 ③ 内部用)。

### D3：读 API 租户 scoped
- `listEventReviews(eventId)`:在既有 Specification 上**追加 kindergarten 谓词**(`root.kindergarten.id == kgId`),并以 eventId 过滤;`@PreAuthorize(EVENT_REVIEW_READ)`。
- `getEventReview(id)`:加载后校验 `review.kindergarten.id == kgId`,否则 `EntityNotFoundException`(隐藏 404);`@PreAuthorize(EVENT_REVIEW_READ)`。

### D4：confirm 本期不触发通知
confirm 仅写 review + 更新状态。步骤③ 接入时,将在 confirm 成功且 `resultStatus ∈ {RESOLVED, ESCALATED}` 时触发家长通知(那是 ③ 的范围 + 待定的「规则 vs 关系」决策)。本 change 留好这个挂点(代码注释标注),不实现。

## Risks / Trade-offs

- [staff 如何得知要复核哪个事件] 经步骤① 站内 Notification 告警;detection-event 浏览 API 本期不做(Non-goal),前端可先用 notification + 后续 detection 读 API。不阻塞 confirm 本身。
- [并发复核同一事件] event_reviews append-only(允许多行);detection_events.status 末次 confirm 胜出。可接受(审计行全留)。
- [confirm 更新状态用整实体 save] dedup_key 未映射不受影响;updatedAt 不自动 bump(无 @UpdateTimestamp)——可接受,或后续加。
- [result_status 合法性] 仅排除 OPEN;未强制状态机合法转移(如 RESOLVED→OPEN)。本期不做状态机校验(YAGNI);记 follow-up。

## Migration Plan

1. AuthorizationAction + Policy(2 action)。
2. [TDD] confirm 测试(写 review 行 + 状态更新、非法 result_status 400、跨园 404、错误角色 403、匿名 401)+ 读测试(列/单条租户 scoped)。
3. EventReviewCreateDTO + EventReviewService(confirm + 租户 scoped read,替换 denyAll)+ EventReviewController(POST + GET list/get)。
4. 容器内全套件全绿;ai-detection spec delta;code review;合 develop / push / archive / 清理 worktree。
- 回滚:纯新增端点 + 2 enum 值;git 还原;无 schema。

## Open Questions

- result_status 是否需状态机合法转移校验?本期仅排除 OPEN,记 follow-up。
- confirm 端点路径用 `/api/v1/event_reviews`(body 带 eventId)vs `/api/v1/detection_events/{id}/reviews`?倾向前者(EventReviewController 既有 mapping),apply 第 3 步定。
