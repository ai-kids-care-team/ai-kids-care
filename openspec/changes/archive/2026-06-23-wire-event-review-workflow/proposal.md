## Why

闭环步骤②。步骤①已让 staff 在 detection 事件摄入时收到「待复核」即时告警,但**目前无端点可处理** —— `EventReviewController` 是空壳、`EventReviewService` 全 `denyAll()`、`detection_events.status` 无任何服务可更新。staff 收到告警却无法在系统里确认/处置事件,闭环断在这里。

本 change 发布**事件复核工作流**:staff 对一个 detection 事件提交复核(写 `event_reviews` 审计行 + 更新 `detection_events.status`),并发布复核记录读 API。这关闭 staff 处置回路,并为步骤③(复核确认→规则引擎→家长 PUSH)备好触发点(③ 因「规则 vs 关系」等产品歧义另起 explore)。

## What Changes

- **复核确认端点** `POST /api/v1/event_reviews`(body: `eventId`, `resultStatus`, 可选 `comment`):
  - 写一条 `event_reviews`(event、kindergarten、reviewer=会话用户、`from_status`=事件当前状态、`result_status`、comment),并把 `detection_events.status` 更新为 `result_status`。
  - `result_status` 仅接受 `ACKNOWLEDGED/IN_REVIEW/RESOLVED/DISMISSED/ESCALATED`(不接受 `OPEN`,那是初始态)→ 非法值 400。
  - 租户 scoped:事件须在调用者 active kindergarten 内,否则隐藏 404(不泄露跨园事件存在性)。
- **复核记录读 API**:`GET /api/v1/event_reviews?eventId={id}`(列某事件复核历史,租户 scoped)+ `GET /api/v1/event_reviews/{id}`(单条,租户 scoped,跨园隐藏 404)。
- **授权**:新增 `AuthorizationAction.EVENT_REVIEW_WRITE` / `EVENT_REVIEW_READ`,均为「本园 ADMIN/TEACHER + 有效 tenant identity」(粗门);细粒度租户隔离由 service 用 `EffectiveAuthorizationContext` + repository 强制。替换 EventReviewService 的 denyAll。
- **DTO**:`EventReviewCreateDTO`(eventId/resultStatus 必填、comment 可选);复用既有 `EventReviewVO`(读响应)。

Non-goals(后续步骤/另议):
- **不**做步骤③ 家长通知(规则引擎→家长 PUSH)—— 有「规则 opt-in vs 关系图自动」等产品歧义,留 explore 定;本 change 的 confirm **不触发任何通知**(③ 接入时再 hook)。
- **不**做 detection-event 读 API(`DetectionEventService` 仍 denyAll;staff 经步骤①站内告警得知事件;detection 浏览 API 另议)。
- **不**改步骤① 摄入/告警;**不**碰 SMS/前端看板;**不**改 schema(event_reviews/detection_events 表已存在)。

## Capabilities

### New Capabilities
（无）

### Modified Capabilities
- `ai-detection`: ADDED「Event review confirmation workflow」—— staff 对 detection 事件提交复核(写 event_reviews + 更新 detection_event.status)+ 复核记录读 API;租户 scoped;ADMIN/TEACHER 授权。(此前 ai-detection 闭环 spec 仅提到 event_reviews 作为家长通知闸门的前置,本 change 把复核**工作流本身**规范化。)

## Impact

- **产品代码**:`EventReviewController`(POST confirm + GET list/get)、`EventReviewService`(confirm 方法 + 自/租户作用域 read,替换 denyAll)、`EventReviewCreateDTO`、`AuthorizationAction`+`AuthorizationPolicy`(2 个 action)、`DetectionEventRepository` 复用 `findByIdAndKindergarten_Id`;detection_event 状态更新经加载实体 setStatus+save(dedup_key 未映射不受影响)。
- **测试**(Testcontainers):confirm(写 review 行 + 状态更新、非法 result_status 400、跨园事件隐藏 404、非 ADMIN/TEACHER 角色 403、匿名 401)、读(列某事件复核、跨园隐藏 404)。
- **CI**:复用 `backend-java-tests.yml`。**无 schema 迁移、无高风险操作。**
- **spec**:ai-detection delta(随 change)。
- **解锁**:步骤③ 有了确认触发点(confirm 写 event_reviews + 置 RESOLVED/ESCALATED 时,③ 将据此发家长通知)。
