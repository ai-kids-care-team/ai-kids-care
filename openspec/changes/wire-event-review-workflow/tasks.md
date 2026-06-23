## 1. 授权 action

- [x] 1.1 `AuthorizationAction` 加 `EVENT_REVIEW_WRITE` / `EVENT_REVIEW_READ`
- [x] 1.2 `AuthorizationPolicy`：两 action → `tenantIdentity && (KINDERGARTEN_ADMIN || TEACHER)`(镜像 TENANT_S2 模式)

## 2. DTO + Service（TDD）

- [x] 2.1 [RED] `EventReviewApiTest extends BaseIntegrationTest`(MockMvc + 真登录会话):confirm 写 review 行 + detection_events.status 更新;非法 result_status(OPEN/未知)→400;跨园事件→隐藏 404;GUARDIAN/非 staff→403;匿名→401;读列某事件复核(租户 scoped)
- [x] 2.2 `EventReviewCreateDTO`(eventId @NotNull、resultStatus @NotNull EventStatusEnum、comment 可选)
- [x] 2.3 `EventReviewService`:`confirm(eventId, resultStatus, comment)` —— 租户加载事件(findByIdAndKindergarten_Id,隐藏 404)+ 校验 result_status + 写 EventReview + event.setStatus+save;`@PreAuthorize(EVENT_REVIEW_WRITE)`;`@Transactional`。read:list(追加 kindergarten 谓词)+ get(校验同园,隐藏 404),`@PreAuthorize(EVENT_REVIEW_READ)`,替换 denyAll(getLatest 保 denyAll 留 ③ 用)。confirm 内注释标注 ③ 的家长通知挂点(RESOLVED/ESCALATED),本期不实现
- [x] 2.4 容器内 service/DTO 编译 + 测试通过

## 3. Controller

- [x] 3.1 `EventReviewController`:`POST /api/v1/event_reviews`(@Valid body → confirm → 201)+ `GET /api/v1/event_reviews?eventId=`(list)+ `GET /api/v1/event_reviews/{id}`(get)
- [x] 3.2 容器内端点测试绿

## 4. 验证与收尾（verification-before-completion）

- [x] 4.1 容器内 `gradle:8.7-jdk21` 实跑**全套件**全绿(既有 163 + 新增)，留存证据
- [x] 4.2 范围核对(git diff)：仅 event-review controller/service + DTO + 2 auth action;未碰步骤③/家长/detection-event 读 API/SMS/前端;无 schema 迁移
- [x] 4.3 code review(sonnet)完成,Ready to merge;采纳 I-1(mapper kindergartenId 单跳避 N+3)/M-1(EventReview.resultStatus @NotNull)/I-2(跨园用外园 admin 实测+补跨园 GET 404)/M-2(teacher 正向)/M-3(匿名 POST),重跑 171 全绿
- [ ] 4.4 合并 develop / push / `/opsx:archive` / 清理 worktree(用户驱动)

---

> 无 schema 迁移、无高风险操作。confirm 本期**不触发任何通知**(步骤③ 家长通知另起 explore + change)。
